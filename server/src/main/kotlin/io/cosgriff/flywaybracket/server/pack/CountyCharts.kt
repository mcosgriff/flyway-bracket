package io.cosgriff.flywaybracket.server.pack

import io.cosgriff.flywaybracket.domain.pack.CountyChart

/** The county bar charts the server holds: [Packs], or a made-up set in tests. */
fun interface CountyCharts {
    /** The bar chart of the county with this code (`US-CO-059`), or null when no pack holds it. */
    fun chartFor(countyCode: String): CountyChart?
}
