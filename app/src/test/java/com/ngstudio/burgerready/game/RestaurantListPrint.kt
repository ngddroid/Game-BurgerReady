package com.ngstudio.burgerready.game

import org.junit.Test

/**
 * Manual: prints the Markdown body of docs/RESTAURANTS.md from the game data (PRINT_RESTAURANTS=true), so the
 * catalogue never drifts from the code. Regenerate it after adding regions.
 */
class RestaurantListPrint {
    @Test
    fun print() {
        if (System.getenv("PRINT_RESTAURANTS") != "true") return
        val sb = StringBuilder()
        sb.append("## Chapter 1: The Classic Voyage (12)\n\n| # | Restaurant | Layout | Pads | Perk |\n|---|---|---|---|---|\n")
        for (i in 0 until Restaurants.CLASSIC) {
            val c = Restaurants.config(i); val s = Restaurants.storeAt(i)
            sb.append("| ${i + 1} | ${s.name} | ${Layouts.get(c.layoutId).name} | ${c.pads.size} | ${s.perk} |\n")
        }
        for (r in Tour.REGIONS.indices) {
            val reg = Tour.REGIONS[r]
            sb.append("\n## Region ${r + 1}: ${reg.name} (20)\n\n")
            sb.append("_${reg.blurb}._ Host: ${reg.host.displayName} · perk family: ${reg.perk.name.lowercase()} · menu lead: ${reg.menuLead.joinToString { it.label }} · weather: ${reg.weather.name.lowercase()}\n\n")
            sb.append("| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |\n|---|---|---|---|---|---|---|---|\n")
            for (k in 0 until Tour.PER_REGION) {
                val g = Tour.globalIndex(r, k)
                val c = Restaurants.config(g); val s = Restaurants.storeAt(g)
                val time = if (s.night >= 0.6f) "night" else if (s.night >= 0.3f) "dusk" else "day"
                sb.append("| ${g + 1} | ${k + 1} | ${s.name} | ${Tour.size(k).label} | ${Layouts.get(c.layoutId).name} | ${c.pads.size} | ${s.perk} | $time |\n")
            }
        }
        println("RESTAURANTS_MD_BEGIN\n$sb\nRESTAURANTS_MD_END")
    }
}
