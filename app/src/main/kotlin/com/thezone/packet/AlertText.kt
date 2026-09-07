package com.thezone.packet

/**
 * The predefined phrase table for ALERT packets (PACKET_SPEC reserved-byte
 * "text-code index"). A phrase is one byte on the wire; the words live here so
 * every phone renders the same instruction without carrying the text.
 *
 * Pure Kotlin. Append only — never renumber an existing entry.
 */
object AlertText {

    /** code -> (short label for the banner, full instruction for the full-screen alert). */
    val phrases: List<Pair<String, String>> = listOf(
        "ALERT" to "Emergency alert.",                                                    // 0
        "EVACUATE NOW" to "Evacuate this area immediately. Move away on foot.",            // 1
        "MOVE TO HIGH GROUND" to "Flood risk. Move to higher ground now.",                // 2
        "SHELTER IN PLACE" to "Stay indoors, away from windows. Do not go outside.",      // 3
        "AFTERSHOCK EXPECTED" to "Aftershock likely. Stay out of damaged buildings.",     // 4
        "FLASH FLOOD" to "Flash flood warning. Leave low-lying areas at once.",           // 5
        "LANDSLIDE RISK" to "Landslide risk. Move off the slope immediately.",            // 6
        "FIRE — EVACUATE" to "Fire spreading. Evacuate the area now.",                    // 7
        "GAS LEAK" to "Gas leak. No flames, no switches. Leave the building.",            // 8
        "RESCUE INBOUND" to "Rescue teams are on the way. Stay visible and together.",    // 9
        "AID POINT OPEN" to "Water and rations available at this location.",              // 10
        "ROUTE BLOCKED" to "This route is blocked. Use an alternate way out.",            // 11
        "ALL CLEAR" to "The hazard has passed. It is safe to return.",                    // 12
    )

    /** code -> category name. 0..4 = INFO..EXTREME. */
    val categories: List<String> = listOf("INFO", "ADVISORY", "WATCH", "WARNING", "EXTREME")

    fun label(code: Int): String = phrases.getOrElse(code) { phrases[0] }.first
    fun full(code: Int): String = phrases.getOrElse(code) { phrases[0] }.second
    fun categoryName(c: Int): String = categories.getOrElse(c) { "ALERT" }
}
