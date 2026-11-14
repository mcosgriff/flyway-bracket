package io.cosgriff.flywaybracket.domain.place

/**
 * The states and counties a player can choose a home county from before a pack is on the phone: codes
 * and names only, from the US Census Bureau's list of counties (public domain).
 */
data class CountyTable(val states: List<StateEntry>) {
    /** The county with this code and its state, or null when the table doesn't hold it. */
    fun locate(countyCode: String): HomeCounty? {
        for (state in states) {
            val county = state.counties.firstOrNull { it.code == countyCode }
            if (county != null) return HomeCounty(state, county)
        }
        return null
    }
}

/** @param code the eBird region code, such as `US-CO` */
data class StateEntry(val code: String, val name: String, val counties: List<CountyEntry>) {
    /** The state's postal abbreviation, the part of its code after the country: `CO`. */
    val abbreviation: String get() = code.substringAfter('-')
}

/**
 * @param code the eBird region code, such as `US-CO-059`: the state's code, then the county's Census code
 * @param name the county's name without "County", such as `Jefferson`
 */
data class CountyEntry(val code: String, val name: String)

/** A county together with the state it is in. */
data class HomeCounty(val state: StateEntry, val county: CountyEntry)

/**
 * Reads the bundled table. One place a line, its code and its name with a tab between: a state
 * (`US-CO`, `Colorado`), then each of its counties (`US-CO-059`, `Jefferson`). Blank lines and lines
 * that start with `#` are skipped.
 */
object CountyTableParser {
    private val stateCode = Regex("[A-Z]{2}-[A-Z]{2}")

    /** @throws IllegalArgumentException for a line that is not a state or a county of the state above it */
    fun parse(text: String): CountyTable {
        // Each state with the counties read so far under it, in the file's order.
        val states = mutableListOf<Pair<StateEntry, MutableList<CountyEntry>>>()
        val codes = mutableSetOf<String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val parts = line.split('\t').map(String::trim)
            require(parts.size == 2 && parts[1].isNotEmpty()) { "Line ${index + 1}: expected a code, a tab and a name" }
            val (code, name) = parts
            require(codes.add(code)) { "Line ${index + 1}: $code is listed twice" }
            when {
                stateCode.matches(code) -> states += StateEntry(code, name, emptyList()) to mutableListOf()
                CountyCode.isWellFormed(code) -> {
                    val current = states.lastOrNull()
                    require(current != null && code.startsWith(current.first.code + "-")) {
                        "Line ${index + 1}: county $code is not under its state"
                    }
                    current.second += CountyEntry(code, name)
                }
                else -> throw IllegalArgumentException("Line ${index + 1}: $code is not a state or county code")
            }
        }
        return CountyTable(states.map { (state, counties) -> state.copy(counties = counties.toList()) })
    }
}
