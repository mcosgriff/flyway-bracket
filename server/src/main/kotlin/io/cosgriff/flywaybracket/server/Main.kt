package io.cosgriff.flywaybracket.server

import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.Properties

/** Starts the server: reads the environment, migrates the database, then listens. */
fun main() {
    val config = ServerConfig.fromEnvironment(System.getenv())
    val database = Database.connect(config)
    database.migrate()

    embeddedServer(Netty, port = config.port) {
        monitor.subscribe(ApplicationStopped) { database.close() }
        module(database, ServerVersion.current)
    }.start(wait = true)
}

object ServerVersion {
    private const val UNKNOWN = "unknown"

    /** The version the build wrote into `server.properties`, from `version.txt`. */
    val current: String by lazy {
        val properties = Properties()
        ServerVersion::class.java.getResourceAsStream("/server.properties")?.use(properties::load)
        properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: UNKNOWN
    }
}
