package com.ngstudio.burgerready.game

/**
 * The story of the Bun Voyage (docs/STORY_SCRIPT.md): data-driven dialogue "beats" shown as speech cards.
 *
 * A beat belongs to a restaurant and fires once: on arrival, when a milestone [Cue] first shows up in the running
 * [World] (first table, first hire, drive-thru, rush, critic, raccoon…), when the restaurant is completed (EXPAND
 * bought) or, in the last restaurant, at the grand finale. Seen beat ids are saved (`SaveData.story`) and can be
 * re-read in the Voyage Book. Nothing here touches the simulation: [cues] only reads a World.
 */
enum class Speaker(val displayName: String, val role: String, val portrait: String, val fallback: String, val color: Int) {
    PATTY("Chef Patty", "That's you!", "skin_patty", "card_guest_corgi", 0xF2542D),
    HAZEL("Hazel", "Your mentor", "cast_hazel", "card_guest_bear", 0x8B5A3C),
    FENNA("Fenna", "Your rival", "cast_fenna", "card_guest_fox", 0x3D5BA8),
    DOT("Dot", "Your #1 regular", "cast_dot", "card_guest_duck", 0x1FA6B8),
    SCRAPS("Scraps", "Raccoon at large", "cast_scraps", "card_guest_raccoon", 0x5B4A70),
    CLEMENTINE("Clementine", "Food critic", "cast_clementine", "card_guest_cat", 0x9B5CC8),
}

/** Milestones the story listens for; [bit] is their place in the mask from [Story.cues]. */
enum class Cue(val bit: Int) {
    TABLE(1), HIRE(2), DRIVE(4), RUSH(8), CRITIC(16), RACCOON(32), FIRE(64), VIP(128), COMBO(256), HALF(512), FULL(1024),
}

enum class Trigger {
    /** First visit to the restaurant (after the welcome banner). */
    ARRIVE,
    /** A milestone [Cue] is present in the running restaurant. */
    CUE,
    /** The EXPAND pad was bought: shown before the "restaurant complete" celebration. */
    COMPLETE,
    /** The last restaurant is fully built. */
    FINALE,
    /** Straight after the finale: the late-game hook. */
    POSTGAME,
    /** Home screen, for saves that started before the story existed (they never saw the Bun Wagon arrival). */
    PROLOGUE,
    /** First visit to a completed restaurant: introduces its gold stars. */
    GOLD_INTRO,
    /** Every gold star in every restaurant earned. */
    EPILOGUE,
}

class Line(val who: Speaker, val text: String)

/** [scene] = played as a cut-scene with the restaurant paused (World Tour arrivals); completions and finales always are. */
class Beat(val id: String, val store: Int, val trigger: Trigger, val title: String, val lines: List<Line>, val cue: Cue? = null, val scene: Boolean = false)

object Story {
    private fun l(who: Speaker, text: String) = Line(who, text)
    private val P = Speaker.PATTY
    private val H = Speaker.HAZEL
    private val F = Speaker.FENNA
    private val D = Speaker.DOT
    private val S = Speaker.SCRAPS
    private val C = Speaker.CLEMENTINE

    private fun arrive(store: Int, title: String, vararg lines: Line) = Beat("s${store}_arrive", store, Trigger.ARRIVE, title, lines.toList())
    private fun cue(store: Int, cue: Cue, title: String, vararg lines: Line) = Beat("s${store}_${cue.name.lowercase()}", store, Trigger.CUE, title, lines.toList(), cue)
    private fun done(store: Int, title: String, vararg lines: Line) = Beat("s${store}_done", store, Trigger.COMPLETE, title, lines.toList())

    /** Every beat, in story order: the classic Voyage, then the World Tour. Ids are saved: never rename one. */
    val BEATS: List<Beat> by lazy { CLASSIC_BEATS + tourBeats() }

