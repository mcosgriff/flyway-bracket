package io.cosgriff.flywaybracket.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens

/** The top of a screen pushed over another: a back button alone, the title being in the page. */
@Composable
fun BackBar(onBack: () -> Unit) {
    Row(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(FlywayDimens.space2)) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
        }
    }
}

/** A section as the Packs board draws one: its title in small capitals, then its rows. */
@Composable
fun ListSection(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        Text(
            text = stringResource(title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/**
 * A row as the Packs board draws one: a title, a caption under it, and [trailing] at the end. With
 * [onClick] the whole row is one button, and it shows the forward arrow unless [trailing] says otherwise.
 */
@Composable
fun ListRow(
    title: String,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.rowHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(
                    painter = painterResource(R.drawable.ic_forward),
                    contentDescription = null,
                    modifier = Modifier.size(FlywayDimens.space5),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val color = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    if (onClick != null) {
        Surface(onClick = onClick, color = color, contentColor = ink, shape = MaterialTheme.shapes.large, content = body)
    } else {
        Surface(color = color, contentColor = ink, shape = MaterialTheme.shapes.large, content = body)
    }
}
