package io.cosgriff.flywaybracket.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** A league's bracket, the player's picks and the results, as kept on the phone (#41). */
@Dao
interface BracketDao {
    /** The league's bracket with its birds, picks and results; null before it is first saved. */
    @Transaction
    @Query("SELECT * FROM brackets WHERE leagueId = :leagueId")
    fun observe(leagueId: String): Flow<StoredBracket?>

    @Transaction
    @Query("SELECT * FROM brackets WHERE leagueId = :leagueId")
    suspend fun get(leagueId: String): StoredBracket?

    /** Saves the row in place: its birds, picks and results stay. */
    @Upsert
    suspend fun upsert(bracket: BracketEntity)

    @Query("DELETE FROM bracket_teams WHERE leagueId = :leagueId")
    suspend fun deleteTeams(leagueId: String)

    @Insert
    suspend fun insertTeams(teams: List<BracketTeamEntity>)

    @Query("DELETE FROM bracket_picks WHERE leagueId = :leagueId")
    suspend fun deletePicks(leagueId: String)

    @Insert
    suspend fun insertPicks(picks: List<BracketPickEntity>)

    @Upsert
    suspend fun upsertResults(results: List<MatchupResultEntity>)

    /** Saves the row and its birds; and its picks in place of those it had, unless [picks] is null. */
    @Transaction
    suspend fun saveBracket(bracket: BracketEntity, teams: List<BracketTeamEntity>, picks: List<BracketPickEntity>?) {
        upsert(bracket)
        deleteTeams(bracket.leagueId)
        insertTeams(teams)
        if (picks != null) {
            deletePicks(bracket.leagueId)
            insertPicks(picks)
        }
    }

    /** Saves the row, and [picks] in place of those it had. */
    @Transaction
    suspend fun savePicks(bracket: BracketEntity, picks: List<BracketPickEntity>) {
        upsert(bracket)
        deletePicks(bracket.leagueId)
        insertPicks(picks)
    }
}