    /**
     * World Tour beats from [TourStory]: the opening and each region's arrival on its first restaurant, a halfway card
     * on its 10th, a farewell when its flagship opens the next region; the tour finale on the very last restaurant.
     */
    private fun tourBeats(): List<Beat> {
        val out = ArrayList<Beat>()
        val first = Tour.globalIndex(0, 0)
        out.add(Beat("tour_open", first, Trigger.ARRIVE, "The World Tour", TourStory.OPENING, scene = true))
        for (rb in TourStory.REGIONS) {
            val r = Tour.regionIndex(rb.region)
            val name = Tour.REGIONS[r].name
            out.add(Beat("t_${rb.region}_arrive", Tour.globalIndex(r, 0), Trigger.ARRIVE, name, rb.arrive, scene = true))
            out.add(Beat("t_${rb.region}_mid", Tour.globalIndex(r, 9), Trigger.ARRIVE, "$name, halfway", rb.mid))
            if (rb.farewell.isNotEmpty()) out.add(Beat("t_${rb.region}_done", Tour.globalIndex(r, Tour.PER_REGION - 1), Trigger.COMPLETE, "Farewell, $name", rb.farewell))
        }
        out.add(Beat("tour_finale", Restaurants.TOTAL - 1, Trigger.FINALE, "The Last Postcard", TourStory.FINALE))
        return out
    }

