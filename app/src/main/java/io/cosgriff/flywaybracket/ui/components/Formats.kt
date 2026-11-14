package io.cosgriff.flywaybracket.ui.components

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.cosgriff.flywaybracket.R
import java.time.Duration

/** How long an outing lasted, in the phone's language: "1 h 12 min", "48 min". */
@Composable
fun lengthText(length: Duration): String {
    val hours = length.toHours().toInt()
    val minutes = (length.toMinutes() % MINUTES_PER_HOUR).toInt()
    return if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes) else stringResource(R.string.duration_minutes, minutes)
}

/** A running clock, in the phone's digits: "42:18", "1:42:18". */
fun clockText(elapsed: Duration): String = DateUtils.formatElapsedTime(elapsed.seconds.coerceAtLeast(0))

private const val MINUTES_PER_HOUR = 60
