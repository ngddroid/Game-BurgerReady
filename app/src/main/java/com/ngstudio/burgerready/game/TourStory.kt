package com.ngstudio.burgerready.game

/**
 * World Tour story lines (docs/WORLD_TOUR_DESIGN.md §6, docs/STORY_SCRIPT.md §9). Per region: arrival (cut-scene at
 * step 1), halfway (card at step 10), farewell (cut-scene when the flagship's NEXT REGION pad is bought). The last
 * region has no farewell: the tour finale plays instead. [Story] turns these into beats; their ids are saved.
 */
object TourStory {
    private val P = Speaker.PATTY
    private val H = Speaker.HAZEL
    private val F = Speaker.FENNA
    private val D = Speaker.DOT
    private val S = Speaker.SCRAPS
    private val C = Speaker.CLEMENTINE
    private fun l(who: Speaker, text: String) = Line(who, text)

    class RegionBeats(val region: String, val arrive: List<Line>, val mid: List<Line>, val farewell: List<Line>)

    val OPENING = listOf(
        l(H, "That golden book won't fill itself, kid."),
        l(F, "I drew you a map. Twenty-five regions, all hungry."),
        l(D, "I already bought the tickets. Window seats!"),
        l(S, "I'll carry the bags. I won't open them, promise."),
        l(P, "Then let's go. The World Tour starts now!"),
    )

    val FINALE = listOf(
        l(H, "Patty. Five hundred and twelve restaurants."),
        l(P, "And one little wagon that started it all."),
        l(F, "Best partner I ever had."),
        l(D, "Best burgers I ever ate."),
        l(S, "Best dishes I ever washed!"),
        l(C, "Best… fine, the best. Don't tell anyone."),
        l(H, "The map has more edges than you think, kid."),
        l(P, "Then we keep cooking. Bun voyage!"),
    )

