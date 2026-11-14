package io.cosgriff.flywaybracket.server.league

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand. Each zone's view of it shows the same moment. */
class HandClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = View(zone)

    private inner class View(private val zone: ZoneId) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = View(zone)
    }
}
