package io.cosgriff.flywaybracket.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens

/**
 * Stands in for a screen the design canvas has and the app doesn't yet. A top-level place shows the
 * bottom bar ([place] and [onNavigate]); a pushed screen shows a back button ([onBack]).
 */
@Composable
fun NotBuiltYetContent(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    place: TopLevel? = null,
    onNavigate: (TopLevel) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (place != null) FlywayBottomBar(selected = place, onNavigate = onNavigate) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
                    }
                }
                Text(
                    text = stringResource(title).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Text(
                text = stringResource(R.string.not_built_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
