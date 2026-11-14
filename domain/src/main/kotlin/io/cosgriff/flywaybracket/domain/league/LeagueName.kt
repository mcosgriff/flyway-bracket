package io.cosgriff.flywaybracket.domain.league

/** The rule for a league's name, chosen by the player who creates it (#32). */
object LeagueName {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 40

    /**
     * Judges a name as typed. Spaces at either end don't count, since the name is stored trimmed. Length
     * is in characters as a person counts them (code points), so a letter outside the basic plane is one.
     */
    fun check(typed: String): LeagueNameCheck {
        val name = typed.trim()
        val length = name.codePointCount(0, name.length)
        return when {
            length == 0 -> LeagueNameCheck.EMPTY
            length < MIN_LENGTH -> LeagueNameCheck.TOO_SHORT
            length > MAX_LENGTH -> LeagueNameCheck.TOO_LONG
            else -> LeagueNameCheck.VALID
        }
    }
}

/** What [LeagueName.check] found. Only [VALID] may be saved. */
enum class LeagueNameCheck { EMPTY, TOO_SHORT, TOO_LONG, VALID }
