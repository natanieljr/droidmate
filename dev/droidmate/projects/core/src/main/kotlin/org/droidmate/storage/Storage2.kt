// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.storage

import com.konradjamrozik.isRegularFile
import com.konradjamrozik.toList
import org.droidmate.exploration.data_aggregators.ApkExplorationOutput2
import org.slf4j.LoggerFactory
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Persistent storage. Allows for serializing to HDD and back.
 */
class Storage2 constructor(private val droidmateOutputDirPath: Path) : IStorage2 {
    companion object {
        private val log = LoggerFactory.getLogger(Storage2::class.java)

        /*val serializationConfig: FSTConfiguration
            get() {
                return FSTConfiguration.createJsonConfiguration(true, false)
                        .apply {
                            registerSerializer(URI::class.java, FSTURISerializer(), false)
                            registerSerializer(LocalDateTime::class.java, FSTLocalDateTimeSerializer(), false)
                        }
            }*/
        private val serializedFileTimestampPattern = DateTimeFormatter.ofPattern("yyyy MMM dd HHmm")
        val ser2FileExt = ".ser2"
    }

    private var timestamp: String = ""

    override fun serializeToFile(obj: ApkExplorationOutput2, file: Path) {
        //val serializer = ApkExplorationOutput2::class.serializer()
        //val data = JSON.indented.stringify(serializer, obj)
        //Files.write(file, data.toByteArray())

        val serOut = ObjectOutputStream(
                //val serOut = serializationConfig.getObjectOutput(
                Channels.newOutputStream(FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)))
        serOut.writeObject(obj)
        serOut.close()
    }

    override fun getSerializedRuns2(): Collection<Path> {
        return Files.list(droidmateOutputDirPath)
                .filter { p -> p.isRegularFile && p.fileName.toString().endsWith(ser2FileExt) }
                .toList()
    }

    override fun deserialize(serPath: Path): ApkExplorationOutput2 {
        //val serializer = ApkExplorationOutput2::class.serializer()
        //val data = Files.readAllLines(serPath).joinToString(System.lineSeparator())
        //return JSON.indented.parse(serializer, data)

        val input = ObjectInputStream(Channels.newInputStream(FileChannel.open(serPath, StandardOpenOption.READ)))
        //val input = LegacyObjectInputStream(Channels.newInputStream(FileChannel.open(serPath, StandardOpenOption.READ)))
        //val input = serializationConfig.getObjectInput(
        //        Channels.newInputStream(FileChannel.open(serPath, StandardOpenOption.READ)))
        val obj = input.readObject()
        input.close()
        return obj as ApkExplorationOutput2
    }

    override fun serialize(obj: ApkExplorationOutput2, namePart: String) {
        if (timestamp.isEmpty())
            timestamp = LocalDateTime.now().format(serializedFileTimestampPattern)

        val ser2 = getNewPath("$timestamp $namePart$ser2FileExt")
        log.info("Serializing ${obj.javaClass.simpleName} to $ser2")
        serializeToFile(obj, ser2)
    }

    private fun getNewPath(fileName: String): Path {
        ensureDroidmateOutputDirExists()

        var path = droidmateOutputDirPath.resolve(fileName)

        if (!Files.exists(path.parent)) {
            Files.createDirectories(path.parent)
            if (!Files.isDirectory(path.parent))
                assert(false)
        }

        path = ensurePathDoesntExist(path)
        assert(Files.isDirectory(path.parent))
        assert(!Files.exists(path))
        return path
    }

    override fun delete(deletionTargetNameSuffix: String) {
        Files.list(droidmateOutputDirPath).forEach { p ->
            if (p.fileName.toString().contains(deletionTargetNameSuffix)) {
                val success = Files.deleteIfExists(p)
                if (success)
                    log.debug("Deleted: " + p.fileName.toString())
                else
                    log.debug("Failed to delete: " + p.fileName.toString())
            }
        }
    }

    private fun ensureDroidmateOutputDirExists() {
        if (!Files.isDirectory(droidmateOutputDirPath)) {
            Files.createDirectories(droidmateOutputDirPath)

            if (!Files.isDirectory(droidmateOutputDirPath))
                assert(false, { "Failed to create droidmate output directory. Path: $droidmateOutputDirPath" })

            log.info("Created directory: $droidmateOutputDirPath")
        }

        assert(Files.isDirectory(droidmateOutputDirPath))
    }

    private fun makeFallbackOutputFileWithRandomUUIDInName(targetOutPath: Path): Path {

        val fallbackOutPath = droidmateOutputDirPath.resolve("fallback-copy-${UUID.randomUUID()}")
        log.warn("Failed to delete $targetOutPath. Trying to create a pointer to nonexistent file with path: $fallbackOutPath")


        assert(!Files.exists(fallbackOutPath), {
            "The $fallbackOutPath exists. This shouldn't be possible, " +
                    "as its file path was just created with a random UUID"
        })


        assert(Files.isDirectory(fallbackOutPath.parent))
        assert(Files.notExists(fallbackOutPath))
        return fallbackOutPath
    }

    private fun ensurePathDoesntExist(path: Path): Path {
        var newPath = path
        if (Files.exists(newPath)) {
            log.trace("Deleting $newPath")
            Files.delete(newPath)
            if (Files.exists(newPath)) {
                newPath = makeFallbackOutputFileWithRandomUUIDInName(path)
            }
        }

        assert(Files.isDirectory(newPath.parent))
        assert(Files.notExists(newPath))
        return newPath
    }
}