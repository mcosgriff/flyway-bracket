package io.cosgriff.flywaybracket.domain.pack

import java.time.LocalDate

/**
 * One of the 48 parts of a year that bar chart data is counted in: four per month, covering days
 * 1 to 7, 8 to 14, 15 to 21, and 22 to the month's end. They are not seven-day weeks.
 */
@JvmInline
value class TimeBin(val index: Int) {
    init {
        require(index in 0 until COUNT) { "TimeBin index $index is outside 0..${COUNT - 1}" }
    }

    /** 1 for January to 12 for December. */
    val month: Int get() = index / PER_MONTH + 1

    /** 1 to 4 within the month. */
    val part: Int get() = index % PER_MONTH + 1

    /** The bin before this one; the year wraps, so January's first follows December's last. */
    fun previous(): TimeBin = TimeBin((index + COUNT - 1) % COUNT)

    fun next(): TimeBin = TimeBin((index + 1) % COUNT)

    companion object {
        const val PER_MONTH = 4
        const val COUNT = 48
        private const val DAYS_PER_PART = 7

        fun of(date: LocalDate): TimeBin {
            val part = minOf((date.dayOfMonth - 1) / DAYS_PER_PART, PER_MONTH - 1)
            return TimeBin((date.monthValue - 1) * PER_MONTH + part)
        }

        fun of(month: Int, part: Int): TimeBin {
            require(month in 1..12 && part in 1..PER_MONTH) { "No bin for month $month, part $part" }
            return TimeBin((month - 1) * PER_MONTH + part - 1)
        }
    }
}
