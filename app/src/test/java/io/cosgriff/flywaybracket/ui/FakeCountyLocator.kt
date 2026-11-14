package io.cosgriff.flywaybracket.ui

import io.cosgriff.flywaybracket.data.CountyFix
import io.cosgriff.flywaybracket.data.CountyLocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/**
 * A [CountyLocator] for tests: no phone, no coordinates. [permitted] stands for Android's answer and [fix]
 * for what the phone's location gives; a null [fix] never answers, as a phone that finds no location.
 */
class FakeCountyLocator(var permitted: Boolean = false, var fix: CountyFix? = CountyFix.NoLocation) : CountyLocator {
    /** Completes the first time [locate] is called. */
    val asked = CompletableDeferred<Unit>()

    var calls = 0
        private set

    override fun permitted(): Boolean = permitted

    override suspend fun locate(): CountyFix {
        calls++
        asked.complete(Unit)
        return fix ?: awaitCancellation()
    }
}
