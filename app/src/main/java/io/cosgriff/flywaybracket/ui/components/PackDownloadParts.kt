package io.cosgriff.flywaybracket.ui.components

import android.icu.text.NumberFormat
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.data.DownloadProgress
import io.cosgriff.flywaybracket.data.PackDownload
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/**
 * A pack download that waits for the player's yes: it is over 5 MB and the connection is metered (#39).
 *
 * @property state the state's name
 */
@Immutable
data class MeteredAsk(val stateCode: String, val state: String, val sizeBytes: Long)

/** "1.4 MB", in the phone's language. */
@Composable
fun fileSizeText(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/**
 * The words for why a download installed nothing, or null when it installed the pack or the phone had it
 * already. They come from the outcome's type, never from the server's text.
 */
@get:StringRes
val PackDownload.failureMessage: Int?
    get() = when (this) {
        is PackDownload.Installed, PackDownload.Unchanged -> null
        // The account went while the download ran.
        PackDownload.NoAccount -> R.string.server_problem_general
        is PackDownload.Failed -> problem.message
        is PackDownload.Refused ->
            if (reason == StatePackResult.Reason.TOO_NEW) R.string.download_problem_too_new else R.string.download_problem_damaged
        PackDownload.NotSaved -> R.string.download_problem_not_saved
    }

/**
 * A download under way: a bar, and words that say the same without it, "Downloading… 42%". One phrase to
 * a screen reader. The colours are the card's it sits on.
 */
@Composable
fun DownloadProgressLine(
    progress: DownloadProgress,
    color: Color,
    trackColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.fraction
    val words = if (fraction != null) {
        // The phone's own way of writing a percentage: "42%", "42 %", "%42".
        stringResource(R.string.download_in_progress_percent, NumberFormat.getPercentInstance().format(fraction))
    } else {
        stringResource(R.string.download_in_progress)
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
    ) {
        // The words above the bar: Material reaches the bar's touch bounds 10dp above it, which would
        // otherwise lie over a button just above and shrink its touch target.
        Text(words, style = MaterialTheme.typography.bodySmall, color = textColor)
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = color, trackColor = trackColor)
        }
    }
}

/** Why a download installed nothing, in [color], read out when it appears. */
@Composable
fun DownloadFailureText(@StringRes message: Int, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Asks before a large download on a metered connection, and says what it may cost. */
@Composable
fun MeteredDownloadDialog(ask: MeteredAsk, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.download_metered_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = stringResource(R.string.download_metered_body, ask.state, fileSizeText(ask.sizeBytes)),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = FlywayTheme.colors.pick)) {
                Text(stringResource(R.string.download_metered_confirm), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.download_metered_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