    private val CLASSIC_BEATS: List<Beat> = listOf(
        Beat("prologue", -1, Trigger.PROLOGUE, "The Voyage Book", listOf(
            l(H, "Patty! Got a minute for your old boss?"),
            l(H, "Forty years I drove the Bun Wagon. Then I gave you the keys."),
            l(H, "And my Voyage Book: twelve places I always meant to cook."),
            l(P, "And I promised to fill every page. One restaurant at a time!"),
            l(H, "That's the spirit. Send me a postcard from each one."),
        )),
        Beat("gold_intro", -1, Trigger.GOLD_INTRO, "Gold Stars", listOf(
            l(H, "Back at an old page? Try for its three gold stars."),
            l(H, "Each star pays gems and boosts this place's idle income."),
        )),

        // ---- 1 Bun Wagon (the tutorial)
        arrive(0, "The Keys",
            l(H, "Forty years I drove this wagon, Patty. Today it's yours."),
            l(P, "Mine? Hazel, I just wash the dishes!"),
            l(H, "Then you know where every crumb hides. Take my Voyage Book too."),
            l(P, "Twelve pages… twelve places?"),
            l(H, "Places I always meant to cook. Fill them for me, starting here."),
            l(H, "See that glowing pad? Stand on it to build your first grill."),
        ),
        cue(0, Cue.TABLE, "The First Regular",
            l(D, "Is this the Bun Wagon? I'm Dot, your first regular!"),
            l(P, "We have regulars?"),
            l(D, "You do now. Extra pickles, please!"),
        ),
        cue(0, Cue.HIRE, "A Team of Two",
            l(H, "A cashier! Now you cook while they serve."),
            l(P, "Feels strange, giving orders."),
            l(H, "You're not giving orders, kid. You're building a team."),
        ),
        done(0, "Page One",
            l(H, "Page one, stamped. I knew you could do it."),
            l(P, "Where to next, Hazel?"),
            l(H, "Page two is by the sea. Salt air makes everything tastier."),
            l(P, "Bun voyage, little wagon!"),
        ),

        // ---- 2 Seaside Shack
        arrive(1, "The Fox Next Door",
            l(P, "Sand, sun and a shack of my own!"),
            l(F, "Cute shack. I'm Fenna, from Fast Lane Burgers."),
            l(F, "My robots flip a burger in nine seconds flat."),
            l(P, "Do they taste like anything?"),
            l(F, "Taste is a rounding error, sweetie."),
        ),
        cue(1, Cue.CRITIC, "The Critic",
            l(C, "I'm Clementine, food critic. I have eaten everywhere."),
            l(C, "Impress me, corgi. Fast service, full order."),
            l(P, "One perfect burger, coming up!"),
        ),
        cue(1, Cue.HALF, "A Long Way for Lunch",
            l(D, "Two ferries, one bus and a bike. Worth it!"),
            l(P, "Dot! You came all this way?"),
            l(D, "Regulars follow their chef. The usual, please!"),
        ),
        done(1, "Postcard Two",
            l(H, "Got your postcard! Page two, stamped."),
            l(H, "Next page has a jukebox and a checkered floor. A real diner."),
            l(F, "A diner? I'm opening a bigger one."),
            l(P, "See you there, Fenna!"),
        ),

        // ---- 3 Retro Diner
        arrive(2, "Jukebox Nights",
            l(P, "Checkered floors and a jukebox! Hazel would love it here."),
            l(H, "I danced on that floor once, badly. Make those cheeseburgers sing!"),
        ),
        cue(2, Cue.DRIVE, "The Window",
            l(F, "A drive-thru window? Copying my homework?"),
            l(P, "Hazel's wagon had a window before you were born!"),
            l(H, "Ha! Drop food at the window, then serve the cars."),
        ),
        cue(2, Cue.FIRE, "Grease Fire!",
            l(H, "Grease fire! Stand by the grill until it's out."),
            l(H, "Don't panic. Kitchens run hot, chefs stay cool."),
        ),
        done(2, "The Newsletter",
            l(D, "Best cheeseburger ever! It's going in my newsletter!"),
            l(P, "You have a newsletter?"),
            l(D, "Twelve readers! Next stop, the mountains?"),
            l(H, "Pack a scarf, Patty. Page four is chilly."),
        ),

        // ---- 4 Mountain Lodge
        arrive(3, "Snow on the Roof",
            l(P, "Brrr! Snow on the roof, fire in the grill."),
            l(H, "Lodge folk come in hungry. Double stacks, kid, and lots of them."),
        ),
        cue(3, Cue.RACCOON, "Scraps",
            l(S, "Nice fries. Mine now!"),
            l(P, "Hey! Come back here!"),
            l(H, "That's Scraps. Run into him and he drops the loot!"),
        ),
        cue(3, Cue.HALF, "Frozen Robots",
            l(F, "My kiosks froze solid. Mind if I warm up in here?"),
            l(P, "Grab a seat. Cocoa's on the house."),
            l(F, "…Thanks. Don't tell anyone."),
        ),
        done(3, "City Lights Ahead",
            l(H, "Four pages! Next is the big city: rooftops, lights and VIPs."),
            l(S, "City, huh? Bigger bins…"),
            l(P, "I heard that, Scraps!"),
        ),

        // ---- 5 City Rooftop
        arrive(4, "Her Town",
            l(P, "The whole city is glowing down there!"),
            l(F, "Welcome to my town, corgi. Rooftop rent is brutal."),
            l(P, "So is my grill. Let's cook!"),
        ),
        cue(4, Cue.VIP, "Very Important Paws",
            l(D, "Psst! The guest with the sparkle is a VIP."),
            l(D, "VIPs tip triple. Serve them first!"),
        ),
        cue(4, Cue.RUSH, "Rush Hour",
            l(H, "Here comes rush hour! Keep the counter stocked."),
            l(H, "Don't stop moving. A walkout breaks your combo."),
        ),
        done(4, "Four Stars",
            l(C, "Rooftop review: four stars. The fifth is for the shake."),
            l(P, "Hazel! We got a real review!"),
            l(H, "Frame it, then head for the desert. They love a drive-in."),
        ),

        // ---- 6 Desert Drive-In
        arrive(5, "The Race",
            l(F, "Drive-thru race. Your window against my Fast Lane."),
            l(F, "Loser buys the sodas."),
            l(P, "You're on!"),
        ),
        cue(5, Cue.DRIVE, "Open Window",
            l(P, "Window's open! Bring on the cars!"),
            l(H, "Drivers hate waiting. Keep that window stocked."),
        ),
        cue(5, Cue.HALF, "Dot Counts",
            l(F, "My robots served more cars than you."),
            l(D, "But half your cars left hungry. I counted."),
            l(F, "You… counted?"),
        ),
        done(5, "Sodas on Me",
            l(F, "Fine, you win. Sodas are on me."),
            l(P, "Stay for a burger?"),
            l(F, "One. For research."),
            l(H, "Page seven is all neon. Tokyo, here comes Patty!"),
        ),

        // ---- 7 Tokyo Neon Alley
        arrive(6, "Neon Nights",
            l(P, "So many lights! So many hungry faces!"),
            l(D, "I learned three words on the plane. All three mean 'burger'."),
        ),
        cue(6, Cue.COMBO, "Combo City",
            l(H, "Hear that? Fast service builds a combo."),
            l(H, "Keep the chain going and the tips keep climbing!"),
        ),
        cue(6, Cue.RACCOON, "Stowaway",
            l(S, "Fancy meeting you here, chef!"),
            l(P, "Scraps? You followed me to Tokyo?"),
            l(S, "The fries are crunchier here. It's business."),
        ),
        done(6, "Worth the Flight",
            l(C, "Tokyo verdict: worth the flight."),
            l(P, "Seven pages, Hazel!"),
            l(H, "Past halfway! Next, a treehouse in the jungle."),
        ),

        // ---- 8 Jungle Treehouse
        arrive(7, "Up a Tree",
            l(P, "A kitchen in a tree! How do the guests get up here?"),
            l(D, "Very carefully."),
        ),
        cue(7, Cue.HALF, "Taking Notes",
            l(F, "My investors left. Robot kiosks don't climb trees."),
            l(P, "So what will you do now?"),
            l(F, "Watch you cook. Take notes."),
        ),
        cue(7, Cue.RUSH, "Jungle Rush",
            l(H, "Jungle rush! Nobody cooks alone, so let your helpers help."),
        ),
        done(7, "A Volcano?",
            l(H, "Eight pages! I drew the next one, a volcano, when I was young."),
            l(P, "A grill on a volcano? Hot!"),
        ),

        // ---- 9 Volcano Grill
        arrive(8, "Warm Floors",
            l(P, "The floor is warm and the grill is warmer. Let's cook!"),
            l(H, "Mind the grease out there. Fires love a volcano."),
        ),
        cue(8, Cue.FIRE, "Fire Next Door",
            l(F, "Patty, my kiosk is on fire! Can you… help me?"),
            l(P, "Grab an extinguisher. We'll do it together!"),
        ),
        cue(8, Cue.HALF, "Why Help?",
            l(F, "Why did you help me? I'm your rival."),
            l(P, "Hazel says a kitchen is a team. Even the one next door."),
        ),
        done(8, "First Lesson",
            l(F, "Teach me to grill, Patty. A real burger, by hand."),
            l(P, "Lesson one: you can't rush a patty."),
            l(H, "Next page is space. Don't ask me how I drew that."),
        ),

        // ---- 10 Space Station Diner
        arrive(9, "Low Gravity",
            l(P, "Low gravity! I can carry two extra burgers!"),
            l(H, "Can you hear me up there, kid? Strap down the ketchup."),
        ),
        cue(9, Cue.HIRE, "400 Readers",
            l(D, "I took a rocket! My newsletter has 400 readers now!"),
            l(P, "Dot, you're famous!"),
            l(D, "WE are famous. The usual, please, in zero-g."),
        ),
        cue(9, Cue.RUSH, "Hand-Flipped",
            l(F, "Patty, look! I flipped a burger by hand, in space!"),
            l(P, "It's perfect! Well… it's round-ish."),
        ),
        done(9, "Out of This World",
            l(C, "Out of this world. And I don't say that lightly."),
            l(H, "Two pages left, and one is under the sea. Bring snorkels!"),
        ),

        // ---- 11 Underwater Dome
        arrive(10, "Under the Sea",
            l(P, "Fish outside every window. They look hungry too!"),
            l(S, "Psst, I'm not stealing. Just… looking."),
        ),
        cue(10, Cue.RACCOON, "Old Habits",
            l(S, "Sorry. Old habits."),
            l(P, "Scraps, why do you keep stealing?"),
            l(S, "Nobody ever just offered me a burger."),
            l(P, "Well, I'm offering. Come by after closing."),
        ),
        cue(10, Cue.HALF, "Something Special",
            l(H, "One page left after this. The last one is special."),
            l(P, "Special how?"),
            l(H, "You'll see, kid. You'll see."),
        ),
        done(10, "The Last Page",
            l(H, "The last page is Candy Kingdom. I'll meet you there."),
            l(P, "You're coming? Really?"),
            l(H, "To your grand opening? Wouldn't miss it."),
        ),

        // ---- 12 Candy Kingdom (no EXPAND pad: the finale fires when it is fully built)
        arrive(11, "Sweet Ending",
            l(P, "Gumdrop trees and lollipop lamps! The last page!"),
            l(D, "I brought every issue of my newsletter. Signed!"),
        ),
        cue(11, Cue.HIRE, "A Job for Scraps",
            l(S, "I brought back everything I took. Some fries are… vintage."),
            l(P, "Keep them. How about a job instead?"),
            l(S, "Me? I could wash dishes."),
            l(P, "That's exactly how I started."),
        ),
        cue(11, Cue.HALF, "Partners",
            l(F, "My crew wants to help. Real grills, no robots."),
            l(P, "Welcome to the team, partner!"),
        ),
        Beat("s11_finale", 11, Trigger.FINALE, "Grand Opening", listOf(
            l(H, "Patty. Look at this place."),
            l(P, "Twelve pages, Hazel. Every one stamped."),
            l(H, "You did more than fill my book. You built a family."),
            l(F, "And a franchise!"),
            l(D, "And a newsletter empire!"),
            l(S, "And the dishes are done!"),
            l(H, "One last gift: a golden book. Every page is blank."),
            l(H, "Now you write your own. Bun voyage, Chef Patty."),
        )),
        Beat("golden_tour", 11, Trigger.POSTGAME, "The Golden Tour", listOf(
            l(C, "Not so fast, chef. The Golden Guide is watching."),
            l(C, "Every restaurant can earn three gold stars. Thirty-six in all."),
            l(P, "Then the voyage isn't over. Let's go for gold!"),
        )),
        Beat("golden_book", -1, Trigger.EPILOGUE, "Every Page Gold", listOf(
            l(C, "Thirty-six gold stars? That has never happened, ever."),
            l(H, "Proud of you, kid. Every page is gold."),
            l(P, "Thanks, Hazel. Now… who's hungry?"),
        )),
    )

