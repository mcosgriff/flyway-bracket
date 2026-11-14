package io.cosgriff.flywaybracket.tools.packbuilder

/**
 * A US state, or the District of Columbia.
 *
 * @param postalCode the two letters eBird puts after `US-`, such as `CO`
 * @param fips the Census Bureau's two-digit code for the state, such as `08`
 */
data class UsState(val postalCode: String, val fips: String, val name: String) {
    /** The eBird region code, such as `US-CO`. */
    val regionCode: String get() = REGION_PREFIX + postalCode

    companion object {
        private const val REGION_PREFIX = "US-"

        /** The state with this eBird region code (`US-CO`), or null when there is none. */
        fun ofRegionCode(regionCode: String): UsState? = all.firstOrNull { it.regionCode == regionCode }

        val all: List<UsState> = listOf(
            UsState("AL", "01", "Alabama"),
            UsState("AK", "02", "Alaska"),
            UsState("AZ", "04", "Arizona"),
            UsState("AR", "05", "Arkansas"),
            UsState("CA", "06", "California"),
            UsState("CO", "08", "Colorado"),
            UsState("CT", "09", "Connecticut"),
            UsState("DE", "10", "Delaware"),
            UsState("DC", "11", "District of Columbia"),
            UsState("FL", "12", "Florida"),
            UsState("GA", "13", "Georgia"),
            UsState("HI", "15", "Hawaii"),
            UsState("ID", "16", "Idaho"),
            UsState("IL", "17", "Illinois"),
            UsState("IN", "18", "Indiana"),
            UsState("IA", "19", "Iowa"),
            UsState("KS", "20", "Kansas"),
            UsState("KY", "21", "Kentucky"),
            UsState("LA", "22", "Louisiana"),
            UsState("ME", "23", "Maine"),
            UsState("MD", "24", "Maryland"),
            UsState("MA", "25", "Massachusetts"),
            UsState("MI", "26", "Michigan"),
            UsState("MN", "27", "Minnesota"),
            UsState("MS", "28", "Mississippi"),
            UsState("MO", "29", "Missouri"),
            UsState("MT", "30", "Montana"),
            UsState("NE", "31", "Nebraska"),
            UsState("NV", "32", "Nevada"),
            UsState("NH", "33", "New Hampshire"),
            UsState("NJ", "34", "New Jersey"),
            UsState("NM", "35", "New Mexico"),
            UsState("NY", "36", "New York"),
            UsState("NC", "37", "North Carolina"),
            UsState("ND", "38", "North Dakota"),
            UsState("OH", "39", "Ohio"),
            UsState("OK", "40", "Oklahoma"),
            UsState("OR", "41", "Oregon"),
            UsState("PA", "42", "Pennsylvania"),
            UsState("RI", "44", "Rhode Island"),
            UsState("SC", "45", "South Carolina"),
            UsState("SD", "46", "South Dakota"),
            UsState("TN", "47", "Tennessee"),
            UsState("TX", "48", "Texas"),
            UsState("UT", "49", "Utah"),
            UsState("VT", "50", "Vermont"),
            UsState("VA", "51", "Virginia"),
            UsState("WA", "53", "Washington"),
            UsState("WV", "54", "West Virginia"),
            UsState("WI", "55", "Wisconsin"),
            UsState("WY", "56", "Wyoming"),
        )
    }
}
