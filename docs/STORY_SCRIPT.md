# Bun Voyage! — Story Script (round 3, 2026-11-28)

What the story adds, who is in it, and every line the game shows. **The lines live in code** (`game/Story.kt`, the single
source of truth); the per-restaurant section below was generated from it. Rules for writing more are at the end.

Everything here is original: names, characters, places and beats were written for this game. No text, names or story
beats are taken from other games or brands.

## 1. What our story was missing
The gap analysis (`docs/GAP_ANALYSIS.md` §4) found: a mascot with no reason to travel, no recurring characters, no
dialogue, no goal beyond "next restaurant", nothing after restaurant 12, and tutorial text in a neutral system voice.
The genre leaders mostly frame progress with a goal (restore, renovate, expand) and a small cast; few arcade-idle games
give the *travel* itself a reason. That is our hook.

## 2. Premise: why Chef Patty is on a "Bun Voyage"
Hazel, a gruff, warm old bear, drove a little burger wagon around for forty years. Patty the corgi washed her dishes.
On the day Hazel retires she hands Patty two things: the keys to the Bun Wagon, and her battered **Voyage Book**, a
sketchbook of twelve places she always meant to open a restaurant and never did.

> Fill every page, and send me a postcard from each one.

Every restaurant is a page of the book. Finishing one (buying NEW RESTAURANT) stamps the page and sends Hazel a
postcard; her reply points to the next page. The title is Hazel's goodbye pun: *bun voyage, kid.*

**The player's goal, in one line:** fill Hazel's twelve pages, then earn the gold stars that turn every page gold.

## 3. Cast
Small, recurring, each with one clear voice. Portraits are rendered from the game's own character models
(`tools/blender/render_ui.py cast` → `assets/ui/cast_*.png`); if one is missing, the guest head of the same species is
shown instead.

| Character | Who | Voice | Arc | Look |
|---|---|---|---|---|
| **Chef Patty** (corgi) | The player. Ex-dishwasher, now a chef | Upbeat, brave, a little unsure at first. Exclamation marks, short sentences | From "I just wash the dishes" to running twelve restaurants and a family of misfits | Existing `skin_patty` |
| **Hazel** (bear) | Mentor; retired wagon cook | Warm, gruff, kitchen proverbs, calls Patty "kid". Never more than two sentences | Sends postcards from home; promises to come to the last opening and does | Bear, white toque, brown apron |
| **Fenna** (fox) | Rival; owns *Fast Lane Burgers*, robot kiosks | Smug, fast, loves numbers ("nine seconds flat"), calls Patty "corgi" or "sweetie" | Always opens next door. Loses the desert race, freezes at the lodge, catches fire at the volcano; Patty helps. Learns to grill by hand and becomes Patty's partner | Fox, navy cap and apron |
| **Dot** (duck) | Patty's first and most loyal regular | Breathless, delighted, always "the usual, please" (extra pickles) | Follows Patty by ferry, plane and rocket; her newsletter grows from 12 to 400 readers | Duck, teal beanie, yellow apron |
| **Scraps** (raccoon) | The thief behind the raccoon trouble | Cheeky, then sheepish | Steals from the Lodge on; at the Dome admits nobody ever offered him a burger; at Candy Kingdom returns the loot and takes a dishwashing job, exactly where Patty started | Raccoon, red bandana |
| **Clementine** (cat) | Food critic | Dry one-liners, reviews in stars | Her verdicts mark the milestones; after the finale she opens the Golden Tour | Cat, lilac beret |

The gameplay already had a food critic, a raccoon thief, VIPs, rushes and a drive-thru. The cast gives each of those a
face, so a mechanic and its story beat arrive together.

## 4. How beats are shown (implementation summary)
- **Speech cards**: portrait, name tag, one bubble at a time, typewriter text. Tap to continue. In-game milestone
  cards sit at the top, under the HUD, away from where players steer, and only a deliberate tap advances them; they
  also advance by themselves after a few seconds, so a beat never holds up play, and the restaurant keeps running
  underneath. Cut-scenes keep the card at the bottom. Completion, finale, post-game, epilogue and the Home prologue are
  short **cut-scenes** with the game paused; they wait for taps, so nobody misses one by looking away.
- **Triggers:** arrival (first visit, after the welcome banner), a milestone *cue* seen in the running restaurant
  (first table, first hire, drive-thru, rush, critic, raccoon, grease fire, VIP, ×2 combo, half built), completion
  (NEW RESTAURANT bought), and the finale (the last restaurant fully built). At most one beat at a time, with a
  cool-down between milestone beats.
- **Shown once**, saved in `SaveData.story`. The **Voyage Book** (Play tab → Voyage) lists every page: stamped pages,
  gold stars, and all beats seen, to re-read.
