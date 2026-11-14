package io.cosgriff.flywaybracket.domain.bracket

/**
 * The four quarters of a league's field (#34), top of the bracket first. Each bird plays in its group's
 * quarter, unless a quarter is short and borrows it ([Field]).
 *
 * A quarter's name in a [Team] is the group's [name], such as `SONGBIRDS`: the app gives it words.
 */
enum class BirdGroup {
    /** Warblers, sparrows, thrushes and the rest: every bird the table does not place elsewhere. */
    SONGBIRDS,

    /** Shorebirds, herons, rails, gulls, terns, grebes and loons. */
    WATERBIRDS,

    /** Hummingbirds, swifts, swallows, nighthawks and raptors. */
    FLIERS,

    /** Ducks, geese and swans. */
    WATERFOWL,
    ;

    companion object {
        /**
         * The group of a bird, from the family word in its common name: "Wilson's Warbler" is a songbird,
         * "Least Sandpiper" a waterbird. The words are read from the last to the first, and a hyphenated
         * word from its last part ("Black-crowned Night-Heron" is a heron); the first word in the table
         * decides. Case does not matter. A name with no word in the table is a songbird.
         */
        fun of(commonName: String): BirdGroup {
            for (word in commonName.split(' ').asReversed()) {
                val whole = word.lowercase()
                FAMILY_WORDS[whole]?.let { return it }
                for (part in whole.split('-').asReversed()) {
                    FAMILY_WORDS[part]?.let { return it }
                }
            }
            return SONGBIRDS
        }

        /** Family words, in lower case, and their group. Songbirds need none: they are the default. */
        private val FAMILY_WORDS: Map<String, BirdGroup> = buildMap {
            fun group(group: BirdGroup, vararg words: String) = words.forEach { put(it, group) }

            group(
                WATERFOWL,
                "duck", "teal", "goose", "swan", "brant", "wigeon", "gadwall", "mallard", "pintail", "shoveler",
                "canvasback", "redhead", "scaup", "eider", "scoter", "bufflehead", "goldeneye", "merganser",
            )
            group(
                WATERBIRDS,
                "sandpiper", "plover", "killdeer", "yellowlegs", "dowitcher", "phalarope", "godwit", "curlew",
                "whimbrel", "willet", "dunlin", "sanderling", "knot", "turnstone", "snipe", "woodcock", "avocet",
                "stilt", "oystercatcher", "heron", "egret", "bittern", "ibis", "spoonbill", "stork", "crane",
                "rail", "sora", "coot", "gallinule", "grebe", "loon", "pelican", "cormorant", "anhinga", "gull",
                "tern", "skimmer", "jaeger",
            )
            group(
                FLIERS,
                "hummingbird", "swift", "swallow", "martin", "nighthawk", "poorwill", "nightjar",
                "chuck-will's-widow", "whip-poor-will", "hawk", "eagle", "falcon", "kestrel", "merlin", "osprey",
                "kite", "harrier", "goshawk", "vulture", "caracara", "owl",
            )
        }
    }
}
