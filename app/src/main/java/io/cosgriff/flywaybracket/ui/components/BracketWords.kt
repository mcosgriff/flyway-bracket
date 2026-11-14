package io.cosgriff.flywaybracket.ui.components

import android.icu.text.NumberFormat
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.BirdGroup
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.Tally
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A bird's share with its base, in the phone's language: "62% of 13". A share is never shown as a bare
 * percent (#42): 1 of 1 and 8 of 13 both round to a figure that means little without the count.
 */
@Composable
fun shareText(tally: Tally): String = stringResource(R.string.share_of_eligible, percentText(tally.share), tally.eligible)

/** 0 to 1 as a whole percent, in the phone's digits and sign: "62%", "62 %". */
fun percentText(share: Double): String = NumberFormat.getPercentInstance().format(share)

/** The name a screen shows for a quarter of the bracket, by its name in a `Team` (`SONGBIRDS`). */
@StringRes
fun quarterNameRes(quarter: String): Int = when (BirdGroup.entries.firstOrNull { it.name == quarter }) {
    BirdGroup.WATERBIRDS -> R.string.quarter_waterbirds
    BirdGroup.FLIERS -> R.string.quarter_fliers
    BirdGroup.WATERFOWL -> R.string.quarter_waterfowl
    // Every quarter is a group's name; songbirds are the default group.
    BirdGroup.SONGBIRDS, null -> R.string.quarter_songbirds
}

/** The words for a season: "Spring". */
@get:StringRes
val Season.nameRes: Int
    get() = when (this) {
        Season.SPRING -> R.string.season_spring
        Season.FALL -> R.string.season_fall
    }

/** A day with its weekday, short, in the phone's language: "Sun, Mar 14". */
@Composable
fun weekdayText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale))
}
