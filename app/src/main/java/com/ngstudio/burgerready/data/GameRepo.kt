package com.ngstudio.burgerready.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.game.Cosmetics
import com.ngstudio.burgerready.game.LevelGenerator
import com.ngstudio.burgerready.game.Rng
import com.ngstudio.burgerready.game.ShiftResult
import com.ngstudio.burgerready.game.StatsCalc
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.Upgrade
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

data class LevelReward(
    val bucks: Long,
    val gems: Int,
    val newCards: List<Cards.Card>,
    val rankUp: Int?,
    val firstWin: Boolean,
    val starsBefore: Int,
)

/** Single source of truth for progress. Compose observes [state]. All mutations go through [update]. */
class GameRepo(private val context: Context) {
    val state = mutableStateOf(SaveData())
    val data: SaveData get() = state.value
    private val io = Executors.newSingleThreadExecutor()
    private val file = File(context.filesDir, "save.json")
    private val backup = File(context.filesDir, "save.bak.json")

    fun load() {
        val loaded = read(file) ?: read(backup)
        if (loaded != null) state.value = loaded
        ensureDaily()
    }

    @Synchronized
    fun update(f: (SaveData) -> SaveData) {
        state.value = f(state.value)
        val snapshot = state.value
        io.execute { write(snapshot) }
    }

    // ------------------------------------------------------------------------------------- levels
    fun isUnlocked(level: Int): Boolean = level <= data.highestUnlocked || level in data.adUnlocked
    fun currentLevel(): Int = min(data.highestUnlocked, LevelGenerator.MAX_LEVEL)

    /** Debug builds only: unlock a level after the (mock) rewarded ad. */
    fun adUnlock(level: Int) {
        if (!BuildConfig.DEBUG_AD_UNLOCK) return
        update { it.copy(adUnlocked = it.adUnlocked + level) }
    }

    fun stats() = StatsCalc.stats(data.upgradeLevels(), data.selectedStore, data.selectedPet)

    fun completeLevel(r: ShiftResult, cardsEarned: Set<String>, counters: Map<String, Long>): LevelReward {
        val before = data.starsOf(r.levelId)
        val firstWin = r.won && before == 0
        var bucks = (LevelGenerator.reward(r.levelId) * LevelGenerator.starMultiplier(if (r.won) r.stars else 0)).toLong()
        if (!r.won) bucks = max(10L, bucks)
        var gems = 0
        if (r.won && r.stars == 3 && before < 3) gems += 2
        if (r.won && LevelGenerator.bossFor(r.levelId) != null && firstWin) gems += 5
        val newCards = Cards.ALL.filter { it.id in cardsEarned && it.id !in data.cards }
        val xpBefore = data.chefXp
        val rankBefore = ChefRank.rankOf(xpBefore)
        val xpAfter = xpBefore + r.served
        val rankAfter = ChefRank.rankOf(xpAfter)
        update { d ->
            val sb = StringBuilder(d.stars)
            while (sb.length < r.levelId) sb.append('0')
            if (r.won && r.stars > before) sb.setCharAt(r.levelId - 1, ('0' + r.stars))
            val c = d.counters.toMutableMap()
            fun add(k: String, v: Long) { c[k] = (c[k] ?: 0L) + v }
            add("served", r.served.toLong()); add("revenue", r.revenue.toLong()); add("played", 1)
            if (r.won) add("wins", 1)
            if (r.won && r.stars == 3 && before < 3) add("threestar", 1)
            if (r.bestCombo >= 2.99f) add("combo3", 1)
            for ((k, v) in counters) add(k, v)
            c["stars"] = sb.toString().sumOf { ch -> (ch - '0').coerceAtLeast(0) }.toLong()
            d.copy(
                stars = sb.toString(),
                highestUnlocked = if (r.won) max(d.highestUnlocked, min(LevelGenerator.MAX_LEVEL, r.levelId + 1)) else d.highestUnlocked,
                bucks = d.bucks + bucks,
                gems = d.gems + gems,
                cards = d.cards + newCards.map { it.id },
                counters = c,
                chefXp = xpAfter,
                missions = progressMissions(d.missions, r, counters),
            )
        }
        return LevelReward(bucks, gems, newCards, if (rankAfter > rankBefore) rankAfter else null, firstWin, before)
    }

