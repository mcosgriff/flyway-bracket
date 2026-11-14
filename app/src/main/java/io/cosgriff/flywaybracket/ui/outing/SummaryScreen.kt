package io.cosgriff.flywaybracket.ui.outing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.components.lengthText
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A finished outing (#26), and whether its sightings have reached the league server (#43). What it did for
 * the player's bracket is shown once there are leagues and live rounds (#41); until then that part of the
 * board is left out.
 */
@Composable
fun SummaryScreen(
    outingId: Long,
    onDone: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel<SummaryViewModel, SummaryViewModel.Factory>(
        creationCallback = { factory -> factory.create(outingId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Blank for the moment it takes to read the outing.
    state?.let { SummaryContent(it, onDone = onDone) }
}

@Composable
fun SummaryContent(state: SummaryState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { Footer(onDone) },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets),
            contentPadding = PaddingValues(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space6),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            item(key = "header") { Header(state) }
            item(key = "tiles") { Tiles(state) }
            item(key = "title") {
                Text(
                    text = stringResource(R.string.summary_birds_title).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = FlywayDimens.space3)
                        .semantics { heading() },
                )
            }
            if (state.sightings.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.summary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.sightings, key = { it.bird }) { BirdItem(it) }
        }
    }
}

@Composable
private fun Header(state: SummaryState) {
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1), modifier = Modifier.padding(bottom = FlywayDimens.space3)) {
        Text(
            text = stringResource(R.string.summary_overline).toUpperCase(Locale.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.place.ifEmpty { stringResource(R.string.summary_unnamed) }.toUpperCase(Locale.current),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                R.string.summary_when,
                state.date.format(DAY),
                state.start.format(TIME),
                state.end.format(TIME),
                lengthText(state.length),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.sent?.let { sent ->
            Text(
                text = stringResource(
                    when (sent) {
                        SummarySent.SENT -> R.string.summary_sent
                        SummarySent.WAITING -> R.string.summary_waiting
                        SummarySent.ON_PHONE -> R.string.summary_on_phone
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tiles(state: SummaryState) {
    val colors = FlywayTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
        Tile(
            figure = state.species,
            label = pluralStringResource(R.plurals.summary_species_label, state.species),
            container = colors.primary,
            content = colors.onPrimary,
            muted = colors.onPrimaryMuted,
            modifier = Modifier.weight(1f),
        )
        Tile(
            figure = state.birds,
            label = pluralStringResource(R.plurals.summary_birds_label, state.birds),
            container = colors.surface,
            content = colors.ink,
            muted = colors.inkMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Tile(figure: Int, label: String, container: Color, content: Color, muted: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = container, contentColor = content, shape = MaterialTheme.shapes.large) {
        // One phrase for a screen reader: "14, species".
        Column(
            modifier = Modifier
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(stringResource(R.string.figure, figure), style = FlywayTheme.text.scoreHero)
            Text(label, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun BirdItem(row: SummaryRow) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.tapMin)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Text(row.bird, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.summary_count, row.count), style = FlywayTheme.text.score)
        }
    }
}

@Composable
private fun Footer(onDone: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onDone,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = FlywayDimens.space5, end = FlywayDimens.space5, top = FlywayDimens.space3, bottom = FlywayDimens.space5)
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.buttonHeight),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) {
            Text(stringResource(R.string.summary_done), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** "Apr 18, 2027", in the phone's language. */
private val DAY: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** "7:48 AM", in the phone's language. */
private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

// A made-up outing for previews and screenshot tests: no real place or sighting.
internal val previewSummary = SummaryState(
    place = "Sample Pond",
    date = LocalDate.of(2027, 4, 18),
    start = LocalTime.of(6, 58),
    end = LocalTime.of(8, 10),
    length = Duration.ofMinutes(72),
    sightings = listOf(
        SummaryRow("Test Teal", 4),
        SummaryRow("Example Thrush", 2),
        SummaryRow("Made-up Pelican", 3),
        SummaryRow("Yellow-rumped Test Warbler", 6),
        SummaryRow("Sample Vireo", 1),
    ),
    sent = SummarySent.WAITING,
)

@Preview
@Composable
private fun SummaryPreview() = FlywayTheme(darkTheme = false) {
    SummaryContent(previewSummary, onDone = {})
}

@Preview
@Composable
private fun SummaryDarkPreview() = FlywayTheme(darkTheme = true) {
    SummaryContent(previewSummary, onDone = {})
}
