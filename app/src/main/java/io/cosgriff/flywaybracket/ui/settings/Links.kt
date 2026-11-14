package io.cosgriff.flywaybracket.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** The user guide (`site/`), served by GitHub Pages. */
const val USER_GUIDE_URL = "https://mcosgriff.github.io/flyway-bracket/"

/** The user guide's privacy page (`site/pages/privacy.md`). */
const val PRIVACY_URL = "https://mcosgriff.github.io/flyway-bracket/privacy.html"

/** The repository's page for a new issue, which offers its issue forms. */
const val REPORT_URL = "https://github.com/mcosgriff/flyway-bracket/issues/new/choose"

/**
 * Opens [url] in the phone's browser with a plain `ACTION_VIEW` intent (#44): no in-app browser, no
 * library, and nothing about the player added to the address.
 */
fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No app on the phone opens web pages. There is nowhere else to show it, so nothing happens.
    }
}