    private fun progressMissions(list: List<Mission>, r: ShiftResult, counters: Map<String, Long>): List<Mission> = list.map { m ->
        val inc = when (m.kind) {
            "serve" -> r.served
            "revenue" -> r.revenue
            "win" -> if (r.won) 1 else 0
            "stars3" -> if (r.won && r.stars == 3) 1 else 0
            "combo" -> if (r.bestCombo >= m.goal.toFloat() / 10f) m.goal else 0
            "item" -> com.ngstudio.burgerready.game.Item.byId(m.item)?.let { r.itemsServed[it.ordinal] } ?: 0
            "troubles" -> (counters["troubles"] ?: 0L).toInt()
            "trays" -> (counters["trays"] ?: 0L).toInt()
            "vips" -> (counters["vips"] ?: 0L).toInt()
            else -> 0
        }
        if (m.kind == "combo") m.copy(progress = if (inc > 0) m.goal else m.progress) else m.copy(progress = min(m.goal, m.progress + inc))
    }

    fun doubleReward(bucks: Long) = update { it.copy(bucks = it.bucks + bucks) }

    // ------------------------------------------------------------------------------------- upgrades
    fun buyUpgrade(u: Upgrade): Boolean {
        val lvl = data.upgrade(u)
        if (lvl >= u.maxLevel) return false
        val cost = u.cost(lvl)
        if (data.bucks < cost) return false
        update { it.copy(bucks = it.bucks - cost, upgrades = it.upgrades + (u.id to lvl + 1), counters = it.counters + ("upgrades" to it.counter("upgrades") + 1)) }
        return true
    }

    // ------------------------------------------------------------------------------------- stores
    fun incomeRate(storeId: String): Double {
        val s = Stores.byId(storeId)
        val ren = data.stores[storeId] ?: return 0.0
        return Stores.incomePerHour(s) * (1.0 + 0.10 * ren) * (1.0 + 0.04 * data.upgrade(Upgrade.INCOME))
    }

    fun pendingIncome(storeId: String, now: Long = System.currentTimeMillis()): Long {
        if (storeId !in data.stores) return 0
        val last = data.storeCollect[storeId] ?: now
        val hours = ((now - last).coerceAtLeast(0L) / 3_600_000.0).coerceAtMost(Stores.INCOME_CAP_HOURS)
        return (incomeRate(storeId) * hours).toLong()
    }

    fun totalPendingIncome(now: Long = System.currentTimeMillis()): Long = data.stores.keys.sumOf { pendingIncome(it, now) }

    fun collectIncome(storeId: String? = null): Long {
        val now = System.currentTimeMillis()
        val ids = storeId?.let { listOf(it) } ?: data.stores.keys.toList()
        val amount = ids.sumOf { pendingIncome(it, now) }
        update { d -> d.copy(bucks = d.bucks + amount, storeCollect = d.storeCollect + ids.associateWith { now }) }
        return amount
    }

    fun openStore(id: String): Boolean {
        val s = Stores.byId(id)
        if (id in data.stores || data.bucks < s.cost) return false
        val now = System.currentTimeMillis()
        update { it.copy(bucks = it.bucks - s.cost, stores = it.stores + (id to 0), storeCollect = it.storeCollect + (id to now), selectedStore = id,
            cards = it.cards + "store_$id", counters = it.counters + ("storesOpened" to (it.stores.size + 1).toLong())) }
        return true
    }

