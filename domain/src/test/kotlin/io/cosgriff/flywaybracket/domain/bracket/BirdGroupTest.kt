package io.cosgriff.flywaybracket.domain.bracket

import org.junit.Assert.assertEquals
import org.junit.Test

// Generic and made-up names: the table reads family words, so a made-up first word is enough.
class BirdGroupTest {
    private fun assertGroup(group: BirdGroup, vararg names: String) = names.forEach { assertEquals(it, group, BirdGroup.of(it)) }

    @Test
    fun `the four quarters, in the bracket's order`() {
        assertEquals(listOf("SONGBIRDS", "WATERBIRDS", "FLIERS", "WATERFOWL"), BirdGroup.entries.map { it.name })
    }

    @Test
    fun `songbirds by family word, and every name the table does not know`() {
        assertGroup(BirdGroup.SONGBIRDS, "Amber Warbler", "Field Sparrow", "Hermit Thrush", "Test Flycatcher", "Mystery Bird", "")
    }

    @Test
    fun `waterbirds`() {
        assertGroup(
            BirdGroup.WATERBIRDS,
            "Least Sandpiper",
            "Gray Plover",
            "Greater Yellowlegs",
            "Great Heron",
            "Black Tern",
            "Horned Grebe",
            "Common Loon",
            "Sora",
            "Killdeer",
        )
    }

    @Test
    fun `fliers - hummingbirds, swifts, swallows, nighthawks and raptors`() {
        assertGroup(
            BirdGroup.FLIERS,
            "Ruby Hummingbird",
            "Chimney Swift",
            "Barn Swallow",
            "Purple Martin",
            "Common Nighthawk",
            "Eastern Whip-poor-will",
            "Red Hawk",
            "Bald Eagle",
            "Peregrine Falcon",
            "Merlin",
            "Osprey",
            "Short Owl",
        )
    }

    @Test
    fun `waterfowl`() {
        assertGroup(BirdGroup.WATERFOWL, "Green Teal", "Wood Duck", "Snow Goose", "Tundra Swan", "Hooded Merganser", "Lesser Scaup")
    }

    @Test
    fun `the last family word decides, and a hyphenated word by its last part`() {
        // "Swallow" and "Duck" describe the bird; the last word names its family.
        assertGroup(BirdGroup.WATERBIRDS, "Swallow-tailed Gull", "Gull-billed Tern", "Black Night-Heron")
        assertGroup(BirdGroup.FLIERS, "Duck Hawk")
        assertGroup(BirdGroup.WATERFOWL, "Black Whistling-Duck")
    }

    @Test
    fun `case does not matter, and a number after the name is skipped`() {
        assertGroup(BirdGroup.WATERFOWL, "test teal", "TEST TEAL", "Test Teal 07")
        assertGroup(BirdGroup.FLIERS, "Test Swallow 12")
    }

    @Test
    fun `a family word must be a whole word`() {
        // "Hawk" inside "Nighthawk" is the nighthawk's own entry; inside another word it is nothing.
        assertGroup(BirdGroup.SONGBIRDS, "Hawkish Finch", "Ducky Wren", "Tealwing Tanager")
    }
}
