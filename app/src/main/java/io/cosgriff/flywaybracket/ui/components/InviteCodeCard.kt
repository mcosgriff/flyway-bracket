package io.cosgriff.flywaybracket.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/**
 * A league's invite code with Share invite (#40): on the League tab while the league forms, and once a league
 * is created.
 */
@Composable
fun InviteCodeCard(code: String, onShare: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FlywayTheme.colors
    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            // Read as one phrase: "Invite code, TESTWREN".
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.invite_code_label), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                Text(code, style = FlywayTheme.text.score)
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
                border = BorderStroke(FlywayDimens.border, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            ) {
                Text(stringResource(R.string.invite_share), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Opens Android's share sheet with the invite as plain text: the league's [name] and its [code]. */
fun shareInvite(context: Context, name: String, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.invite_share_text, name, code))
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.invite_share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