    fun renovate(id: String): Boolean {
        val s = Stores.byId(id)
        val stars = data.stores[id] ?: return false
        if (stars >= Stores.MAX_RENOVATION) return false
        val cost = Stores.renovationCost(s, stars)
        if (data.bucks < cost) return false
        collectIncome(id)
        update { it.copy(bucks = it.bucks - cost, stores = it.stores + (id to stars + 1)) }
        return true
    }

    fun selectStore(id: String) { if (id in data.stores) update { it.copy(selectedStore = id) } }

    // ------------------------------------------------------------------------------------- cosmetics
    private fun pay(bucks: Long, gems: Int): Boolean {
        if (data.bucks < bucks || data.gems < gems) return false
        update { it.copy(bucks = it.bucks - bucks, gems = it.gems - gems) }
        return true
    }
    fun buySkin(id: String): Boolean { val s = Cosmetics.SKINS.first { it.id == id }; if (id in data.skins || !pay(s.costBucks, s.costGems)) return false; update { it.copy(skins = it.skins + id, selectedSkin = id) }; return true }
    fun buyPet(id: String): Boolean { val p = Cosmetics.PETS.first { it.id == id }; if (id in data.pets || !pay(p.costBucks, p.costGems)) return false; update { it.copy(pets = it.pets + id, selectedPet = id) }; return true }
    fun buyTheme(id: String): Boolean { val t = Cosmetics.THEMES.first { it.id == id }; if (id in data.themes || !pay(t.costBucks, t.costGems)) return false; update { it.copy(themes = it.themes + id, selectedTheme = id) }; return true }
    fun selectSkin(id: String) { if (id in data.skins) update { it.copy(selectedSkin = id) } }
    fun selectPet(id: String) { if (id in data.pets) update { it.copy(selectedPet = id) } }
    fun selectTheme(id: String) { if (id in data.themes) update { it.copy(selectedTheme = id) } }

