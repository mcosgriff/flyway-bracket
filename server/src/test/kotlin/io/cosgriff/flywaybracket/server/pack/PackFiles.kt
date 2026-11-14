package io.cosgriff.flywaybracket.server.pack

import io.cosgriff.flywaybracket.domain.pack.StatePack
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** Packs over a new, empty directory: for tests of other endpoints. */
fun noPacks(): Packs {
    val directory = Files.createTempDirectory("no-packs")
    directory.toFile().deleteOnExit()
    return Packs(directory)
}

/** Writes [pack] into [directory] as its `.fbpack` file, or under [fileName], and returns the bytes. */
fun writePack(directory: Path, pack: StatePack, fileName: String = pack.fileName): ByteArray =
    StatePackCodec.encode(pack).also { write(directory, fileName, it) }

/**
 * Writes [bytes] to the file, then sets its modified time a second after the last one, so a change shows
 * however coarse the file system's clock is.
 */
fun write(directory: Path, fileName: String, bytes: ByteArray) {
    val file = directory.resolve(fileName)
    val before = if (Files.exists(file)) Files.getLastModifiedTime(file).toMillis() else 0L
    Files.write(file, bytes)
    val after = maxOf(Files.getLastModifiedTime(file).toMillis(), before + 1_000)
    Files.setLastModifiedTime(file, FileTime.fromMillis(after))
}
