package io.cosgriff.flywaybracket.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/**
 * What the league server is sent, what other players see, and how deleting works, in the words of
 * ADR 0001: one card each. The consent page (#38) and Settings (#44) show the same list, so the two never
 * say different things.
 */
@Composable
fun PrivacyList() {
    PrivacyCard(
        R.string.consent_sent_title,
        R.string.consent_sent_account,
        R.string.consent_sent_profile,
        R.string.consent_sent_leagues,
        R.string.consent_sent_sightings,
        R.string.consent_sent_packs,
        R.string.consent_never_sent,
    )
    PrivacyCard(R.string.consent_seen_title, R.string.consent_seen_body, R.string.consent_not_seen_body)
    PrivacyCard(R.string.consent_delete_title, R.string.consent_delete_body, R.string.consent_lost_body)
}

/** A card: its title, then a paragraph for each of [paragraphs]. Flat, with a border. */
@Composable
private fun PrivacyCard(@StringRes title: Int, @StringRes vararg paragraphs: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(FlywayDimens.border, FlywayTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Text(
                text = stringResource(title).toUpperCase(Locale.current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            paragraphs.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