- **Saves from before round 3:** restaurants that were already finished stay quiet (their beats are readable in the
  Voyage Book), and Home plays a short prologue once so the premise is not lost.

## 5. The arc, restaurant by restaurant
Each restaurant has an arrival beat, one or two mid-build beats tied to a milestone, and a completion beat that sets up
the next page. Act one (1–4) sets up the book, the rival and the regular; act two (5–8) is the rivalry and the road;
act three (9–12) turns rivals and thieves into a team and brings Hazel to the last opening.

### 1. Bun Wagon

**Arrival** — *The Keys*

- **Hazel:** Forty years I drove this wagon, Patty. Today it's yours.
- **Patty:** Mine? Hazel, I just wash the dishes!
- **Hazel:** Then you know where every crumb hides. Take my Voyage Book too.
- **Patty:** Twelve pages… twelve places?
- **Hazel:** Places I always meant to cook. Fill them for me, starting here.
- **Hazel:** See that glowing pad? Stand on it to build your first grill.

**Mid-build:** first table built — *The First Regular*

- **Dot:** Is this the Bun Wagon? I'm Dot, your first regular!
- **Patty:** We have regulars?
- **Dot:** You do now. Extra pickles, please!

**Mid-build:** first hire — *A Team of Two*

- **Hazel:** A cashier! Now you cook while they serve.
- **Patty:** Feels strange, giving orders.
- **Hazel:** You're not giving orders, kid. You're building a team.

**Completion** (NEW RESTAURANT bought) — *Page One*

- **Hazel:** Page one, stamped. I knew you could do it.
- **Patty:** Where to next, Hazel?
- **Hazel:** Page two is by the sea. Salt air makes everything tastier.
- **Patty:** Bun voyage, little wagon!

### 2. Seaside Shack

**Arrival** — *The Fox Next Door*

- **Patty:** Sand, sun and a shack of my own!
- **Fenna:** Cute shack. I'm Fenna, from Fast Lane Burgers.
- **Fenna:** My robots flip a burger in nine seconds flat.
- **Patty:** Do they taste like anything?
- **Fenna:** Taste is a rounding error, sweetie.

**Mid-build:** a food critic arrives — *The Critic*

- **Clementine:** I'm Clementine, food critic. I have eaten everywhere.
- **Clementine:** Impress me, corgi. Fast service, full order.
- **Patty:** One perfect burger, coming up!

**Mid-build:** half the restaurant built — *A Long Way for Lunch*

- **Dot:** Two ferries, one bus and a bike. Worth it!
- **Patty:** Dot! You came all this way?
- **Dot:** Regulars follow their chef. The usual, please!

**Completion** (NEW RESTAURANT bought) — *Postcard Two*

- **Hazel:** Got your postcard! Page two, stamped.
- **Hazel:** Next page has a jukebox and a checkered floor. A real diner.
- **Fenna:** A diner? I'm opening a bigger one.
- **Patty:** See you there, Fenna!

### 3. Retro Diner

**Arrival** — *Jukebox Nights*

- **Patty:** Checkered floors and a jukebox! Hazel would love it here.
- **Hazel:** I danced on that floor once, badly. Make those cheeseburgers sing!

**Mid-build:** drive-thru built — *The Window*

- **Fenna:** A drive-thru window? Copying my homework?
- **Patty:** Hazel's wagon had a window before you were born!
- **Hazel:** Ha! Drop food at the window, then serve the cars.

**Mid-build:** first grease fire — *Grease Fire!*

- **Hazel:** Grease fire! Stand by the grill until it's out.
- **Hazel:** Don't panic. Kitchens run hot, chefs stay cool.

**Completion** (NEW RESTAURANT bought) — *The Newsletter*

- **Dot:** Best cheeseburger ever! It's going in my newsletter!
- **Patty:** You have a newsletter?
- **Dot:** Twelve readers! Next stop, the mountains?
- **Hazel:** Pack a scarf, Patty. Page four is chilly.

### 4. Mountain Lodge

**Arrival** — *Snow on the Roof*

- **Patty:** Brrr! Snow on the roof, fire in the grill.
- **Hazel:** Lodge folk come in hungry. Double stacks, kid, and lots of them.

**Mid-build:** a raccoon thief appears — *Scraps*

- **Scraps:** Nice fries. Mine now!
- **Patty:** Hey! Come back here!
- **Hazel:** That's Scraps. Run into him and he drops the loot!

**Mid-build:** half the restaurant built — *Frozen Robots*

- **Fenna:** My kiosks froze solid. Mind if I warm up in here?
- **Patty:** Grab a seat. Cocoa's on the house.
- **Fenna:** …Thanks. Don't tell anyone.

**Completion** (NEW RESTAURANT bought) — *City Lights Ahead*

