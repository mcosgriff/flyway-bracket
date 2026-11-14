package io.cosgriff.flywaybracket.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules in CLAUDE.md that are a fixed pattern in the source, checked here so the build catches them
 * instead of review. A rule that needs judgement stays with review. Paths are relative to `app/`.
 */
class SourceRulesTest {
    private val res = File("src/main/res")

    /** Every `strings.xml`, a translation's included once there is one. */
    private fun stringFiles(): List<File> =
        res.walk().filter { it.isFile && it.name == "strings.xml" && it.parentFile.name.startsWith("values") }.toList()

    // The basketball tournament's trademarks (CLAUDE.md, Decisions; #19), in any case and spacing.
    private val trademarks = listOf("Sweet 16", "Elite 8", "Elite Eight", "Final Four", "March Madness")
        .map { name -> name to Regex(name.split(" ").joinToString("""\s*""") { Regex.escape(it) }, RegexOption.IGNORE_CASE) }

    private fun trademarksIn(text: String): List<String> =
        trademarks.filter { (_, pattern) -> pattern.containsMatchIn(text) }.map { (name, _) -> name }

    @Test
    fun `strings keep to neutral round names`() {
        val found = stringFiles().flatMap { file ->
            file.readLines().withIndex().flatMap { (i, line) ->
                trademarksIn(line).map { "${file.path}:${i + 1}: \"$it\" is a trademark; use a neutral round name" }
            }
        }
        assertEquals(found.joinToString("\n"), emptyList<String>(), found)
    }

    @Test
    fun `the round name rule reads strings that are there`() {
        assertTrue("$res has no strings.xml", stringFiles().isNotEmpty())
    }

    @Test
    fun `the round name rule catches each trademark and no neutral name`() {
        listOf("Sweet 16", "sweet  16", "Elite 8", "ELITE EIGHT", "Final Four", "FinalFour", "March Madness").forEach {
            assertTrue("\"$it\" was not caught", trademarksIn(it).isNotEmpty())
        }
        listOf("Round of 16", "Quarterfinals", "Semifinals", "Final").forEach {
            assertEquals("\"$it\" was caught", emptyList<String>(), trademarksIn(it))
        }
    }
}
