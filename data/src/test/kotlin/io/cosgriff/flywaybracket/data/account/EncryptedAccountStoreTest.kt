package io.cosgriff.flywaybracket.data.account

import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The encrypted file, with a software AES key: the Android Keystore that holds the real one is not on the
 * JVM. The id and secret are made up.
 */
class EncryptedAccountStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val file by lazy { File(folder.root, "account") }
    private val store by lazy { EncryptedAccountStore(file, key = { key }) }
    private val account = StoredAccount(
        playerId = "00000000-0000-4000-8000-000000000001",
        secret = "made-up-secret-made-up-secret-made-up-secre",
        sentDisplayName = "Wren Example",
        sentHomeCounty = null,
    )

    @Test
    fun `nothing written reads as no account`() = runBlocking {
        assertNull(store.read())
    }

    @Test
    fun `an account round-trips`() = runBlocking {
        store.write(account)
        assertEquals(account, store.read())
        assertEquals(account, EncryptedAccountStore(file, key = { key }).read())
    }

    @Test
    fun `the file holds neither the id nor the secret in plain form`() = runBlocking {
        store.write(account)
        val bytes = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse(bytes.contains(account.secret))
        assertFalse(bytes.contains(account.playerId))
        assertFalse(bytes.contains("Wren"))
    }

    @Test
    fun `another key reads no account`() = runBlocking {
        store.write(account)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(EncryptedAccountStore(file, key = { other }).read())
    }

    @Test
    fun `a damaged file reads as no account`() = runBlocking {
        store.write(account)
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        file.writeBytes(bytes)
        assertNull(store.read())
        file.writeBytes(byteArrayOf(9))
        assertNull(store.read())
    }

    @Test
    fun `clearing removes the file`() = runBlocking {
        store.write(account)
        store.clear()
        assertFalse(file.exists())
        assertNull(store.read())
    }

    @Test
    fun `the secret never prints`() {
        assertFalse(account.toString().contains(account.secret))
    }
}
