package io.cosgriff.flywaybracket.data

import android.content.Context
import io.cosgriff.flywaybracket.domain.place.CountyTable
import io.cosgriff.flywaybracket.domain.place.CountyTableParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The table of states and counties that ships in the app (`assets/places/counties.tsv`), for choosing a
 * home county before a pack is on the phone (#23). Read once, off the main thread. Made by `PlacesModule`.
 */
class BundledCounties(private val context: Context) {
    private val table = lazy {
        context.assets.open(ASSET).bufferedReader().use { CountyTableParser.parse(it.readText()) }
    }

    suspend fun table(): CountyTable = withContext(Dispatchers.IO) { table.value }

    private companion object {
        const val ASSET = "places/counties.tsv"
    }
}