    // ------------------------------------------------------------------------------------- daily
    fun today(): Long = (System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())) / 86_400_000L

    fun ensureDaily() {
        val day = today()
        if (data.missionDay != day) {
            update { it.copy(missionDay = day, missions = makeMissions(day), missionChestClaimed = false) }
        }
        if (data.lastLoginDay != day) {
            val streak = if (data.lastLoginDay == day - 1) (data.loginStreak) else 0
            update { it.copy(lastLoginDay = day, loginStreak = if (data.loginClaimedDay == day - 1 || data.loginClaimedDay == -1L) streak else 0) }
        }
    }

    private fun makeMissions(day: Long): List<Mission> {
        val r = Rng(day * 977 + 13)
        val lvl = data.highestUnlocked
        val scale = 1.0 + lvl / 40.0
        val pool = mutableListOf(
            { Mission("m1", "serve", "", (20 * scale).toInt() / 5 * 5, 0, (300 * scale).toLong(), 0, false) },
            { Mission("m2", "revenue", "", (400 * scale * scale).toInt() / 50 * 50, 0, (350 * scale).toLong(), 0, false) },
            { Mission("m3", "win", "", 3, 0, (250 * scale).toLong(), 2, false) },
            { Mission("m4", "stars3", "", 2, 0, (300 * scale).toLong(), 3, false) },
            { Mission("m5", "combo", "", 20, 0, (200 * scale).toLong(), 2, false) },
            { Mission("m6", "item", "fries", (12 * scale).toInt(), 0, (250 * scale).toLong(), 0, false) },
            { Mission("m7", "trays", "", (10 * scale).toInt(), 0, (220 * scale).toLong(), 0, false) },
        )
        if (lvl >= 15) pool.add { Mission("m8", "troubles", "", 5, 0, (300 * scale).toLong(), 2, false) }
        if (lvl >= 8) pool.add { Mission("m9", "vips", "", 3, 0, (280 * scale).toLong(), 2, false) }
        val out = ArrayList<Mission>()
        while (out.size < 3 && pool.isNotEmpty()) { val i = r.int(pool.size); out.add(pool.removeAt(i)()) }
        return out
    }

    fun claimMission(id: String) {
        val m = data.missions.firstOrNull { it.id == id && it.done && !it.claimed } ?: return
        update { d -> d.copy(bucks = d.bucks + m.rewardBucks, gems = d.gems + m.rewardGems, missions = d.missions.map { if (it.id == id) it.copy(claimed = true) else it }) }
    }

    fun claimMissionChest(): Pair<Long, Int>? {
        if (data.missionChestClaimed || data.missions.isEmpty() || !data.missions.all { it.claimed }) return null
        val reward = chestReward()
        update { it.copy(missionChestClaimed = true, bucks = it.bucks + reward.first, gems = it.gems + reward.second) }
        return reward
    }

    fun canClaimLogin(): Boolean = data.loginClaimedDay != today()

    fun claimLogin(): LoginRewards.Reward? {
        if (!canClaimLogin()) return null
        val idx = data.loginStreak % 7
        val rw = LoginRewards.DAYS[idx]
        val chest = if (rw.chest) chestReward() else 0L to 0
        update { it.copy(loginClaimedDay = today(), loginStreak = (idx + 1) % 7, bucks = it.bucks + rw.bucks + chest.first, gems = it.gems + rw.gems + chest.second) }
        return rw
    }

    fun canSpin(): Boolean = data.lastSpinDay != today() || data.extraSpins > 0

    /** Returns the winning segment index (the UI animates to it). */
    fun spin(): Int? {
        if (!canSpin()) return null
        val r = Rng(System.nanoTime())
        var pick = r.float() * SpinWheel.WEIGHTS.sum(); var idx = 0
        for (i in SpinWheel.WEIGHTS.indices) { pick -= SpinWheel.WEIGHTS[i]; if (pick <= 0f) { idx = i; break } }
        val seg = SpinWheel.SEGMENTS[idx]
        val scale = 1.0 + data.highestUnlocked / 60.0
        val chest = if (seg.label == "CHEST") chestReward() else 0L to 0
        update { d ->
            val useFree = d.lastSpinDay != today()
            d.copy(lastSpinDay = if (useFree) today() else d.lastSpinDay, extraSpins = if (useFree) d.extraSpins else d.extraSpins - 1,
                bucks = d.bucks + (seg.bucks * scale).toLong() + chest.first, gems = d.gems + seg.gems + chest.second)
        }
        return idx
    }

    fun grantExtraSpin() { if (BuildConfig.DEBUG_AD_UNLOCK) update { it.copy(extraSpins = it.extraSpins + 1) } }

    fun canClaimFreeChest(): Boolean = data.freeChestDay != today()
    fun claimFreeChest(): Pair<Long, Int>? {
        if (!canClaimFreeChest()) return null
        val rw = chestReward()
        update { it.copy(freeChestDay = today(), bucks = it.bucks + rw.first, gems = it.gems + rw.second) }
        return rw
    }

    fun chestReward(): Pair<Long, Int> {
        val r = Rng(System.nanoTime())
        val base = LevelGenerator.reward(max(1, data.highestUnlocked))
        return (base * r.range(2f, 4f)).toLong() / 10 * 10 to (if (r.chance(0.4f)) r.int(6) + 3 else 0)
    }

    // ------------------------------------------------------------------------------------- collection & achievements
    fun claimSet(set: String) {
        if (set in data.setsClaimed) return
        val cards = Cards.ALL.filter { it.set == set }
        if (!cards.all { it.id in data.cards }) return
        update { it.copy(setsClaimed = it.setsClaimed + set, gems = it.gems + Cards.setReward(set)) }
    }

    fun achievementValue(key: String): Long = when (key) {
        "storesOpened" -> data.stores.size.toLong()
        "upgrades" -> data.upgrades.values.sum().toLong()
        "stars" -> data.totalStars().toLong()
        else -> data.counter(key)
    }

    fun claimAchievement(track: Achievements.Track, tier: Int) {
        val id = Achievements.id(track.key, tier)
        if (id in data.achievementsClaimed || achievementValue(track.key) < track.tiers[tier]) return
        update { it.copy(achievementsClaimed = it.achievementsClaimed + id, gems = it.gems + Achievements.gems(tier)) }
    }

    fun unclaimedAchievements(): Int = Achievements.TRACKS.sumOf { t -> t.tiers.indices.count { i -> achievementValue(t.key) >= t.tiers[i] && Achievements.id(t.key, i) !in data.achievementsClaimed } }

    fun claimRankRewards(): Int {
        val rank = ChefRank.rankOf(data.chefXp)
        if (rank <= data.chefRankClaimed) return 0
        val gems = (rank - data.chefRankClaimed) * 5
        update { it.copy(chefRankClaimed = rank, gems = it.gems + gems) }
        return gems
    }

    // ------------------------------------------------------------------------------------- settings
    fun setOnboardingDone() = update { it.copy(onboardingDone = true) }
    fun resetProgress() {
        val keep = data
        update { SaveData(onboardingDone = true, music = keep.music, sfx = keep.sfx, haptics = keep.haptics, musicVolume = keep.musicVolume, sfxVolume = keep.sfxVolume, quality = keep.quality) }
        ensureDaily()
    }

    // ------------------------------------------------------------------------------------- persistence
    private fun write(d: SaveData) {
        try {
            val json = toJson(d).toString()
            val tmp = File(context.filesDir, "save.tmp")
            tmp.writeText(json)
            if (file.exists()) file.copyTo(backup, overwrite = true)
            if (!tmp.renameTo(file)) { file.writeText(json) }
        } catch (e: Exception) { Log.e("GameRepo", "save failed", e) }
    }

    private fun read(f: File): SaveData? = try { if (f.exists()) fromJson(JSONObject(f.readText())) else null } catch (e: Exception) { Log.w("GameRepo", "corrupt ${f.name}"); null }

    companion object {
        fun toJson(d: SaveData): JSONObject = JSONObject().apply {
            put("version", d.version); put("onboardingDone", d.onboardingDone); put("highestUnlocked", d.highestUnlocked)
            put("stars", d.stars); put("adUnlocked", JSONArray(d.adUnlocked.toList())); put("bucks", d.bucks); put("gems", d.gems)
            put("upgrades", JSONObject(d.upgrades as Map<*, *>)); put("stores", JSONObject(d.stores as Map<*, *>)); put("storeCollect", JSONObject(d.storeCollect as Map<*, *>))
            put("selectedStore", d.selectedStore); put("skins", JSONArray(d.skins.toList())); put("selectedSkin", d.selectedSkin)
            put("pets", JSONArray(d.pets.toList())); put("selectedPet", d.selectedPet); put("themes", JSONArray(d.themes.toList())); put("selectedTheme", d.selectedTheme)
            put("lastLoginDay", d.lastLoginDay); put("loginStreak", d.loginStreak); put("loginClaimedDay", d.loginClaimedDay)
            put("lastSpinDay", d.lastSpinDay); put("extraSpins", d.extraSpins); put("missionDay", d.missionDay); put("missionChestClaimed", d.missionChestClaimed)
            put("freeChestDay", d.freeChestDay)
            put("missions", JSONArray(d.missions.map { m -> JSONObject().apply { put("id", m.id); put("kind", m.kind); put("item", m.item); put("goal", m.goal); put("progress", m.progress); put("rb", m.rewardBucks); put("rg", m.rewardGems); put("claimed", m.claimed) } }))
            put("cards", JSONArray(d.cards.toList())); put("setsClaimed", JSONArray(d.setsClaimed.toList())); put("counters", JSONObject(d.counters as Map<*, *>))
            put("achievementsClaimed", JSONArray(d.achievementsClaimed.toList())); put("chefXp", d.chefXp); put("chefRankClaimed", d.chefRankClaimed)
            put("music", d.music); put("sfx", d.sfx); put("haptics", d.haptics); put("musicVolume", d.musicVolume.toDouble()); put("sfxVolume", d.sfxVolume.toDouble())
            put("quality", d.quality); put("highFps", d.highFps)
        }

        private fun strSet(a: JSONArray?): Set<String> { val s = HashSet<String>(); if (a != null) for (i in 0 until a.length()) s.add(a.getString(i)); return s }
        private fun intSet(a: JSONArray?): Set<Int> { val s = HashSet<Int>(); if (a != null) for (i in 0 until a.length()) s.add(a.getInt(i)); return s }
        private fun intMap(o: JSONObject?): Map<String, Int> { val m = HashMap<String, Int>(); if (o != null) for (k in o.keys()) m[k] = o.getInt(k); return m }
        private fun longMap(o: JSONObject?): Map<String, Long> { val m = HashMap<String, Long>(); if (o != null) for (k in o.keys()) m[k] = o.getLong(k); return m }

        fun fromJson(o: JSONObject): SaveData {
            val def = SaveData()
            val ms = o.optJSONArray("missions")
            val missions = ArrayList<Mission>()
            if (ms != null) for (i in 0 until ms.length()) { val m = ms.getJSONObject(i); missions.add(Mission(m.getString("id"), m.getString("kind"), m.optString("item"), m.getInt("goal"), m.getInt("progress"), m.getLong("rb"), m.getInt("rg"), m.getBoolean("claimed"))) }
            return SaveData(
                version = o.optInt("version", 1), onboardingDone = o.optBoolean("onboardingDone"), highestUnlocked = o.optInt("highestUnlocked", 1).coerceIn(1, LevelGenerator.MAX_LEVEL),
                stars = o.optString("stars", ""), adUnlocked = intSet(o.optJSONArray("adUnlocked")), bucks = o.optLong("bucks", def.bucks), gems = o.optInt("gems", def.gems),
                upgrades = intMap(o.optJSONObject("upgrades")), stores = intMap(o.optJSONObject("stores")).ifEmpty { def.stores }, storeCollect = longMap(o.optJSONObject("storeCollect")),
                selectedStore = o.optString("selectedStore", def.selectedStore), skins = strSet(o.optJSONArray("skins")).ifEmpty { def.skins }, selectedSkin = o.optString("selectedSkin", def.selectedSkin),
                pets = strSet(o.optJSONArray("pets")).ifEmpty { def.pets }, selectedPet = o.optString("selectedPet", def.selectedPet),
                themes = strSet(o.optJSONArray("themes")).ifEmpty { def.themes }, selectedTheme = o.optString("selectedTheme", def.selectedTheme),
                lastLoginDay = o.optLong("lastLoginDay", -1), loginStreak = o.optInt("loginStreak"), loginClaimedDay = o.optLong("loginClaimedDay", -1),
                lastSpinDay = o.optLong("lastSpinDay", -1), extraSpins = o.optInt("extraSpins"), missionDay = o.optLong("missionDay", -1), missions = missions,
                missionChestClaimed = o.optBoolean("missionChestClaimed"), freeChestDay = o.optLong("freeChestDay", -1),
                cards = strSet(o.optJSONArray("cards")), setsClaimed = strSet(o.optJSONArray("setsClaimed")), counters = longMap(o.optJSONObject("counters")),
                achievementsClaimed = strSet(o.optJSONArray("achievementsClaimed")), chefXp = o.optLong("chefXp"), chefRankClaimed = o.optInt("chefRankClaimed", 1),
                music = o.optBoolean("music", true), sfx = o.optBoolean("sfx", true), haptics = o.optBoolean("haptics", true),
                musicVolume = o.optDouble("musicVolume", 0.7).toFloat(), sfxVolume = o.optDouble("sfxVolume", 1.0).toFloat(), quality = o.optString("quality", "auto"), highFps = o.optBoolean("highFps"),
            )
        }
    }
}
