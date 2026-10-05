package com.thezone.packet

/**
 * The optional phrase a Citizen can attach to their own report
 * ([PacketCodec.withStatusPhrase] / [PacketCodec.statusPhraseCode]) — one extra
 * detail beyond the status/severity/casualty-count fields already on every
 * packet. Same idea as [AlertText]'s phrase table, deliberately not free text:
 * a code is 1 byte on the wire and costs nothing extra to carry or relay,
 * where free text would need its own linked packets and real extra airtime.
 *
 * Code 0 is reserved — it means "no phrase chosen", matching every packet
 * encoded before this field existed. Pure Kotlin. Append only — never
 * renumber or reuse an existing code once it has shipped.
 */
object StatusPhrases {

    /** code 1.. -> short label, shown next to the report wherever it's listed. */
    val phrases: List<String> = listOf(
        "Child with me",          // 1
        "Elderly with me",        // 2
        "Multiple people here",   // 3
        "Someone injured",        // 4
        "Cannot walk",            // 5
        "Have water / food",      // 6
        "Need water",             // 7
        "Need medicine",          // 8
        "Pet with me",            // 9
        "Heard others nearby",    // 10
    )

    /** null for 0 ("no phrase chosen") or anything unrecognised — never throws. */
    fun label(code: Int?): String? = code?.let { phrases.getOrNull(it - 1) }
}
