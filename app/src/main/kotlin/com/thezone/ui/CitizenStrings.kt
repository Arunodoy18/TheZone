package com.thezone.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.thezone.config.LangStore
import com.thezone.probe.R
import java.util.Locale

/** Citizen-screen copy, resolved against the user's chosen language ([LangStore]). */
class CitizenStrings(
    val heard: String,
    val reaching: String,
    val carryingOne: String,
    val carryingMany: String,
    val trapped: String,
    val waterRising: String,
    val safe: String,
    val peopleNone: String,
    val peopleMax: String,
    private val peopleNFmt: String,
) {
    fun peopleN(n: Int): String = String.format(peopleNFmt, n)
}

@Composable
fun citizenStrings(): CitizenStrings {
    val context = LocalContext.current
    val tag = LangStore.tag(context)
    val res = remember(tag) {
        if (tag == null) context.resources
        else {
            val cfg = Configuration(context.resources.configuration)
            cfg.setLocale(Locale.forLanguageTag(tag))
            context.createConfigurationContext(cfg).resources
        }
    }
    return remember(res) {
        CitizenStrings(
            heard = res.getString(R.string.citizen_heard),
            reaching = res.getString(R.string.citizen_reaching),
            carryingOne = res.getString(R.string.citizen_carrying_one),
            carryingMany = res.getString(R.string.citizen_carrying_many),
            trapped = res.getString(R.string.citizen_trapped),
            waterRising = res.getString(R.string.citizen_water_rising),
            safe = res.getString(R.string.citizen_safe),
            peopleNone = res.getString(R.string.citizen_people_none),
            peopleMax = res.getString(R.string.citizen_people_max),
            peopleNFmt = res.getString(R.string.citizen_people_n),
        )
    }
}