- **Hazel:** Four pages! Next is the big city: rooftops, lights and VIPs.
- **Scraps:** City, huh? Bigger bins…
- **Patty:** I heard that, Scraps!

### 5. City Rooftop

**Arrival** — *Her Town*

- **Patty:** The whole city is glowing down there!
- **Fenna:** Welcome to my town, corgi. Rooftop rent is brutal.
- **Patty:** So is my grill. Let's cook!

**Mid-build:** first VIP guest — *Very Important Paws*

- **Dot:** Psst! The guest with the sparkle is a VIP.
- **Dot:** VIPs tip triple. Serve them first!

**Mid-build:** first rush hour — *Rush Hour*

- **Hazel:** Here comes rush hour! Keep the counter stocked.
- **Hazel:** Don't stop moving. A walkout breaks your combo.

**Completion** (NEW RESTAURANT bought) — *Four Stars*

- **Clementine:** Rooftop review: four stars. The fifth is for the shake.
- **Patty:** Hazel! We got a real review!
- **Hazel:** Frame it, then head for the desert. They love a drive-in.

### 6. Desert Drive-In

**Arrival** — *The Race*

- **Fenna:** Drive-thru race. Your window against my Fast Lane.
- **Fenna:** Loser buys the sodas.
- **Patty:** You're on!

**Mid-build:** drive-thru built — *Open Window*

- **Patty:** Window's open! Bring on the cars!
- **Hazel:** Drivers hate waiting. Keep that window stocked.

**Mid-build:** half the restaurant built — *Dot Counts*

- **Fenna:** My robots served more cars than you.
- **Dot:** But half your cars left hungry. I counted.
- **Fenna:** You… counted?

**Completion** (NEW RESTAURANT bought) — *Sodas on Me*

- **Fenna:** Fine, you win. Sodas are on me.
- **Patty:** Stay for a burger?
- **Fenna:** One. For research.
- **Hazel:** Page seven is all neon. Tokyo, here comes Patty!

### 7. Tokyo Neon Alley

**Arrival** — *Neon Nights*

- **Patty:** So many lights! So many hungry faces!
- **Dot:** I learned three words on the plane. All three mean 'burger'.

**Mid-build:** combo reaches x2 — *Combo City*

- **Hazel:** Hear that? Fast service builds a combo.
- **Hazel:** Keep the chain going and the tips keep climbing!

**Mid-build:** a raccoon thief appears — *Stowaway*

- **Scraps:** Fancy meeting you here, chef!
- **Patty:** Scraps? You followed me to Tokyo?
- **Scraps:** The fries are crunchier here. It's business.

**Completion** (NEW RESTAURANT bought) — *Worth the Flight*

- **Clementine:** Tokyo verdict: worth the flight.
- **Patty:** Seven pages, Hazel!
- **Hazel:** Past halfway! Next, a treehouse in the jungle.

### 8. Jungle Treehouse

**Arrival** — *Up a Tree*

- **Patty:** A kitchen in a tree! How do the guests get up here?
- **Dot:** Very carefully.

**Mid-build:** half the restaurant built — *Taking Notes*

- **Fenna:** My investors left. Robot kiosks don't climb trees.
- **Patty:** So what will you do now?
- **Fenna:** Watch you cook. Take notes.

**Mid-build:** first rush hour — *Jungle Rush*

- **Hazel:** Jungle rush! Nobody cooks alone, so let your helpers help.

**Completion** (NEW RESTAURANT bought) — *A Volcano?*

- **Hazel:** Eight pages! I drew the next one, a volcano, when I was young.
- **Patty:** A grill on a volcano? Hot!

### 9. Volcano Grill

**Arrival** — *Warm Floors*

- **Patty:** The floor is warm and the grill is warmer. Let's cook!
- **Hazel:** Mind the grease out there. Fires love a volcano.

**Mid-build:** first grease fire — *Fire Next Door*

- **Fenna:** Patty, my kiosk is on fire! Can you… help me?
- **Patty:** Grab an extinguisher. We'll do it together!

**Mid-build:** half the restaurant built — *Why Help?*

- **Fenna:** Why did you help me? I'm your rival.
- **Patty:** Hazel says a kitchen is a team. Even the one next door.

**Completion** (NEW RESTAURANT bought) — *First Lesson*

- **Fenna:** Teach me to grill, Patty. A real burger, by hand.
- **Patty:** Lesson one: you can't rush a patty.
- **Hazel:** Next page is space. Don't ask me how I drew that.

### 10. Space Station Diner

**Arrival** — *Low Gravity*

- **Patty:** Low gravity! I can carry two extra burgers!
- **Hazel:** Can you hear me up there, kid? Strap down the ketchup.

**Mid-build:** first hire — *400 Readers*

- **Dot:** I took a rocket! My newsletter has 400 readers now!
- **Patty:** Dot, you're famous!
- **Dot:** WE are famous. The usual, please, in zero-g.

