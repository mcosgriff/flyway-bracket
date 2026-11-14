package io.cosgriff.flywaybracket.domain.place

/**
 * The shape of a county's eBird region code: a country and a state, two capital letters each, then the
 * county's three-digit Census code (`US-CO-059`). The bundled county table and the server both hold a
 * code to it. A well-formed code need not name a county that exists.
 */
object CountyCode {
    private val pattern = Regex("[A-Z]{2}-[A-Z]{2}-[0-9]{3}")

    fun isWellFormed(code: String): Boolean = pattern.matches(code)
}