    private val byId by lazy { BEATS.associateBy { it.id } }
    fun beat(id: String): Beat? = byId[id]
    fun beatsFor(store: Int): List<Beat> = BEATS.filter { it.store == store && it.trigger != Trigger.POSTGAME && it.trigger != Trigger.EPILOGUE }
    fun completion(store: Int): Beat? = BEATS.firstOrNull { it.store == store && it.trigger == Trigger.COMPLETE }
    val finale: Beat get() = byId.getValue("s11_finale")
    val postgame: Beat get() = byId.getValue("golden_tour")

    /**
     * The next beat to show while playing restaurant [store], or null. [cues] is the mask from [cues];
     * [completeOnEntry] = the restaurant was already finished when the visit began (its old beats stay unshown,
     * the Voyage Book lets the player read them). Arrival comes first; milestone beats fire in any order once their
     * cue appears; the finale fires when the last restaurant is fully built.
     */
    fun next(store: Int, cues: Int, seen: Set<String>, completeOnEntry: Boolean): Beat? {
        // arrived = every arrival beat of this restaurant has been shown (the tour's first one has two)
        val arrived = BEATS.none { it.store == store && it.trigger == Trigger.ARRIVE && it.id !in seen }
        // the finale outranks any milestone beat still pending in the last restaurant
        if ((cues and Cue.FULL.bit) != 0 && (arrived || completeOnEntry)) {
            for (b in BEATS) if (b.store == store && b.trigger == Trigger.FINALE && b.id !in seen) return b
        }
        for (b in BEATS) {
            if (b.store != store || b.id in seen) continue
            when (b.trigger) {
                Trigger.ARRIVE -> if (!completeOnEntry) return b
                Trigger.CUE -> if (!completeOnEntry && arrived && b.cue != null && (cues and b.cue.bit) != 0) return b
                Trigger.FINALE -> if ((cues and Cue.FULL.bit) != 0) return b
                else -> {}
            }
        }
        return null
    }

