package io.cosgriff.flywaybracket.server.league

import java.security.SecureRandom

/**
 * What a player types to join a league: 8 characters from an alphabet with no look-alikes (no 0, O, 1, I
 * or L), so a code read aloud or copied by hand comes out right. 31 characters to the power of 8 is about
 * 850 billion codes.
 */
object InviteCode {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun random(random: SecureRandom): String = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /**
     * The code as typed, without spaces at either end and in capitals; null when it cannot be a code, so
     * that it is not looked up.
     */
    fun normalize(typed: String): String? =
        typed.trim().uppercase().takeIf { code -> code.length == LENGTH && code.all { it in ALPHABET } }
}
