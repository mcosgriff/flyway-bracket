package io.cosgriff.flywaybracket.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Path

/** All values are made up. */
class ServerConfigTest {
    private val environment = mapOf(
        "DATABASE_URL" to "jdbc:postgresql://db.invalid:5432/flywaybracket",
        "DATABASE_USER" to "flywaybracket",
        "DATABASE_PASSWORD" to "made-up-password",
        "PORT" to "9090",
        "PACKS_DIR" to "/srv/made-up-packs",
    )

    @Test
    fun `reads the five variables`() {
        val config = ServerConfig.fromEnvironment(environment)

        assertEquals("jdbc:postgresql://db.invalid:5432/flywaybracket", config.databaseUrl)
        assertEquals("flywaybracket", config.databaseUser)
        assertEquals("made-up-password", config.databasePassword)
        assertEquals(9090, config.port)
        assertEquals(Path.of("/srv/made-up-packs"), config.packsDirectory)
    }

    @Test
    fun `the port is 8080 when not set`() {
        assertEquals(8080, ServerConfig.fromEnvironment(environment - "PORT").port)
    }

    @Test
    fun `names every missing variable`() {
        val failure = assertThrows(ConfigException::class.java) {
            ServerConfig.fromEnvironment(mapOf("DATABASE_USER" to " ", "PORT" to "9090"))
        }

        assertEquals(listOf("DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD", "PACKS_DIR"), failure.missing)
        assertEquals(emptyList<String>(), failure.invalid)
    }

    @Test
    fun `a port that is not a port is refused`() {
        listOf("http", "0", "65536", "-1").forEach { port ->
            val failure = assertThrows(port, ConfigException::class.java) {
                ServerConfig.fromEnvironment(environment + ("PORT" to port))
            }

            assertEquals(emptyList<String>(), failure.missing)
            assertEquals(listOf("PORT"), failure.invalid)
        }
    }

    @Test
    fun `a packs directory that is not a path is refused`() {
        val failure = assertThrows(ConfigException::class.java) {
            ServerConfig.fromEnvironment(environment + ("PACKS_DIR" to "made-up\u0000packs"))
        }

        assertEquals(emptyList<String>(), failure.missing)
        assertEquals(listOf("PACKS_DIR"), failure.invalid)
    }

    @Test
    fun `no client address header unless one is named`() {
        assertEquals(null, ServerConfig.fromEnvironment(environment).clientAddressHeader)
        assertEquals(null, ServerConfig.fromEnvironment(environment + ("CLIENT_IP_HEADER" to " ")).clientAddressHeader)
    }

    @Test
    fun `reads the client address header`() {
        val config = ServerConfig.fromEnvironment(environment + ("CLIENT_IP_HEADER" to "X-Flyway-Client-Ip"))

        assertEquals("X-Flyway-Client-Ip", config.clientAddressHeader)
    }

    @Test
    fun `a client address header that is not a header name is refused`() {
        listOf("X Flyway", "X-Flyway:Ip", "X-Flyway\u0000Ip").forEach { name ->
            val failure = assertThrows(name, ConfigException::class.java) {
                ServerConfig.fromEnvironment(environment + ("CLIENT_IP_HEADER" to name))
            }

            assertEquals(emptyList<String>(), failure.missing)
            assertEquals(listOf("CLIENT_IP_HEADER"), failure.invalid)
        }
    }

    @Test
    fun `neither the failure nor the config prints the password`() {
        val failure = assertThrows(ConfigException::class.java) {
            ServerConfig.fromEnvironment(environment - "DATABASE_URL")
        }

        assertFalse(failure.toString().contains("made-up-password"))
        assertFalse(ServerConfig.fromEnvironment(environment).toString().contains("made-up-password"))
    }
}