**Mid-build:** first rush hour — *Hand-Flipped*

- **Fenna:** Patty, look! I flipped a burger by hand, in space!
- **Patty:** It's perfect! Well… it's round-ish.

**Completion** (NEW RESTAURANT bought) — *Out of This World*

- **Clementine:** Out of this world. And I don't say that lightly.
- **Hazel:** Two pages left, and one is under the sea. Bring snorkels!

### 11. Underwater Dome

**Arrival** — *Under the Sea*

- **Patty:** Fish outside every window. They look hungry too!
- **Scraps:** Psst, I'm not stealing. Just… looking.

**Mid-build:** a raccoon thief appears — *Old Habits*

- **Scraps:** Sorry. Old habits.
- **Patty:** Scraps, why do you keep stealing?
- **Scraps:** Nobody ever just offered me a burger.
- **Patty:** Well, I'm offering. Come by after closing.

**Mid-build:** half the restaurant built — *Something Special*

- **Hazel:** One page left after this. The last one is special.
- **Patty:** Special how?
- **Hazel:** You'll see, kid. You'll see.

**Completion** (NEW RESTAURANT bought) — *The Last Page*

- **Hazel:** The last page is Candy Kingdom. I'll meet you there.
- **Patty:** You're coming? Really?
- **Hazel:** To your grand opening? Wouldn't miss it.

### 12. Candy Kingdom

**Arrival** — *Sweet Ending*

- **Patty:** Gumdrop trees and lollipop lamps! The last page!
- **Dot:** I brought every issue of my newsletter. Signed!

**Mid-build:** first hire — *A Job for Scraps*

- **Scraps:** I brought back everything I took. Some fries are… vintage.
- **Patty:** Keep them. How about a job instead?
- **Scraps:** Me? I could wash dishes.
- **Patty:** That's exactly how I started.

**Mid-build:** half the restaurant built — *Partners*

- **Fenna:** My crew wants to help. Real grills, no robots.
- **Patty:** Welcome to the team, partner!

## 6. Tutorial lines (Bun Wagon, Hazel's voice)
Shown in the HUD tip box with Hazel's portrait while the first restaurant has fewer than 10 builds
(`Story.tutorialTip`). Onboarding still has no skip button.

| Situation | Line |
|---|---|
| Nothing built, can afford the grill | Stand on the glowing pad. That's where your grill goes! |
| Nothing built, can't afford it | Grab some cash first, then build. |
| Carrying dirty trays | Dirty trays go in the bin. Every time. |
| Carrying food | Food goes on the counter. Hot and fast! |
| Guest waiting, food ready, nobody at the register | Hop behind the register. Your guest is waiting! |
| Cash piling up | Your cash is on the counter. Scoop it up! |
| Next build affordable | You can afford the next build. Go on, kid! |
| Dirty tables | Clean tables, happy guests. |
| Food waiting on a station | Food's ready. Grab it off the grill! |
| 6+ builds | Serve fast for a COMBO. Bigger tips! |
| 4+ builds | What you build stays forever. Keep going! |
| Otherwise | Patience. The grill is sizzling. |

## 7. Finale and the late game
### Finale: *Grand Opening* (Candy Kingdom fully built)

- **Hazel:** Patty. Look at this place.
- **Patty:** Twelve pages, Hazel. Every one stamped.
- **Hazel:** You did more than fill my book. You built a family.
- **Fenna:** And a franchise!
- **Dot:** And a newsletter empire!
- **Scraps:** And the dishes are done!
- **Hazel:** One last gift: a golden book. Every page is blank.
- **Hazel:** Now you write your own. Bun voyage, Chef Patty.

Shown over a dimmed screen with confetti, followed by a "VOYAGE COMPLETE" card.

### Post-game hook: *The Golden Tour*

- **Clementine:** Not so fast, chef. The Golden Guide is watching.
- **Clementine:** Every restaurant can earn three gold stars. Thirty-six in all.
- **Patty:** Then the voyage isn't over. Let's go for gold!

**Why the late game exists:** Hazel's book is full, but the golden book is blank. Every finished restaurant offers
three **gold stars**, each earned within one visit (serve N guests, a restaurant-specific challenge such as VIPs,
drive-thru cars, troubles or a flawless rush, and a one-visit revenue target). Each star pays 10 gems and adds 5 % to
that restaurant's idle income, forever. 36 stars in all; the last one pays 300 gems and plays the epilogue. Gold stars
open as soon as a restaurant is finished, so they also give a reason to revisit earlier restaurants mid-game.

### First revisit of a finished restaurant: *Gold Stars*

- **Hazel:** Back at an old page? Try for its three gold stars.
- **Hazel:** Each star pays gems and boosts this place's idle income.

