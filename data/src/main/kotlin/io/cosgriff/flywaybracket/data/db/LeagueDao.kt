package io.cosgriff.flywaybracket.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

/** The player's league as kept on the phone (#40): one row at most, with its members. */
@Dao
interface LeagueDao {
    /** The league with its members; null when the phone holds none. */
    @Transaction
    @Query("SELECT * FROM league LIMIT 1")
    fun observe(): Flow<StoredLeague?>

    @Transaction
    @Query("SELECT * FROM league LIMIT 1")
    suspend fun get(): StoredLeague?

    /** Deletes the league, and its members with it. */
    @Query("DELETE FROM league")
    suspend fun clear()

    @Insert
    suspend fun insert(league: LeagueEntity)

    @Insert
    suspend fun insertMembers(members: List<LeagueMemberEntity>)

    /** Keeps [league] and [members] in place of whatever league the phone held. */
    @Transaction
    suspend fun replace(league: LeagueEntity, members: List<LeagueMemberEntity>) {
        clear()
        insert(league)
        insertMembers(members)
    }
}
