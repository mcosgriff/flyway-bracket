package io.cosgriff.flywaybracket.domain.outing

import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.SpeciesFrequency
import io.cosgriff.flywaybracket.domain.pack.TimeBin
import java.text.Normalizer
import java.util.Locale

// The Add sighting sheet's list (#26): the birds of the county's chart, in the order the player looks for
// them, narrowed by what they type.

/**
 * A bird the player can log.
 *
 * @property expected whether it is expected in the county in the bin the list was made for
 *   ([CountyChart.isExpected]).
 */
data class BirdChoice(val commonName: String, val expected: Boolean)

/**
 * Every bird in [chart], ordered for the bin [bin]: the birds expected there first, most often reported
 * first, then the rest by name. Birds reported equally often are in order of name.
 */
fun birdChoices(chart: CountyChart, bin: TimeBin): List<BirdChoice> {
    val (expected, rest) = chart.species.partition { chart.isExpected(it.commonName, bin) }
    return expected.sortedWith(compareByDescending<SpeciesFrequency> { it.at(bin) }.thenBy(BY_NAME) { it.commonName })
        .map { BirdChoice(it.commonName, expected = true) } +
        rest.sortedWith(compareBy(BY_NAME) { it.commonName }).map { BirdChoice(it.commonName, expected = false) }
}

/**
 * The birds of [chart] that match [query], in the order of [birdChoices]. A blank query matches every
 * bird.
 */
fun searchBirds(chart: CountyChart, bin: TimeBin, query: String): List<BirdChoice> {
    val terms = words(query)
    return birdChoices(chart, bin).filter { matches(it.commonName, terms) }
}

/**
 * Whether the bird [commonName] matches [query]: each word of the query starts a word of the name, in any
 * order. Case, accents and apostrophes are ignored, and a hyphen splits words, so "warb" finds
 * "Yellow-rumped Warbler", "rump" finds it too, and "wilsons" finds "Wilson's Warbler". A blank query
 * matches every name.
 */
fun birdNameMatches(commonName: String, query: String): Boolean = matches(commonName, words(query))

private fun matches(commonName: String, terms: List<String>): Boolean {
    if (terms.isEmpty()) return true
    val nameWords = words(commonName)
    return terms.all { term -> nameWords.any { it.startsWith(term) } }
}

/** The words of [text], folded: lower case, no accents, no apostrophes. */
private fun words(text: String): List<String> =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(APOSTROPHES, "")
        .lowercase(Locale.ROOT)
        .split(NOT_A_WORD)
        .filter { it.isNotEmpty() }

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val APOSTROPHES = Regex("['’ʼ]")
private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

/** Names in order without regard to case or accents, so "Éider" sorts beside "Eider". */
private val BY_NAME: Comparator<String> = compareBy<String> { name ->
    Normalizer.normalize(name, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)
}.thenBy { it }
