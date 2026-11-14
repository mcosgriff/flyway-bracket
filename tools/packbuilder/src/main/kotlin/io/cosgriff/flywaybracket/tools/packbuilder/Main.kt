package io.cosgriff.flywaybracket.tools.packbuilder

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.system.exitProcess

private const val VINTAGE_OPTION = "--census-vintage="
private const val OUT_DIR = "out"
private const val USAGE = "Usage: packbuilder <state code, such as US-CO> [$VINTAGE_OPTION<year>]"

private const val EXIT_BUILT = 0
private const val EXIT_FAILED = 1
private const val EXIT_USAGE = 2

fun main(args: Array<String>) {
    exitProcess(run(args.toList(), Path.of("packs-local"), LocalDate.now(), System.out, System.err))
}

/**
 * Builds the pack and writes it to `<packs-local>/out/`. All of the tool's wording is in this file.
 *
 * @return the process's exit code: 0 when a pack was written
 */
internal fun run(args: List<String>, packsLocal: Path, today: LocalDate, out: PrintStream, err: PrintStream): Int {
    val (options, codes) = args.partition { it.startsWith("--") }
    val vintages = options.map { it.removePrefix(VINTAGE_OPTION).toIntOrNull() }
    val known = options.all { it.startsWith(VINTAGE_OPTION) }
    if (codes.size != 1 || !known || vintages.size > 1 || vintages.any { it == null }) {
        err.println(USAGE)
        return EXIT_USAGE
    }

    return when (val result = PackBuilder(packsLocal).build(codes.single(), today, vintages.firstOrNull())) {
        is PackBuildResult.Built -> {
            val file = packsLocal.resolve(OUT_DIR).resolve(result.pack.fileName)
            Files.createDirectories(file.parent)
            file.writeBytes(result.bytes)
            out.println(summary(result, file))
            EXIT_BUILT
        }
        is PackBuildResult.Failed -> {
            err.println(explain(result))
            EXIT_FAILED
        }
    }
}

private fun summary(built: PackBuildResult.Built, file: Path): String = listOf(
    "Wrote $file",
    "File size: ${built.bytes.size} bytes",
    "Source: ${built.pack.source}",
    "Counties found: ${built.speciesByCounty.size}",
    *built.speciesByCounty.map { (county, species) -> "  ${label(county)}: $species species" }.toTypedArray(),
    missing("Counties missing a bar chart", built.missingBarChart),
    missing("Counties missing an outline", built.missingOutline),
).joinToString("\n")

private fun missing(title: String, counties: List<CountyRef>): String =
    if (counties.isEmpty()) "$title: none" else "$title: ${counties.size}\n" + counties.joinToString("\n") { "  ${label(it)}" }

private fun label(county: CountyRef): String = if (county.name == null) county.code else "${county.code} ${county.name}"

private fun explain(failure: PackBuildResult.Failed): String = when (failure) {
    is PackBuildResult.UnknownState ->
        "${failure.code} is not a US state code. Give US- and the state's two letters, such as US-CO."
    is PackBuildResult.NoOutlinesFile ->
        "No county outlines: ${failure.file} does not exist. See docs/packs.md."
    is PackBuildResult.BadOutlinesFile ->
        "${failure.file.name} was rejected: ${failure.reason}" + failure.feature?.let { " (feature $it)" }.orEmpty()
    is PackBuildResult.NoCensusVintage ->
        "${failure.file.name} does not say which year's Census file it is. Run again with $VINTAGE_OPTION<year>."
    is PackBuildResult.BadBarChart ->
        "${failure.file.name} was rejected: ${failure.reason}" + failure.line?.let { " (line $it)" }.orEmpty()
    is PackBuildResult.DuplicateBarChart ->
        "${failure.countyCode} has more than one bar chart file: ${failure.files.joinToString { it.name }}. Keep one."
    is PackBuildResult.NoCounties -> listOf(
        "No county has both a bar chart and an outline, so no pack was written.",
        missing("Counties missing a bar chart", failure.missingBarChart),
        missing("Counties missing an outline", failure.missingOutline),
    ).joinToString("\n")
    is PackBuildResult.Unreadable ->
        "The pack that was built does not read back: ${failure.reason}. This is a fault in the tool."
}
