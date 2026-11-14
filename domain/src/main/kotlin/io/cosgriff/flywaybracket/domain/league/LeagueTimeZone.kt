package io.cosgriff.flywaybracket.domain.league

import java.time.ZoneId

/**
 * The time zone a league is created in (#32): an IANA zone id, such as `America/Denver`. Every date of the
 * league's season is a date in it. The server accepts a zone only through [of]; the app offers [ids] to
 * choose from (#40).
 */
object LeagueTimeZone {
    /** Every zone id this runtime knows, in alphabetical order. No offset such as `+02:00` is among them. */
    val ids: List<String> by lazy { ZoneId.getAvailableZoneIds().sorted() }

    private val known: Set<String> by lazy { ids.toHashSet() }

    /** The zone named [text] exactly; null for an offset, a zone this runtime does not know, or anything else. */
    fun of(text: String): ZoneId? = text.takeIf { it in known }?.let(ZoneId::of)
}
