package io.cosgriff.flywaybracket.server

import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.DeletedAccountsJob
import io.cosgriff.flywaybracket.server.account.PostgresPlayerStore
import io.cosgriff.flywaybracket.server.league.Leagues
import io.cosgriff.flywaybracket.server.league.PostgresLeagueStore
import io.cosgriff.flywaybracket.server.league.SeasonStartJob
import io.cosgriff.flywaybracket.server.pack.Packs
import io.cosgriff.flywaybracket.server.sighting.PostgresSightingStore
import io.cosgriff.flywaybracket.server.sighting.Sightings
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.Properties

/** Starts the server: reads the environment and the packs, migrates the database, then listens. */
fun main() {
    val config = ServerConfig.fromEnvironment(System.getenv())
    // Before the database, so a packs directory that is not there stops the server at once.
    val packs = Packs(config.packsDirectory)
    val database = Database.connect(config)
    database.migrate()
    val leagues = Leagues(PostgresLeagueStore(database.dataSource), packs)
    val accounts = Accounts(PostgresPlayerStore(database.dataSource), leagues, packs)
    val sightings = Sightings(PostgresSightingStore(database.dataSource), packs)
    val seasonStarts = SeasonStartJob(leagues)
    val deletedAccounts = DeletedAccountsJob(accounts)

    embeddedServer(Netty, port = config.port) {
        monitor.subscribe(ApplicationStarted) {
            seasonStarts.start()
            deletedAccounts.start()
        }
        monitor.subscribe(ApplicationStopped) {
            seasonStarts.close()
            deletedAccounts.close()
            database.close()
        }
        module(database, accounts, leagues, packs, sightings, ServerVersion.current, config.clientAddressHeader)
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