### Epilogue: *Every Page Gold* (all 36 gold stars)

- **Clementine:** Thirty-six gold stars? That has never happened, ever.
- **Hazel:** Proud of you, kid. Every page is gold.
- **Patty:** Thanks, Hazel. Now… who's hungry?

### Prologue for older saves: *The Voyage Book* (Home, once)

- **Hazel:** Patty! Got a minute for your old boss?
- **Hazel:** Forty years I drove the Bun Wagon. Then I gave you the keys.
- **Hazel:** And my Voyage Book: twelve places I always meant to cook.
- **Patty:** And I promised to fill every page. One restaurant at a time!
- **Hazel:** That's the spirit. Send me a postcard from each one.

Shown only to players who had already left the Bun Wagon before the story existed.

## 8. Writing rules (enforced by `StoryTest`)
- One or two short sentences per bubble, at most 72 characters; at most 8 bubbles per beat.
- Every restaurant: exactly one arrival, one or two mid-build beats on different cues, and a completion beat (the last
  restaurant has the finale instead). Each cue used must be able to happen there (the expert bot must see it).
- Beat ids are saved: never rename or remove one. Add new beats with new ids.
- Characters keep their voice: Hazel never rambles, Fenna never says "please", Dot always wants "the usual".
- Originality: write new lines; never borrow names, catchphrases or plot beats from other games, films or brands.

## 9. The World Tour (round 5): the golden book

After Candy Kingdom's grand opening, Hazel's blank golden book becomes the **World Tour**:
- 25 regions × 20 restaurants, played after the classic twelve (`docs/WORLD_TOUR_DESIGN.md`);
- each region has a **host** from the cast who carries its scenes;
- three beats per region:
  - an **arrival** cut-scene at its first restaurant;
  - a **halfway** card at its 10th;
  - a **farewell** cut-scene when its flagship's NEXT REGION pad is bought.

The last region ends with the tour finale instead. The lines live in `game/TourStory.kt`, and this section is generated from it.

### Opening: *The World Tour* (first tour restaurant, cut-scene)

- **Hazel:** That golden book won't fill itself, kid.
- **Fenna:** I drew you a map. Twenty-five regions, all hungry.
- **Dot:** I already bought the tickets. Window seats!
- **Scraps:** I'll carry the bags. I won't open them, promise.
- **Patty:** Then let's go. The World Tour starts now!

### 1. Maple Harbor (host: Dot)

**Arrival**

- **Dot:** Maple Harbor, where my grandpa fished! Smell that salt?
- **Patty:** I smell fries. Same thing, right?
- **Dot:** Close enough. The fog rolls in at noon, so cook fast!

**Halfway** (stop 10)

- **Dot:** Half the harbor eats here now. The gulls want in too.
- **Patty:** No gulls behind the counter!

**Farewell** (NEXT REGION bought)

- **Dot:** The harbor is yours, chef. Grandpa would be proud.
- **Hazel:** Postcard received! Next stop, Blossom Valley.
- **Patty:** Blossoms, here we come!

### 2. Blossom Valley (host: Hazel)

**Arrival**

- **Hazel:** I came here every spring with the wagon. Look at it now.
- **Patty:** Pink trees everywhere! Even the buns look happy.
- **Hazel:** Petals land in the fryer if you're slow. Stay quick.

**Halfway** (stop 10)

- **Hazel:** Half a valley of grills. The bees are jealous.
- **Patty:** We'll save them a bun.

**Farewell** (NEXT REGION bought)

- **Hazel:** You turned my favourite valley into a feast, Patty.
- **Fenna:** Next region is mine: the savanna. Bring sunglasses.
- **Patty:** Sunglasses packed!

### 3. Sunbaked Savanna (host: Fenna)

**Arrival**

- **Fenna:** My old kiosk route. The sun here almost cooks for you.
- **Patty:** Then why did your robots melt?
- **Fenna:** We don't talk about the robots. Open that drive-thru!

**Halfway** (stop 10)

- **Fenna:** Ten stops in. The jeeps line up for your window.
- **Patty:** Keep that window stocked, partner!

**Farewell** (NEXT REGION bought)

- **Fenna:** Okay. The savanna loves you more than it loved me.
- **Hazel:** Hot postcard! Next is Polar Outpost, so pack big mittens.
- **Patty:** From sunburn to snowballs!

### 4. Polar Outpost (host: Scraps)

**Arrival**

- **Scraps:** I stowed away on a supply plane once. Long story.
- **Patty:** Scraps, it's freezing! Who eats burgers here?
- **Scraps:** Scientists eat EVERYTHING. Keep the grills hot.

**Halfway** (stop 10)

- **Scraps:** The aurora is out! Even the penguins stopped to look.
- **Patty:** Penguins want shakes. Go, go!

**Farewell** (NEXT REGION bought)