    val REGIONS: List<RegionBeats> = listOf(
        RegionBeats("harbor",
            listOf(l(D, "Maple Harbor, where my grandpa fished! Smell that salt?"), l(P, "I smell fries. Same thing, right?"), l(D, "Close enough. The fog rolls in at noon, so cook fast!")),
            listOf(l(D, "Half the harbor eats here now. The gulls want in too."), l(P, "No gulls behind the counter!")),
            listOf(l(D, "The harbor is yours, chef. Grandpa would be proud."), l(H, "Postcard received! Next stop, Blossom Valley."), l(P, "Blossoms, here we come!"))),
        RegionBeats("blossom",
            listOf(l(H, "I came here every spring with the wagon. Look at it now."), l(P, "Pink trees everywhere! Even the buns look happy."), l(H, "Petals land in the fryer if you're slow. Stay quick.")),
            listOf(l(H, "Half a valley of grills. The bees are jealous."), l(P, "We'll save them a bun.")),
            listOf(l(H, "You turned my favourite valley into a feast, Patty."), l(F, "Next region is mine: the savanna. Bring sunglasses."), l(P, "Sunglasses packed!"))),
        RegionBeats("savanna",
            listOf(l(F, "My old kiosk route. The sun here almost cooks for you."), l(P, "Then why did your robots melt?"), l(F, "We don't talk about the robots. Open that drive-thru!")),
            listOf(l(F, "Ten stops in. The jeeps line up for your window."), l(P, "Keep that window stocked, partner!")),
            listOf(l(F, "Okay. The savanna loves you more than it loved me."), l(H, "Hot postcard! Next is Polar Outpost, so pack big mittens."), l(P, "From sunburn to snowballs!"))),
        RegionBeats("polar",
            listOf(l(S, "I stowed away on a supply plane once. Long story."), l(P, "Scraps, it's freezing! Who eats burgers here?"), l(S, "Scientists eat EVERYTHING. Keep the grills hot.")),
            listOf(l(S, "The aurora is out! Even the penguins stopped to look."), l(P, "Penguins want shakes. Go, go!")),
            listOf(l(S, "The station chief gave me a medal. For dishes!"), l(H, "Proud of you, Scraps. Next page: a river in the rain."), l(P, "Riverboat Delta, bring boots!"))),
        RegionBeats("delta",
            listOf(l(D, "We came by riverboat! I only fell in twice."), l(P, "Rain on the roof, steam on the grill. Cozy!"), l(D, "River folk love veggie burgers. Lead with those!")),
            listOf(l(D, "My newsletter just hit ten thousand readers!"), l(P, "Ten thousand? Dot, that's a whole town!")),
            listOf(l(D, "The whole river waved goodbye. With napkins!"), l(C, "Next is Castle Hill. I review there, personally."), l(P, "No pressure, then."))),
        RegionBeats("castle",
            listOf(l(C, "Castle Hill. The market here is older than the castle."), l(P, "Banners, towers and cobbles. Fancy!"), l(C, "Fancy eaters too. Every order must be perfect.")),
            listOf(l(C, "Halfway, and not one cold fry. I am… impressed."), l(P, "Did Clementine just smile?")),
            listOf(l(C, "Five stars, castle-wide. Don't let it go to your toque."), l(F, "Next stop, Frontier Gulch. Dust and cactus, my kind of place."), l(P, "Saddle up!"))),
        RegionBeats("gulch",
            listOf(l(F, "In the gulch, the fastest window wins. Ready?"), l(P, "Faster than nine seconds?"), l(F, "Ha! Faster than my robots, anyway.")),
            listOf(l(F, "Ten saloons sold out. The horses want fries."), l(P, "Horses can't have fries, Fenna.")),
            listOf(l(F, "The sheriff made you a deputy. Of lunch."), l(H, "Next is Coconut Cove. Sun hats on, kids."), l(P, "Hammocks, here I come!"))),
        RegionBeats("cove",
            listOf(l(D, "Coconut Cove! I wrote my first review on this beach."), l(P, "Turquoise water and a grill in the sand. Perfect."), l(D, "Island time is slow, but the guests aren't. Hop to it!")),
            listOf(l(D, "Someone found a treasure chest. It's full of ketchup!"), l(P, "Best treasure ever.")),
            listOf(l(D, "The cove threw us a goodbye bonfire!"), l(H, "Next, my old friends in Alpine Meadow. Say hi to the cows."), l(P, "Moo-ving on!"))),
        RegionBeats("alpine",
            listOf(l(H, "Fresh milk, fresh cheese, fresh air. Cook like it matters."), l(P, "Cheeseburgers in the mountains. Dream come true!"), l(H, "Mind the cowbells. They mean a crowd is coming.")),
            listOf(l(H, "Half the valley's cheese goes on your burgers now."), l(P, "And the other half on my nose.")),
            listOf(l(H, "The meadow will miss you. So will the goats."), l(S, "Next is Pixel Arcade. I know all the high scores."), l(P, "Of course you do."))),
        RegionBeats("arcade",
            listOf(l(S, "Welcome to Pixel Arcade! Every burger is a bonus round."), l(P, "Neon, beeps and burgers. Let's play!"), l(S, "Combos score big here. Don't break the chain!")),
            listOf(l(S, "New high score, chef. Ten arcades fed!"), l(P, "Can I put my initials in?")),
            listOf(l(S, "Game over? Nope, next level: Moonlit Hollow."), l(D, "Is it spooky? I'm not scared… a little scared."), l(P, "I'll bring a night light!"))),
        RegionBeats("hollow",
            listOf(l(S, "Moonlit Hollow! Raccoons love it, and so do friendly ghosts."), l(P, "Pumpkins and lanterns everywhere. It's kind of cozy."), l(S, "Night owls tip well. Keep the combo glowing.")),
            listOf(l(S, "An owl ordered twelve fries. I think it's a party."), l(P, "Twelve fries for one owl, coming up!")),
            listOf(l(S, "The hollow says thanks. In whispers."), l(D, "Next is Funfair Pier! Rides, games, cotton candy!"), l(P, "I call the ferris wheel!"))),
        RegionBeats("funfair",
            listOf(l(D, "Funfair Pier! I've been on every ride, twice."), l(P, "Burgers by a carousel? We can do that."), l(D, "VIP ticket holders tip big. Watch for the sparkles!")),
            listOf(l(D, "Ten stands open! The ticket booth ran out of tickets."), l(P, "Then everyone gets a burger instead!")),
            listOf(l(D, "Best funfair ever. I won you a giant bun plush."), l(C, "Next, Sandstone Ruins. History, sand and a very old oven."), l(P, "Ancient burgers?"))),
        RegionBeats("ruins",
            listOf(l(C, "These ruins had a bakery once. Three thousand years ago."), l(P, "Then we're reopening it. With fries."), l(C, "Explorers are picky eaters. Keep every order exact.")),
            listOf(l(C, "I found a carving of a burger. Very old taste."), l(P, "History repeats itself!")),
            listOf(l(C, "You fed an ancient city. My notes say 'acceptable'."), l(F, "Next is Skyreach Isles, the floating islands. Hold your hat!"), l(P, "And my patties!"))),
        RegionBeats("sky",
            listOf(l(F, "Up here the wind does the delivery. Nice, right?"), l(P, "Floating islands! Don't look down, don't look down."), l(F, "Air's light on the high isles. Stack higher!")),
            listOf(l(F, "Ten islands fed. The windmills spin for your grills now."), l(P, "Wind-powered burgers. The future!")),
            listOf(l(F, "We're holding a balloon parade. In your honour."), l(S, "Next stop: Crystal Caverns. Shiny things, just for looking!"), l(P, "Only looking, Scraps."))),
        RegionBeats("caverns",
            listOf(l(S, "Crystal Caverns! I promise I'm only looking."), l(P, "It glows down here! The grill looks like a disco."), l(S, "Miners are hungry. Cook fast, they work hard.")),
            listOf(l(S, "Found a crystal shaped like a fry. Can I keep it?"), l(P, "Ask the miners first, Scraps.")),
            listOf(l(S, "The miners gave me the crystal. Legally!"), l(H, "Next is Orchard Farm, my cousin's place. Be nice."), l(P, "Always nice!"))),
        RegionBeats("farm",
            listOf(l(H, "My cousin grows the best apples in the land."), l(P, "Barns, hay and a tractor drive-thru?"), l(H, "Farmers start early. Patience, and big portions.")),
            listOf(l(H, "Ten farms fed. My cousin wants the recipe."), l(P, "The secret is love. And pickles.")),
            listOf(l(H, "The farm sent you a crate of apples. And a goat."), l(F, "Next is Lunar Colony. Real space this time, partner."), l(P, "I'll strap down the ketchup."))),
        RegionBeats("lunar",
            listOf(l(F, "Lunar Colony. Moon dust, domes and very hungry pilots."), l(P, "Low gravity! My stack is floating again!"), l(F, "Carry more, walk less. Physics is on your side.")),
            listOf(l(F, "Ten domes running. Earth is watching through telescopes."), l(P, "Wave at Earth, everybody!")),
            listOf(l(F, "Moon done. Never thought I'd say that."), l(D, "Next is Lantern Lagoon. A floating night market!"), l(P, "Lanterns on the water. Beautiful."))),
        RegionBeats("lagoon",
            listOf(l(D, "Lantern Lagoon! The market floats, so mind your balance."), l(P, "Boats, lanterns and burgers. Magic!"), l(D, "VIPs paddle in at dusk. Serve them first!")),
            listOf(l(D, "Ten rafts of hungry guests! My readers want photos."), l(P, "Make sure you get my good side.")),
            listOf(l(D, "The lagoon lit a lantern with your name on it."), l(H, "Next is Amber Woods. Autumn, my favourite season."), l(P, "Crunchy leaves, here I come!"))),
        RegionBeats("amber",
            listOf(l(H, "I wrote my first recipe under these very trees."), l(P, "Golden leaves everywhere. It smells like toast!"), l(H, "Hikers come in cold. Warm them up, fast.")),
            listOf(l(H, "Ten cabins of happy hikers. The squirrels are jealous."), l(P, "Squirrels get the crumbs!")),
            listOf(l(H, "The woods will remember your grill, kid."), l(F, "Next is Gearworks City. Machines, my people!"), l(P, "No robots in my kitchen, Fenna."))),
        RegionBeats("gearworks",
            listOf(l(F, "Gearworks City. Everything ticks, whistles or steams."), l(P, "Can the machines flip burgers?"), l(F, "No, I checked. Your helpers run faster here, though.")),
            listOf(l(F, "Ten workshops fed. The clocktower chimes for lunch now."), l(P, "Lunchtime, every hour!")),
            listOf(l(F, "The city made you a brass spatula. It's heavy."), l(S, "Next is Mushroom Glade. Glowing caps, not for eating."), l(P, "Noted, Scraps."))),
        RegionBeats("glade",
            listOf(l(S, "Mushroom Glade! My cousins live here, so be polite."), l(P, "Glowing mushrooms and fireflies. It's like a dream."), l(S, "Pets love it here. Bring yours, they help even more.")),
            listOf(l(S, "My cousins finally said hello. With a fry in each paw."), l(P, "Family dinner!")),
            listOf(l(S, "The glade threw a firefly parade for us."), l(C, "Next is Bamboo Springs. Hot springs and quiet, finally."), l(P, "Relaxing. And cooking."))),
        RegionBeats("springs",
            listOf(l(C, "Bamboo Springs. I come here to rest my taste buds."), l(P, "Steam, stone lanterns and bamboo. So calm."), l(C, "Guests here are patient. Don't test it.")),
            listOf(l(C, "Ten springs. My taste buds feel rested and happy."), l(P, "Was that a compliment?")),
            listOf(l(C, "Serene, swift and tasty. A rare combination."), l(H, "Next is Frostfest Village. Festival time, kids!"), l(P, "Snow and celebrations!"))),
        RegionBeats("frostfest",
            listOf(l(H, "Frostfest! The whole village decorates for a week."), l(P, "Lights, gifts and snowflakes. Best season ever!"), l(H, "Festival crowds love combos. Keep them coming.")),
            listOf(l(H, "Ten stalls glowing. Someone built a snow-Patty!"), l(P, "It has my ears!")),
            listOf(l(H, "The village gave you the festival key. Keep it safe."), l(D, "Next is Fossil Valley. Big friendly dinosaurs!"), l(P, "Big appetites, too."))),
        RegionBeats("fossil",
            listOf(l(D, "Fossil Valley! Old bones, hungry guests."), l(P, "Ferns, footprints and a volcano. Wild!"), l(D, "Grills cook fast on the warm ground. Use it!")),
            listOf(l(D, "Ten dig sites fed! The scientists want seconds."), l(P, "Seconds are on the house.")),
            listOf(l(D, "One last region, chef. The Golden Capital!"), l(H, "I'll be there. We all will."), l(P, "The final page. Let's make it gold."))),
        RegionBeats("capital",
            listOf(l(P, "The Golden Capital. Every street shines!"), l(F, "I booked the best corner. Fast Lane is now Patty's Partner."), l(C, "I'll be reviewing every single burger."), l(S, "I polished the statues. Only polished!")),
            listOf(l(D, "Ten golden kitchens! My newsletter has a million readers!"), l(P, "A million? Dot!")),
            emptyList()),  // the last flagship has no NEXT REGION: the tour finale plays instead
    )
}
