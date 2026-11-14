package io.cosgriff.flywaybracket.ui.consent

import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.data.server.ServerProblem

/**
 * What the consent page shows.
 *
 * @property creating the player said Continue and the account is being asked for.
 * @property problem why the last try failed, or null.
 */
@Immutable
data class ConsentState(val creating: Boolean = false, val problem: ServerProblem? = null)