- **Scraps:** The station chief gave me a medal. For dishes!
- **Hazel:** Proud of you, Scraps. Next page: a river in the rain.
- **Patty:** Riverboat Delta, bring boots!

### 5. Riverboat Delta (host: Dot)

**Arrival**

- **Dot:** We came by riverboat! I only fell in twice.
- **Patty:** Rain on the roof, steam on the grill. Cozy!
- **Dot:** River folk love veggie burgers. Lead with those!

**Halfway** (stop 10)

- **Dot:** My newsletter just hit ten thousand readers!
- **Patty:** Ten thousand? Dot, that's a whole town!

**Farewell** (NEXT REGION bought)

- **Dot:** The whole river waved goodbye. With napkins!
- **Clementine:** Next is Castle Hill. I review there, personally.
- **Patty:** No pressure, then.

### 6. Castle Hill (host: Clementine)

**Arrival**

- **Clementine:** Castle Hill. The market here is older than the castle.
- **Patty:** Banners, towers and cobbles. Fancy!
- **Clementine:** Fancy eaters too. Every order must be perfect.

**Halfway** (stop 10)

- **Clementine:** Halfway, and not one cold fry. I am… impressed.
- **Patty:** Did Clementine just smile?

**Farewell** (NEXT REGION bought)

- **Clementine:** Five stars, castle-wide. Don't let it go to your toque.
- **Fenna:** Next stop, Frontier Gulch. Dust and cactus, my kind of place.
- **Patty:** Saddle up!

### 7. Frontier Gulch (host: Fenna)

**Arrival**

- **Fenna:** In the gulch, the fastest window wins. Ready?
- **Patty:** Faster than nine seconds?
- **Fenna:** Ha! Faster than my robots, anyway.

**Halfway** (stop 10)

- **Fenna:** Ten saloons sold out. The horses want fries.
- **Patty:** Horses can't have fries, Fenna.

**Farewell** (NEXT REGION bought)

- **Fenna:** The sheriff made you a deputy. Of lunch.
- **Hazel:** Next is Coconut Cove. Sun hats on, kids.
- **Patty:** Hammocks, here I come!

### 8. Coconut Cove (host: Dot)

**Arrival**

- **Dot:** Coconut Cove! I wrote my first review on this beach.
- **Patty:** Turquoise water and a grill in the sand. Perfect.
- **Dot:** Island time is slow, but the guests aren't. Hop to it!

**Halfway** (stop 10)

- **Dot:** Someone found a treasure chest. It's full of ketchup!
- **Patty:** Best treasure ever.

**Farewell** (NEXT REGION bought)

- **Dot:** The cove threw us a goodbye bonfire!
- **Hazel:** Next, my old friends in Alpine Meadow. Say hi to the cows.
- **Patty:** Moo-ving on!

### 9. Alpine Meadow (host: Hazel)

**Arrival**

- **Hazel:** Fresh milk, fresh cheese, fresh air. Cook like it matters.
- **Patty:** Cheeseburgers in the mountains. Dream come true!
- **Hazel:** Mind the cowbells. They mean a crowd is coming.

**Halfway** (stop 10)

- **Hazel:** Half the valley's cheese goes on your burgers now.
- **Patty:** And the other half on my nose.

**Farewell** (NEXT REGION bought)

- **Hazel:** The meadow will miss you. So will the goats.
- **Scraps:** Next is Pixel Arcade. I know all the high scores.
- **Patty:** Of course you do.

### 10. Pixel Arcade (host: Scraps)

**Arrival**

- **Scraps:** Welcome to Pixel Arcade! Every burger is a bonus round.
- **Patty:** Neon, beeps and burgers. Let's play!
- **Scraps:** Combos score big here. Don't break the chain!

**Halfway** (stop 10)

- **Scraps:** New high score, chef. Ten arcades fed!
- **Patty:** Can I put my initials in?

**Farewell** (NEXT REGION bought)

- **Scraps:** Game over? Nope, next level: Moonlit Hollow.
- **Dot:** Is it spooky? I'm not scared… a little scared.
- **Patty:** I'll bring a night light!

### 11. Moonlit Hollow (host: Scraps)

**Arrival**

- **Scraps:** Moonlit Hollow! Raccoons love it, and so do friendly ghosts.
- **Patty:** Pumpkins and lanterns everywhere. It's kind of cozy.
- **Scraps:** Night owls tip well. Keep the combo glowing.

**Halfway** (stop 10)

- **Scraps:** An owl ordered twelve fries. I think it's a party.
- **Patty:** Twelve fries for one owl, coming up!

**Farewell** (NEXT REGION bought)

- **Scraps:** The hollow says thanks. In whispers.
- **Dot:** Next is Funfair Pier! Rides, games, cotton candy!
- **Patty:** I call the ferris wheel!

