package io.cosgriff.flywaybracket.ui.components

import androidx.annotation.StringRes
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.Round

/**
 * The name a screen shows for a round: `stringResource(round.nameRes)`. The one place a round gets its
 * name, so no screen spells one itself (#19).
 */
@get:StringRes
val Round.nameRes: Int
    get() = when (this) {
        Round.ROUND_OF_64 -> R.string.round_of_64
        Round.ROUND_OF_32 -> R.string.round_of_32
        Round.ROUND_OF_16 -> R.string.round_of_16
        Round.QUARTERFINAL -> R.string.round_quarterfinals
        Round.SEMIFINAL -> R.string.round_semifinals
        Round.FINAL -> R.string.round_final
    }
