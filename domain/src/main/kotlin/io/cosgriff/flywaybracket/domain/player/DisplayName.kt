package io.cosgriff.flywaybracket.domain.player

/** The rule for the name a player's league sees. */
object DisplayName {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 24

    /**
     * Judges a name as typed. Spaces at either end don't count, since the name is stored trimmed. Length
     * is in characters as a person counts them (code points), so a letter outside the basic plane is one.
     */
    fun check(typed: String): DisplayNameCheck {
        val name = typed.trim()
        val length = name.codePointCount(0, name.length)
        return when {
            length == 0 -> DisplayNameCheck.EMPTY
            length < MIN_LENGTH -> DisplayNameCheck.TOO_SHORT
            length > MAX_LENGTH -> DisplayNameCheck.TOO_LONG
            else -> DisplayNameCheck.VALID
        }
    }
}

/** What [DisplayName.check] found. Only [VALID] may be saved. */
enum class DisplayNameCheck { EMPTY, TOO_SHORT, TOO_LONG, VALID }
