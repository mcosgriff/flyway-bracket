package io.cosgriff.flywaybracket.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.cosgriff.flywaybracket.R

/**
 * Under a screen's waiting words while the server is most likely waking (#93): it sleeps when no one has used
 * it for some minutes, and its first answer then takes a few seconds. Read out when it appears. The screens
 * that wait on the server show it while `ServerActivity.waking` is true.
 */
@Composable
fun ServerWaking(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = stringResource(R.string.server_waking),
        style = style,
        color = color,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
