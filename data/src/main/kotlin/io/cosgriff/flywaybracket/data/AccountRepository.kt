package io.cosgriff.flywaybracket.data

import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.server.Credentials
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The player's anonymous account on the league server (#38, ADR 0001). Nothing here calls the server
 * until the player has agreed and [createAccount] has been called: with no account, every other function
 * returns without a call. Made by `ServerModule`.
 */
class AccountRepository(
    private val server: ServerClient,
    private val store: AccountStore,
    private val settings: SettingsRepository,
) {
    // Held while the account is read or changed, so the store and memory never part.
    private val mutex = Mutex()

    // The account once the store has been read: null while unread, then Loaded(null) for none.
    private val account = MutableStateFlow<Loaded?>(null)

    private data class Loaded(val account: StoredAccount?)

    /** Whether the phone has an account, and again after each change. */
    fun hasAccount(): Flow<Boolean> = flow {
        loaded()
        emitAll(account.map { it?.account != null })
    }.distinctUntilChanged()

    /**
     * Asks the server for an account, keeps it, then sends the display name and home county from the
     * settings. Only the first call has to succeed: a profile that fails to send is sent again by
     * [syncProfile]. With an account already on the phone it makes no call.
     */
    suspend fun createAccount(): ServerResult<Unit> {
        mutex.withLock {
            if (loadedLocked() == null) {
                when (val created = server.createAccount()) {
                    is ServerResult.Failed -> return created
                    is ServerResult.Ok -> try {
                        save(StoredAccount(created.value.playerId, created.value.secret))
                    } catch (_: IOException) {
                        // The phone could not keep it. The account on the server is left unused.
                        return ServerResult.Failed(ServerProblem.Refused(code = null))
                    }
                }
            }
        }
        syncProfile()
        return ServerResult.Ok(Unit)
    }

    /**
     * Deletes the account on the server, then on the phone. An account the server no longer knows
     * (`UNAUTHORIZED`) is deleted from the phone too. Any other failure keeps it on the phone, so the player
     * can try again.
     */
    suspend fun deleteAccount(): ServerResult<Unit> = mutex.withLock {
        val current = loadedLocked() ?: return ServerResult.Ok(Unit)
        val deleted = server.deleteAccount(current.credentials)
        val gone = deleted is ServerResult.Ok ||
            (deleted is ServerResult.Failed && deleted.problem == ServerProblem.Refused(ApiErrorCode.UNAUTHORIZED))
        if (!gone) return deleted
        store.clear()
        account.value = Loaded(null)
        ServerResult.Ok(Unit)
    }

    /**
     * Sends the display name and the home county if either differs from what was last sent, and only the
     * one that differs. A value cleared in the settings is not sent: the server keeps the last one.
     */
    suspend fun syncProfile(): ServerResult<Unit> = mutex.withLock {
        val current = loadedLocked() ?: return ServerResult.Ok(Unit)
        val player = settings.settings().first()
        val update = UpdateProfileRequest(
            displayName = player.displayName?.takeIf { it != current.sentDisplayName },
            homeCounty = player.homeCountyCode?.takeIf { it != current.sentHomeCounty },
        )
        if (update.displayName == null && update.homeCounty == null) return ServerResult.Ok(Unit)
        val sent = server.updateProfile(current.credentials, update)
        if (sent is ServerResult.Ok) {
            val updated = current.copy(
                sentDisplayName = update.displayName ?: current.sentDisplayName,
                sentHomeCounty = update.homeCounty ?: current.sentHomeCounty,
            )
            try {
                save(updated)
            } catch (_: IOException) {
                // Not kept: the same values are sent again next time, which changes nothing on the server.
                account.value = Loaded(updated)
            }
        }
        sent
    }

    /**
     * Sends the profile each time the settings change while there is an account, and once when an
     * account appears. A send that fails waits for the next change or the next start. Runs until cancelled.
     */
    suspend fun keepProfileInSync() {
        combine(settings.settings(), hasAccount()) { player, has -> player to has }
            .distinctUntilChanged()
            .collect { (_, has) -> if (has) syncProfile() }
    }

    /**
     * What the server holds about the player (`GET /v1/account`), the league they are in among it (#42). With
     * no account it makes no call and answers `UNAUTHORIZED`, as the server would.
     */
    suspend fun profile(): ServerResult<ProfileResponse> {
        val current = loaded() ?: return ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.UNAUTHORIZED))
        return server.profile(current.credentials)
    }

    /** The account's id and secret, for another call that needs them (#39, #41); null with no account. */
    internal suspend fun credentials(): Credentials? = loaded()?.credentials

    private suspend fun loaded(): StoredAccount? {
        account.value?.let { return it.account }
        return mutex.withLock { loadedLocked() }
    }

    /** Only with [mutex] held. */
    private suspend fun loadedLocked(): StoredAccount? {
        account.value?.let { return it.account }
        return store.read().also { account.value = Loaded(it) }
    }

    private suspend fun save(updated: StoredAccount) {
        store.write(updated)
        account.value = Loaded(updated)
    }

    private val StoredAccount.credentials get() = Credentials(playerId, secret)
}
