package io.cosgriff.flywaybracket.server

import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Everything the server is told from outside, read from environment variables and nowhere else.
 *
 * Not a data class: a generated `toString` would print the database password into a log.
 *
 * @property databaseUrl a JDBC URL, such as `jdbc:postgresql://localhost:5432/flywaybracket`.
 * @property packsDirectory the directory the operator copies `.fbpack` files into (#33). Whether it exists
 *   is checked when the packs are first read, not here.
 */
class ServerConfig(
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val port: Int,
    val packsDirectory: Path,
) {
    companion object {
        const val DATABASE_URL = "DATABASE_URL"
        const val DATABASE_USER = "DATABASE_USER"
        const val DATABASE_PASSWORD = "DATABASE_PASSWORD"
        const val PORT = "PORT"
        const val PACKS_DIR = "PACKS_DIR"

        /** The port when `PORT` is not set. */
        const val DEFAULT_PORT = 8080

        private val PORTS = 1..65535

        /**
         * @param environment the variables, as `System.getenv()` gives them
         * @throws ConfigException naming every variable that is missing or unusable, never a value
         */
        fun fromEnvironment(environment: Map<String, String>): ServerConfig {
            fun text(name: String): String? = environment[name]?.takeIf { it.isNotBlank() }

            val url = text(DATABASE_URL)
            val user = text(DATABASE_USER)
            val password = text(DATABASE_PASSWORD)
            val packsText = text(PACKS_DIR)
            val missing = listOfNotNull(
                DATABASE_URL.takeIf { url == null },
                DATABASE_USER.takeIf { user == null },
                DATABASE_PASSWORD.takeIf { password == null },
                PACKS_DIR.takeIf { packsText == null },
            )

            val portText = text(PORT)
            val port = if (portText == null) DEFAULT_PORT else portText.toIntOrNull()?.takeIf { it in PORTS }
            val packs = packsText?.let(::pathOrNull)
            val invalid = listOfNotNull(PORT.takeIf { port == null }, PACKS_DIR.takeIf { packsText != null && packs == null })

            if (url == null || user == null || password == null || port == null || packs == null) {
                throw ConfigException(missing, invalid)
            }
            return ServerConfig(url, user, password, port, packs)
        }

        private fun pathOrNull(text: String): Path? = try {
            Path.of(text)
        } catch (_: InvalidPathException) {
            null
        }
    }
}

/**
 * The environment does not describe a server that can start.
 *
 * @property missing the variables that are not set
 * @property invalid the variables whose value cannot be used
 */
class ConfigException(val missing: List<String>, val invalid: List<String>) :
    IllegalStateException("Environment variables missing: $missing, not usable: $invalid")
