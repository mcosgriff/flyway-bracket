package io.cosgriff.flywaybracket.domain.player

/**
 * What the player has told the app about themselves. Both are unset until the player enters them.
 *
 * @property displayName the name the player's league sees.
 * @property homeCountyCode the player's home county as an eBird region code (`US-CO-059`): where a bird must
 *   be expected for the player to be eligible for it.
 */
data class PlayerSettings(
    val displayName: String? = null,
    val homeCountyCode: String? = null,
)
