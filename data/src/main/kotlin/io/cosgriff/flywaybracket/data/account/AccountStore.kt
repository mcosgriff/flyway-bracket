package io.cosgriff.flywaybracket.data.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The account on this phone: the id and secret the server issued, and the profile last sent to it, so
 * that a name or county is sent only when it changes (ADR 0001).
 */
data class StoredAccount(
    val playerId: String,
    val secret: String,
    val sentDisplayName: String? = null,
    val sentHomeCounty: String? = null,
) {
    // The secret is the only proof the account is this phone's: it never reaches a log.
    override fun toString() = "StoredAccount(playerId=$playerId, sentDisplayName=$sentDisplayName, sentHomeCounty=$sentHomeCounty)"
}

/** Where the account is kept. */
interface AccountStore {
    /** The account, or null when there is none or it can no longer be read. */
    suspend fun read(): StoredAccount?

    suspend fun write(account: StoredAccount)

    suspend fun clear()
}

/**
 * Keeps the account in one [file], encrypted with AES-GCM under [key]. On the phone the key is in the
 * Android Keystore ([keystoreKey]) and never leaves it; the file holds only the IV and the ciphertext.
 * A file that does not decrypt (the key is gone, the file is damaged) reads as no account.
 */
class EncryptedAccountStore(
    private val file: File,
    private val key: () -> SecretKey,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AccountStore {
    override suspend fun read(): StoredAccount? = withContext(io) {
        if (!file.isFile) return@withContext null
        try {
            decode(decrypt(file.readBytes()))
        } catch (_: IOException) {
            null
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    override suspend fun write(account: StoredAccount): Unit = withContext(io) {
        val bytes = encrypt(encode(account))
        file.parentFile?.mkdirs()
        // Written beside the file and moved into place, so a write cut short never leaves half an account.
        val partial = File(file.path + ".part")
        try {
            partial.writeBytes(bytes)
            Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.delete()
        }
    }

    override suspend fun clear(): Unit = withContext(io) {
        file.delete()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The cipher picks the IV: a Keystore key refuses one chosen by the caller.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        return ByteArrayOutputStream().apply {
            write(FORMAT)
            write(iv.size)
            write(iv)
            write(sealed)
        }.toByteArray()
    }

    private fun decrypt(stored: ByteArray): ByteArray {
        if (stored.size < 2 || stored[0].toInt() != FORMAT) throw IOException("Not an account file")
        val ivSize = stored[1].toInt()
        if (stored.size < 2 + ivSize) throw IOException("Not an account file")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, stored, 2, ivSize))
        return cipher.doFinal(stored, 2 + ivSize, stored.size - 2 - ivSize)
    }

    private fun encode(account: StoredAccount): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeUTF(account.playerId)
            out.writeUTF(account.secret)
            out.writeOptional(account.sentDisplayName)
            out.writeOptional(account.sentHomeCounty)
        }
    }.toByteArray()

    private fun decode(plain: ByteArray): StoredAccount = DataInputStream(ByteArrayInputStream(plain)).use { input ->
        StoredAccount(
            playerId = input.readUTF(),
            secret = input.readUTF(),
            sentDisplayName = input.readOptional(),
            sentHomeCounty = input.readOptional(),
        )
    }

    private fun DataOutputStream.writeOptional(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readOptional(): String? = if (readBoolean()) readUTF() else null

    private companion object {
        const val FORMAT = 1
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

/**
 * The AES key named [alias] in the Android Keystore, made on first use. It cannot be read out of the
 * Keystore, and it goes when the app's data is cleared, taking the account with it (ADR 0001).
 */
fun keystoreKey(alias: String): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(AES_KEY_BITS)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(spec)
        generateKey()
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_KEY_BITS = 256
