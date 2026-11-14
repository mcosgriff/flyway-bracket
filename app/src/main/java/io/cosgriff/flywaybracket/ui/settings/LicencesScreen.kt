package io.cosgriff.flywaybracket.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.cosgriff.flywaybracket.ui.components.BackBar
import io.cosgriff.flywaybracket.ui.components.ListRow
import io.cosgriff.flywaybracket.ui.components.ListSection
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/** The app's licence, the font's, and the open-source libraries in the app (#44). */
@Composable
fun LicencesScreen(onBack: () -> Unit, viewModel: LicencesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LicencesContent(state, onBack = onBack, onOpenLink = context::openInBrowser)
}

/** @param onOpenLink opens a licence's text in the browser */
@Composable
fun LicencesContent(state: LicencesState, onBack: () -> Unit, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    // The font's licence is long: it opens when asked for.
    var fontOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(onBack) },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets),
            contentPadding = PaddingValues(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space2),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            item {
                Text(
                    text = stringResource(R.string.licences_title).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(bottom = FlywayDimens.space3)
                        .semantics { heading() },
                )
            }
            item {
                ListSection(R.string.licences_app_title) { Caption(stringResource(R.string.app_licence)) }
            }
            item {
                Column(modifier = Modifier.padding(top = FlywayDimens.space3)) {
                    ListSection(R.string.licences_font_title) {
                        FontCard(state.fontLicence, open = fontOpen, onToggle = { fontOpen = !fontOpen })
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(top = FlywayDimens.space3)) {
                    ListSection(R.string.licences_libraries_title) {
                        val libraries = state.libraries
                        when {
                            state.notRead -> Caption(stringResource(R.string.licences_not_read))
                            libraries != null ->
                                Caption(pluralStringResource(R.plurals.licences_libraries_caption, libraries.size, libraries.size))
                        }
                    }
                }
            }
            items(state.libraries.orEmpty(), key = { it.coordinates }) { library ->
                val url = library.licences.firstNotNullOfOrNull { it.url }
                ListRow(
                    title = library.name,
                    caption = stringResource(R.string.licences_library_version, library.coordinates, library.version) +
                        library.licences.joinToString(separator = "") { "\n" + it.name },
                    onClick = url?.let { { onOpenLink(it) } },
                )
            }
        }
    }
}

/** Inter and its licence's name; the whole text under it once opened. */
@Composable
private fun FontCard(text: String?, open: Boolean, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
        ) {
            Text(stringResource(R.string.licences_inter), style = MaterialTheme.typography.titleMedium)
            Caption(stringResource(R.string.licences_inter_licence))
            if (text != null) {
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
                    colors = ButtonDefaults.textButtonColors(contentColor = FlywayTheme.colors.pick),
                ) {
                    Text(
                        text = stringResource(if (open) R.string.licences_hide_text else R.string.licences_show_text),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (open) Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Made-up libraries for previews and screenshot tests, marked as samples; the real list is the build's.
internal val previewLicences = LicencesState(
    fontLicence = "Sample licence text.",
    libraries = listOf(
        LibraryLicence(
            name = "Sample Library",
            coordinates = "com.example:sample",
            version = "1.0.0",
            licences = listOf(Licence("Sample Licence 1.0", "https://example.com/licence")),
        ),
        LibraryLicence(
            name = "Second Sample",
            coordinates = "com.example:second",
            version = "2.1.0",
            licences = listOf(Licence("Sample Licence 1.0", "https://example.com/licence"), Licence("Other Sample Licence")),
        ),
    ),
)

@Preview
@Composable
private fun LicencesPreview() = FlywayTheme(darkTheme = false) {
    LicencesContent(previewLicences, onBack = {}, onOpenLink = {})
}