    /** Milestone mask of a running restaurant (read-only; allocation-free, called ~15×/s on the GL thread). */
    fun cues(w: World): Int {
        var m = 0
        for (i in w.tables.indices) if (w.tables[i].built) { m = m or Cue.TABLE.bit; break }
        if (w.staff.isNotEmpty()) m = m or Cue.HIRE.bit
        if (w.driveBuilt) m = m or Cue.DRIVE.bit
        if (w.rushT > 0f) m = m or Cue.RUSH.bit
        for (i in w.queue.indices) {
            val c = w.queue[i]
            if (c.critic) m = m or Cue.CRITIC.bit
            if (c.vip) m = m or Cue.VIP.bit
        }
        for (i in w.troubles.indices) {
            val t = w.troubles[i]
            if (t.done) continue
            if (t.kind == TroubleKind.RACCOON) m = m or Cue.RACCOON.bit
            if (t.kind == TroubleKind.FIRE) m = m or Cue.FIRE.bit
        }
        if (w.combo >= 2f) m = m or Cue.COMBO.bit
        val built = w.builtCount
        if (built * 2 >= w.cfg.buildPads) m = m or Cue.HALF.bit
        if (built >= w.cfg.buildPads) m = m or Cue.FULL.bit
        return m
    }

    /** Tutorial tips for the Bun Wagon, in Hazel's voice (shown with her portrait in the HUD tip box). */
    fun tutorialTip(w: World): String {
        val p = w.player
        val pad = w.visiblePads().firstOrNull()
        if (w.stations.none { it.built }) return if (w.cash >= (pad?.remaining ?: 0L)) "Stand on the glowing pad. That's where your grill goes!" else "Grab some cash first, then build."
        if (p.stack.isNotEmpty() && p.stack[0] == Item.TRAY) return "Dirty trays go in the bin. Every time."
        if (p.stack.isNotEmpty()) return "Food goes on the counter. Hot and fast!"
        val front = w.queue.firstOrNull()
        if (front != null && front.state == CState.ORDERING && !w.cashierPresent && w.staff.none { it.role == StaffRole.CASHIER }) {
            var ok = true
            for (i in front.order.indices) if (front.order[i] - front.got[i] > w.counterRacks[i]) ok = false
            if (ok) return "Hop behind the register. Your guest is waiting!"
        }
        if (w.counterPile.amount >= 10) return "Your cash is on the counter. Scoop it up!"
        if (pad != null && w.cash >= pad.remaining) return "You can afford the next build. Go on, kid!"
        if (w.tables.any { it.trays > 0 }) return "Clean tables, happy guests."
        if (w.stations.any { it.built && it.tray > 0 }) return "Food's ready. Grab it off the grill!"
        return when {
            w.builtCount >= 6 -> "Serve fast for a COMBO. Bigger tips!"
            w.builtCount >= 4 -> "What you build stays forever. Keep going!"
            else -> "Patience. The grill is sizzling."
        }
    }
}