### 12. Funfair Pier (host: Dot)

**Arrival**

- **Dot:** Funfair Pier! I've been on every ride, twice.
- **Patty:** Burgers by a carousel? We can do that.
- **Dot:** VIP ticket holders tip big. Watch for the sparkles!

**Halfway** (stop 10)

- **Dot:** Ten stands open! The ticket booth ran out of tickets.
- **Patty:** Then everyone gets a burger instead!

**Farewell** (NEXT REGION bought)

- **Dot:** Best funfair ever. I won you a giant bun plush.
- **Clementine:** Next, Sandstone Ruins. History, sand and a very old oven.
- **Patty:** Ancient burgers?

### 13. Sandstone Ruins (host: Clementine)

**Arrival**

- **Clementine:** These ruins had a bakery once. Three thousand years ago.
- **Patty:** Then we're reopening it. With fries.
- **Clementine:** Explorers are picky eaters. Keep every order exact.

**Halfway** (stop 10)

- **Clementine:** I found a carving of a burger. Very old taste.
- **Patty:** History repeats itself!

**Farewell** (NEXT REGION bought)

- **Clementine:** You fed an ancient city. My notes say 'acceptable'.
- **Fenna:** Next is Skyreach Isles, the floating islands. Hold your hat!
- **Patty:** And my patties!

### 14. Skyreach Isles (host: Fenna)

**Arrival**

- **Fenna:** Up here the wind does the delivery. Nice, right?
- **Patty:** Floating islands! Don't look down, don't look down.
- **Fenna:** Air's light on the high isles. Stack higher!

**Halfway** (stop 10)

- **Fenna:** Ten islands fed. The windmills spin for your grills now.
- **Patty:** Wind-powered burgers. The future!

**Farewell** (NEXT REGION bought)

- **Fenna:** We're holding a balloon parade. In your honour.
- **Scraps:** Next stop: Crystal Caverns. Shiny things, just for looking!
- **Patty:** Only looking, Scraps.

### 15. Crystal Caverns (host: Scraps)

**Arrival**

- **Scraps:** Crystal Caverns! I promise I'm only looking.
- **Patty:** It glows down here! The grill looks like a disco.
- **Scraps:** Miners are hungry. Cook fast, they work hard.

**Halfway** (stop 10)

- **Scraps:** Found a crystal shaped like a fry. Can I keep it?
- **Patty:** Ask the miners first, Scraps.

**Farewell** (NEXT REGION bought)

- **Scraps:** The miners gave me the crystal. Legally!
- **Hazel:** Next is Orchard Farm, my cousin's place. Be nice.
- **Patty:** Always nice!

### 16. Orchard Farm (host: Hazel)

**Arrival**

- **Hazel:** My cousin grows the best apples in the land.
- **Patty:** Barns, hay and a tractor drive-thru?
- **Hazel:** Farmers start early. Patience, and big portions.

**Halfway** (stop 10)

- **Hazel:** Ten farms fed. My cousin wants the recipe.
- **Patty:** The secret is love. And pickles.

**Farewell** (NEXT REGION bought)

- **Hazel:** The farm sent you a crate of apples. And a goat.
- **Fenna:** Next is Lunar Colony. Real space this time, partner.
- **Patty:** I'll strap down the ketchup.

### 17. Lunar Colony (host: Fenna)

**Arrival**

- **Fenna:** Lunar Colony. Moon dust, domes and very hungry pilots.
- **Patty:** Low gravity! My stack is floating again!
- **Fenna:** Carry more, walk less. Physics is on your side.

**Halfway** (stop 10)

- **Fenna:** Ten domes running. Earth is watching through telescopes.
- **Patty:** Wave at Earth, everybody!

**Farewell** (NEXT REGION bought)

- **Fenna:** Moon done. Never thought I'd say that.
- **Dot:** Next is Lantern Lagoon. A floating night market!
- **Patty:** Lanterns on the water. Beautiful.

### 18. Lantern Lagoon (host: Dot)

**Arrival**

- **Dot:** Lantern Lagoon! The market floats, so mind your balance.
- **Patty:** Boats, lanterns and burgers. Magic!
- **Dot:** VIPs paddle in at dusk. Serve them first!

**Halfway** (stop 10)

- **Dot:** Ten rafts of hungry guests! My readers want photos.
- **Patty:** Make sure you get my good side.

**Farewell** (NEXT REGION bought)

- **Dot:** The lagoon lit a lantern with your name on it.
- **Hazel:** Next is Amber Woods. Autumn, my favourite season.
- **Patty:** Crunchy leaves, here I come!

### 19. Amber Woods (host: Hazel)

**Arrival**

- **Hazel:** I wrote my first recipe under these very trees.
- **Patty:** Golden leaves everywhere. It smells like toast!
- **Hazel:** Hikers come in cold. Warm them up, fast.

