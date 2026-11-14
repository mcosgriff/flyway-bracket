package io.cosgriff.flywaybracket.data.server

import io.cosgriff.flywaybracket.api.ApiErrorCode

/** What a call to the league server gave: its answer, or why there is none. */
sealed interface ServerResult<out T> {
    data class Ok<out T>(val value: T) : ServerResult<T>

    data class Failed(val problem: ServerProblem) : ServerResult<Nothing>
}

/**
 * Why a call to the server failed, as the app tells the player. Typed, never wording: the screen turns it
 * into words from `strings.xml`, and nothing the server writes reaches the screen.
 */
sealed interface ServerProblem {
    /** The phone could not reach the network: no connection, so the server's name could not be looked up. */
    data object NoNetwork : ServerProblem

    /** The network was there and the server did not answer: it timed out, refused, or broke off. */
    data object NoAnswer : ServerProblem

    /**
     * The server answered and refused the request.
     *
     * @property code why, or null when the answer named a code this build does not know or was not an
     *   `ApiError` at all: a general failure.
     */
    data class Refused(val code: ApiErrorCode?) : ServerProblem
}
