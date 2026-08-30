package com.thezone.demo

/**
 * Set when this device has personally heard the devices in a cell that then went
 * dark — i.e. a confirmed collapse is inside its radio horizon. Every remaining
 * node near the damage leans in: pin to Coded PHY for reach, and don't let the
 * heartbeat interval space out past [ALERT_INTERVAL_FLOOR_S] even on a low
 * battery. The device "works according to distance from the damage" — inferred,
 * not assumed.
 */
object NetworkAlert {

    const val ALERT_INTERVAL_FLOOR_S = 10

    @Volatile
    var nearDamage: Boolean = false
}