**Halfway** (stop 10)

- **Hazel:** Ten cabins of happy hikers. The squirrels are jealous.
- **Patty:** Squirrels get the crumbs!

**Farewell** (NEXT REGION bought)

- **Hazel:** The woods will remember your grill, kid.
- **Fenna:** Next is Gearworks City. Machines, my people!
- **Patty:** No robots in my kitchen, Fenna.

### 20. Gearworks City (host: Fenna)

**Arrival**

- **Fenna:** Gearworks City. Everything ticks, whistles or steams.
- **Patty:** Can the machines flip burgers?
- **Fenna:** No, I checked. Your helpers run faster here, though.

**Halfway** (stop 10)

- **Fenna:** Ten workshops fed. The clocktower chimes for lunch now.
- **Patty:** Lunchtime, every hour!

**Farewell** (NEXT REGION bought)

- **Fenna:** The city made you a brass spatula. It's heavy.
- **Scraps:** Next is Mushroom Glade. Glowing caps, not for eating.
- **Patty:** Noted, Scraps.

### 21. Mushroom Glade (host: Scraps)

**Arrival**

- **Scraps:** Mushroom Glade! My cousins live here, so be polite.
- **Patty:** Glowing mushrooms and fireflies. It's like a dream.
- **Scraps:** Pets love it here. Bring yours, they help even more.

**Halfway** (stop 10)

- **Scraps:** My cousins finally said hello. With a fry in each paw.
- **Patty:** Family dinner!

**Farewell** (NEXT REGION bought)

- **Scraps:** The glade threw a firefly parade for us.
- **Clementine:** Next is Bamboo Springs. Hot springs and quiet, finally.
- **Patty:** Relaxing. And cooking.

### 22. Bamboo Springs (host: Clementine)

**Arrival**

- **Clementine:** Bamboo Springs. I come here to rest my taste buds.
- **Patty:** Steam, stone lanterns and bamboo. So calm.
- **Clementine:** Guests here are patient. Don't test it.

**Halfway** (stop 10)

- **Clementine:** Ten springs. My taste buds feel rested and happy.
- **Patty:** Was that a compliment?

**Farewell** (NEXT REGION bought)

- **Clementine:** Serene, swift and tasty. A rare combination.
- **Hazel:** Next is Frostfest Village. Festival time, kids!
- **Patty:** Snow and celebrations!

### 23. Frostfest Village (host: Hazel)

**Arrival**

- **Hazel:** Frostfest! The whole village decorates for a week.
- **Patty:** Lights, gifts and snowflakes. Best season ever!
- **Hazel:** Festival crowds love combos. Keep them coming.

**Halfway** (stop 10)

- **Hazel:** Ten stalls glowing. Someone built a snow-Patty!
- **Patty:** It has my ears!

**Farewell** (NEXT REGION bought)

- **Hazel:** The village gave you the festival key. Keep it safe.
- **Dot:** Next is Fossil Valley. Big friendly dinosaurs!
- **Patty:** Big appetites, too.

### 24. Fossil Valley (host: Dot)

**Arrival**

- **Dot:** Fossil Valley! Old bones, hungry guests.
- **Patty:** Ferns, footprints and a volcano. Wild!
- **Dot:** Grills cook fast on the warm ground. Use it!

**Halfway** (stop 10)

- **Dot:** Ten dig sites fed! The scientists want seconds.
- **Patty:** Seconds are on the house.

**Farewell** (NEXT REGION bought)

- **Dot:** One last region, chef. The Golden Capital!
- **Hazel:** I'll be there. We all will.
- **Patty:** The final page. Let's make it gold.

### 25. Golden Capital (host: Hazel)

**Arrival**

- **Patty:** The Golden Capital. Every street shines!
- **Fenna:** I booked the best corner. Fast Lane is now Patty's Partner.
- **Clementine:** I'll be reviewing every single burger.
- **Scraps:** I polished the statues. Only polished!

**Halfway** (stop 10)

- **Dot:** Ten golden kitchens! My newsletter has a million readers!
- **Patty:** A million? Dot!

*No farewell: the tour finale plays instead.*

### Tour finale: *The Last Postcard* (the last restaurant fully built)

- **Hazel:** Patty. Five hundred and twelve restaurants.
- **Patty:** And one little wagon that started it all.
- **Fenna:** Best partner I ever had.
- **Dot:** Best burgers I ever ate.
- **Scraps:** Best dishes I ever washed!
- **Clementine:** Best… fine, the best. Don't tell anyone.
- **Hazel:** The map has more edges than you think, kid.
- **Patty:** Then we keep cooking. Bun voyage!

The finale leaves a hook for future regions ("The map has more edges than you think"); new regions are appended after Golden Capital.
