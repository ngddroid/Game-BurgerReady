package com.ngstudio.burgerready.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.game.Cosmetics
import com.ngstudio.burgerready.game.GoldStars
import com.ngstudio.burgerready.game.Item
import com.ngstudio.burgerready.game.RestaurantProgress
import com.ngstudio.burgerready.game.Restaurants
import com.ngstudio.burgerready.game.Rng
import com.ngstudio.burgerready.game.StatsCalc
import com.ngstudio.burgerready.game.Stores
import com.ngstudio.burgerready.game.Tour
import com.ngstudio.burgerready.game.Upgrade
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * What happened in a restaurant since the last save. The World keeps cumulative counters; the game screen
 * diffs two snapshots and commits the difference every few seconds.
 */
data class SessionDelta(
    val served: Int = 0,
    val revenue: Long = 0,
    val items: IntArray = IntArray(Item.entries.size),
    val vips: Int = 0,
    val troubles: Int = 0,
    val trays: Int = 0,
    val rushes: Int = 0,
    val built: Int = 0,
    val critics: Int = 0,
    val bestCombo: Float = 1f,
    val cards: Set<String> = emptySet(),
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
        if (loaded != null) state.value = if (loaded.selectedStore in loaded.stores) loaded else loaded.copy(selectedStore = "wagon", stores = loaded.stores + ("wagon" to (loaded.stores["wagon"] ?: 0)))
        startIncomeClocks()
        ensureDaily()
        // saves that finished the classic Voyage (finale and Golden Tour hook seen) before the World Tour existed
        if (data.isComplete("candy") && "golden_tour" in data.story && !worldTourOpen()) openWorldTour(select = false)
    }

    // ------------------------------------------------------------------------------------- world tour
    val firstTourId: String get() = Tour.id(0, 0)
    fun worldTourOpen(): Boolean = firstTourId in data.stores

    /** Opens the World Tour's first restaurant (after the classic finale). Returns its id. */
    fun openWorldTour(select: Boolean = true): String {
        val id = firstTourId
        if (id in data.stores) { if (select) selectStore(id); return id }
        val now = System.currentTimeMillis()
        update {
            it.copy(
                stores = it.stores + (id to 0),
                storeCollect = it.storeCollect + (id to now),
                selectedStore = if (select) id else it.selectedStore,
                counters = it.counters + ("storesOpened" to (it.stores.size + 1).toLong()),
            )
        }
        return id
    }

    /** Regions whose flagship is finished (NEXT REGION bought, or the last one fully built). */
    fun regionsComplete(): Int = Tour.REGIONS.indices.count { r -> data.isComplete(Tour.id(r, Tour.PER_REGION - 1)) }

    /**
     * Every open restaurant needs an idle-income start time. The Bun Wagon is open from the first launch but only
     * got one at the first collect, so it earned nothing until then (pendingIncome treats a missing time as "now").
     */
    private fun startIncomeClocks() {
        val missing = data.stores.keys.filter { it !in data.storeCollect }
        if (missing.isEmpty()) return
        val now = System.currentTimeMillis()
        update { d -> d.copy(storeCollect = d.storeCollect + missing.associateWith { now }) }
    }

    @Synchronized
    fun update(f: (SaveData) -> SaveData) {
        state.value = f(state.value)
        val snapshot = state.value
        io.execute { write(snapshot) }
    }

    // ------------------------------------------------------------------------------------- restaurants
    fun stats() = StatsCalc.stats(data.upgradeLevels(), data.selectedStore, data.selectedPet)

    fun isOpen(storeId: String) = storeId in data.stores

    /**
     * Saves the running restaurant: its build progress, the wallet and what happened since the last commit.
     * [playedMs] is the time spent playing in it since the last commit: its idle-income clock is moved forward by
     * that much, so a restaurant earns "while you're away" only while you are actually away (playing in it used to
     * accrue idle income on top of the cash earned at the register).
     * Returns the Recipe cards that are new to the player.
     */
    fun commitRestaurant(storeId: String, progress: RestaurantProgress, cash: Long, delta: SessionDelta, playedMs: Long = 0L): List<Cards.Card> {
        val newCards = Cards.ALL.filter { it.id in delta.cards && it.id !in data.cards }
        val now = System.currentTimeMillis()
        update { d ->
            val c = d.counters.toMutableMap()
            fun add(k: String, v: Long) { if (v != 0L) c[k] = (c[k] ?: 0L) + v }
            add("served", delta.served.toLong()); add("revenue", delta.revenue); add("built", delta.built.toLong())
            add("rushes", delta.rushes.toLong()); add("critics", delta.critics.toLong()); add("troubles", delta.troubles.toLong())
            add("trays", delta.trays.toLong()); add("vips", delta.vips.toLong())
            if (delta.bestCombo >= 2.99f) add("combo3", 1)
            val last = d.storeCollect[storeId]
            d.copy(
                bucks = max(0L, cash),
                restaurants = d.restaurants + (storeId to progress),
                storeCollect = if (last != null && playedMs > 0L) d.storeCollect + (storeId to min(now, last + playedMs)) else d.storeCollect,
                counters = c,
                cards = d.cards + newCards.map { it.id },
                chefXp = d.chefXp + delta.served,
                missions = progressMissions(d.missions, delta),
            )
        }
        return newCards
    }

    /** Opens the restaurant after [fromStore] (the EXPAND pad was bought). Returns its id, or null at the end of the chain. */
    fun openNextRestaurant(fromStore: String): String? {
        val next = Restaurants.nextStoreId(fromStore) ?: return null
        val now = System.currentTimeMillis()
        val fromIndex = Restaurants.indexOf(fromStore)
        // finishing a World Tour flagship stamps its region (a Recipe Book card and a bigger gem reward)
        val regionDone = if (Tour.isFlagship(fromIndex)) "region_" + Tour.REGIONS[Tour.regionOf(fromIndex)].id else null
        update {
            it.copy(
                stores = it.stores + (next to (it.stores[next] ?: 0)),
                storeCollect = it.storeCollect + (next to (it.storeCollect[next] ?: now)),
                selectedStore = next,
                gems = it.gems + (if (regionDone != null) 40 else 15),
                cards = it.cards + (if (Stores.isClassic(next)) setOf("store_$next") else emptySet()) + listOfNotNull(regionDone),
                counters = it.counters + ("storesOpened" to (it.stores.size + 1).toLong()),
            )
        }
        return next
    }

    /** Debug builds only: finish the current restaurant (every pad bought) and open the next one. */
    fun debugCompleteRestaurant() {
        if (!BuildConfig.DEBUG_TOOLS) return
        val id = data.selectedStore
        val cfg = Restaurants.config(id)
        update { it.copy(restaurants = it.restaurants + (id to RestaurantProgress(cfg.pads.map { p -> p.id }.toSet()))) }
        openNextRestaurant(id)
    }

    /** Debug builds only: open the next restaurant after a (mock) rewarded ad, without finishing this one. */
    fun adUnlockNext(): String? {
        if (!BuildConfig.DEBUG_AD_UNLOCK) return null
        val last = Restaurants.storeAt(data.frontier()).id
        return openNextRestaurant(last)
    }

    private fun progressMissions(list: List<Mission>, r: SessionDelta): List<Mission> = list.map { m ->
        val inc: Long = when (m.kind) {
            "serve" -> r.served.toLong()
            "revenue" -> r.revenue
            "build" -> r.built.toLong()
            "rush" -> r.rushes.toLong()
            "combo" -> if (r.bestCombo >= m.goal.toFloat() / 10f) m.goal else 0L
            "item" -> Item.byId(m.item)?.let { r.items[it.ordinal].toLong() } ?: 0L
            "troubles" -> r.troubles.toLong()
            "trays" -> r.trays.toLong()
            "vips" -> r.vips.toLong()
            else -> 0L
        }
        if (inc <= 0L) m
        else if (m.kind == "combo") m.copy(progress = m.goal)
        else m.copy(progress = min(m.goal, m.progress + inc))
    }

    fun doubleReward(bucks: Long) = update { it.copy(bucks = it.bucks + bucks) }

    // ------------------------------------------------------------------------------------- story & gold stars
    fun markStorySeen(id: String) { if (id !in data.story) update { it.copy(story = it.story + id) } }

    /** Pages stamped so far (finished restaurants). */
    fun stampedPages(): Int = Stores.ALL.count { data.isComplete(it.id) }
    /** A page was stamped since the player last opened the Voyage Book. */
    fun voyageHasNew(): Boolean = stampedPages() > data.voyageSeen
    fun markVoyageSeen() { val n = stampedPages(); if (n != data.voyageSeen) update { it.copy(voyageSeen = n) } }

    /**
     * Records the gold stars met in [storeId] during this visit ([met] = bitmask from GoldStars.met).
     * Returns how many are new. Each pays GoldStars.GEMS; the 36th also pays GoldStars.BOOK_GEMS.
     */
    fun awardGold(storeId: String, met: Int): Int {
        if (!Stores.isClassic(storeId) || !data.isComplete(storeId)) return 0
        val old = data.goldOf(storeId)
        val fresh = met and 7 and old.inv()
        if (fresh == 0) return 0
        val n = GoldStars.count(fresh)
        val before = data.goldTotal()
        update { d ->
            val total = before + n
            val book = if (before < GoldStars.TOTAL && total >= GoldStars.TOTAL) GoldStars.BOOK_GEMS else 0
            d.copy(gold = d.gold + (storeId to (old or fresh)), gems = d.gems + n * GoldStars.GEMS + book)
        }
        return n
    }

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
        val ren = data.stores[storeId] ?: return 0.0
        val cfg = Restaurants.config(storeId)
        val frac = data.progressOf(storeId).fraction(cfg)
        return Restaurants.incomePerHour(cfg.index, frac) * (1.0 + 0.10 * ren) * (1.0 + 0.04 * data.upgrade(Upgrade.INCOME)) *
            (1.0 + GoldStars.INCOME_BONUS * GoldStars.count(data.goldOf(storeId)))
    }

    fun pendingIncome(storeId: String, now: Long = System.currentTimeMillis()): Long {
        if (storeId !in data.stores) return 0
        val last = data.storeCollect[storeId] ?: now
        val hours = ((now - last).coerceAtLeast(0L) / 3_600_000.0).coerceAtMost(Stores.INCOME_CAP_HOURS)
        return (incomeRate(storeId) * hours).toLong()
    }

    fun totalPendingIncome(now: Long = System.currentTimeMillis()): Long = data.stores.keys.sumOf { pendingIncome(it, now) }

    /** Bucks per hour from every open restaurant together. */
    fun totalIncomeRate(): Double = data.stores.keys.sumOf { incomeRate(it) }

    fun collectIncome(storeId: String? = null): Long {
        val now = System.currentTimeMillis()
        val ids = storeId?.let { listOf(it) } ?: data.stores.keys.toList()
        val amount = ids.sumOf { pendingIncome(it, now) }
        update { d -> d.copy(bucks = d.bucks + amount, storeCollect = d.storeCollect + ids.associateWith { now }) }
        return amount
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
        val f = data.frontier()
        val scale = 1.0 + f * 0.35
        val unit = Restaurants.rewardUnit(f)
        val pool = mutableListOf(
            { Mission("m1", "serve", "", ((25 * scale).toInt() / 5 * 5).toLong(), 0L, unit * 3, 0, false) },
            { Mission("m2", "revenue", "", Restaurants.roundCost(600f * Restaurants.priceMul(f)), 0L, unit * 3, 0, false) },
            { Mission("m3", "build", "", 3L, 0L, unit * 2, 2, false) },
            { Mission("m5", "combo", "", 20L, 0L, unit * 2, 2, false) },
            { Mission("m6", "item", "fries", ((12 * scale).toInt()).toLong(), 0L, unit * 2, 0, false) },
            { Mission("m7", "trays", "", ((10 * scale).toInt()).toLong(), 0L, unit * 2, 0, false) },
        )
        if (f >= 1 || data.builtTotal() >= 8) pool.add { Mission("m4", "rush", "", 2L, 0L, unit * 3, 3, false) }
        if (f >= 1) pool.add { Mission("m8", "troubles", "", 5L, 0L, unit * 3, 2, false) }
        if (data.builtTotal() >= 6) pool.add { Mission("m9", "vips", "", 3L, 0L, unit * 3, 2, false) }
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

    /**
     * Bucks a daily-calendar day or a spin segment pays, scaled to the newest restaurant like tasks and chests
     * (a flat 200 Bucks meant nothing once pads cost millions). Restaurant 1 keeps the table values.
     */
    fun scaledBucks(base: Long): Long =
        if (base <= 0L) 0L else Restaurants.roundCost((base * Restaurants.priceMul(data.frontier())).toFloat()).toLong()

    fun loginBucks(day: Int): Long = scaledBucks(LoginRewards.DAYS[day.coerceIn(0, 6)].bucks)

    fun claimLogin(): LoginRewards.Reward? {
        if (!canClaimLogin()) return null
        val idx = data.loginStreak % 7
        val rw = LoginRewards.DAYS[idx]
        val bucks = loginBucks(idx)
        val chest = if (rw.chest) chestReward() else 0L to 0
        update { it.copy(loginClaimedDay = today(), loginStreak = (idx + 1) % 7, bucks = it.bucks + bucks + chest.first, gems = it.gems + rw.gems + chest.second) }
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
        val chest = if (seg.label == "CHEST") chestReward() else 0L to 0
        update { d ->
            val useFree = d.lastSpinDay != today()
            d.copy(lastSpinDay = if (useFree) today() else d.lastSpinDay, extraSpins = if (useFree) d.extraSpins else d.extraSpins - 1,
                bucks = d.bucks + scaledBucks(seg.bucks) + chest.first, gems = d.gems + seg.gems + chest.second)
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
        val base = Restaurants.rewardUnit(data.frontier())
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
        "built" -> data.builtTotal().toLong()
        "regions" -> regionsComplete().toLong()
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
        startIncomeClocks()
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
            put("version", 3); put("onboardingDone", d.onboardingDone)
            put("bucks", d.bucks); put("gems", d.gems)
            put("restaurants", JSONObject().apply {
                for ((id, p) in d.restaurants) put(id, JSONObject().apply {
                    // v3: a fully bought restaurant is stored as {"all":1} (hundreds of them with the World Tour)
                    if (p.paid.isEmpty() && isFullyBought(id, p)) put("all", 1)
                    else {
                        put("done", JSONArray(p.done.sorted()))
                        put("paid", JSONObject().apply { for ((k, v) in p.paid) put(k.toString(), v) })
                    }
                })
            })
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
            put("story", JSONArray(d.story.toList())); put("gold", JSONObject(d.gold as Map<*, *>)); put("voyageSeen", d.voyageSeen)
        }

        private fun strSet(a: JSONArray?): Set<String> { val s = HashSet<String>(); if (a != null) for (i in 0 until a.length()) s.add(a.getString(i)); return s }
        private fun intSet(a: JSONArray?): Set<Int> { val s = HashSet<Int>(); if (a != null) for (i in 0 until a.length()) s.add(a.getInt(i)); return s }
        private fun knownStore(id: String) = Stores.isClassic(id) || Tour.indexOf(id) != null
        private fun isFullyBought(id: String, p: RestaurantProgress): Boolean {
            if (!knownStore(id)) return false
            val pads = Restaurants.config(id).pads
            return p.done.size == pads.size && pads.all { it.id in p.done }
        }

        private fun progressMap(o: JSONObject?): Map<String, RestaurantProgress> {
            val m = HashMap<String, RestaurantProgress>()
            if (o != null) for (k in o.keys()) {
                val r = o.optJSONObject(k) ?: continue
                if (r.optInt("all") == 1) {
                    if (!knownStore(k)) continue
                    m[k] = RestaurantProgress(Restaurants.config(k).pads.map { it.id }.toSet())
                    continue
                }
                val done = intSet(r.optJSONArray("done"))
                val paid = HashMap<Int, Long>()
                r.optJSONObject("paid")?.let { po -> for (pk in po.keys()) pk.toIntOrNull()?.let { paid[it] = po.getLong(pk) } }
                m[k] = RestaurantProgress(done, paid)
            }
            return m
        }

        private fun intMap(o: JSONObject?): Map<String, Int> { val m = HashMap<String, Int>(); if (o != null) for (k in o.keys()) m[k] = o.getInt(k); return m }
        private fun longMap(o: JSONObject?): Map<String, Long> { val m = HashMap<String, Long>(); if (o != null) for (k in o.keys()) m[k] = o.getLong(k); return m }

        fun fromJson(o: JSONObject): SaveData {
            val def = SaveData()
            val ms = o.optJSONArray("missions")
            val missions = ArrayList<Mission>()
            if (ms != null) for (i in 0 until ms.length()) { val m = ms.getJSONObject(i); missions.add(Mission(m.getString("id"), m.getString("kind"), m.optString("item"), m.getLong("goal"), m.getLong("progress"), m.getLong("rb"), m.getInt("rg"), m.getBoolean("claimed"))) }
            return SaveData(
                // v1 saves (level-based) keep their wallet, cosmetics and opened stores; level progress is dropped.
                version = 3, onboardingDone = o.optBoolean("onboardingDone"),
                bucks = o.optLong("bucks", def.bucks), gems = o.optInt("gems", def.gems),
                restaurants = progressMap(o.optJSONObject("restaurants")),
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
                story = strSet(o.optJSONArray("story")), gold = intMap(o.optJSONObject("gold")), voyageSeen = o.optInt("voyageSeen", 0),
            )
        }
    }
}
