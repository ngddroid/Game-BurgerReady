package com.ngstudio.burgerready.data

import com.ngstudio.burgerready.game.RestaurantProgress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The save file keeps the round-3 fields (story beats seen, gold stars) and older saves still load. */
class SaveDataJsonTest {

    @Test
    fun storyAndGoldRoundTrip() {
        val d = SaveData(
            bucks = 1234, gems = 56, stores = mapOf("wagon" to 2, "seaside" to 0), selectedStore = "seaside",
            restaurants = mapOf("wagon" to RestaurantProgress(setOf(0, 1, 2), mapOf(3 to 7))),
            story = setOf("prologue", "s1_arrive"), gold = mapOf("wagon" to 5),
        )
        val back = GameRepo.fromJson(JSONObject(GameRepo.toJson(d).toString()))
        assertEquals(d.story, back.story)
        assertEquals(d.gold, back.gold)
        assertEquals(d.restaurants, back.restaurants)
        assertEquals(d.bucks, back.bucks)
        assertEquals(2, back.goldTotal())
    }

    @Test
    fun saveFromBeforeRound3Loads() {
        // shape of the save on the test phone before round 3 (no "story" / "gold" keys)
        val old = JSONObject("""{"version":2,"onboardingDone":true,"bucks":422,"gems":25,
            "restaurants":{"seaside":{"done":[0],"paid":{}},"wagon":{"done":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14],"paid":{}}},
            "stores":{"seaside":0,"wagon":0},"selectedStore":"seaside","chefXp":68}""")
        val d = GameRepo.fromJson(old)
        assertTrue(d.story.isEmpty())
        assertTrue(d.gold.isEmpty())
        assertEquals(422L, d.bucks)
        assertEquals(15, d.progressOf("wagon").done.size)
        assertTrue("Bun Wagon is finished", d.isComplete("wagon"))
        assertTrue(!d.isComplete("seaside"))
    }

    /** v3: finished restaurants are stored as {"all":1}; partial ones keep pad ids and Long payments. */
    @Test
    fun worldTourSaveIsCompactAndExact() {
        val ids = (0 until 40).map { com.ngstudio.burgerready.game.Tour.id(it / 20, it % 20) }
        val all = ids.associateWith { RestaurantProgress(com.ngstudio.burgerready.game.Restaurants.config(it).pads.map { p -> p.id }.toSet()) }
        val partial = RestaurantProgress(setOf(0, 1, 2), mapOf(3 to 123_456_789_012L))
        val d = SaveData(stores = (ids + "polar_01" + "wagon").associateWith { 0 }, selectedStore = "polar_01",
            restaurants = all + ("polar_01" to partial) + ("wagon" to RestaurantProgress(setOf(0, 1))), bucks = 9_000_000_000_000L)
        val json = GameRepo.toJson(d)
        assertEquals(3, json.getInt("version"))
        val r = json.getJSONObject("restaurants")
        assertEquals(1, r.getJSONObject("harbor_01").getInt("all"))
        assertTrue(!r.getJSONObject("polar_01").has("all"))
        val back = GameRepo.fromJson(JSONObject(json.toString()))
        assertEquals(d.restaurants, back.restaurants)
        assertEquals(123_456_789_012L, back.progressOf("polar_01").paid[3])
        assertEquals(9_000_000_000_000L, back.bucks)
        assertTrue(back.isComplete("harbor_20") && back.isComplete("blossom_20"))
        assertTrue(json.toString().length < 6000)
        // unknown ids written by a future version are skipped, not mapped onto another restaurant
        val future = JSONObject(json.toString()); future.getJSONObject("restaurants").put("zzz_01", JSONObject().put("all", 1))
        assertTrue("zzz_01" !in GameRepo.fromJson(future).restaurants)
    }
}
