// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
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
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.frontend

import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.droidmate.test_tools.DroidmateTestCase
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.io.*
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * In order to inline DroidMate needs to execute an aapt command
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class InlineTest : DroidmateTestCase() {

    inner class Resource(var path: String) {
        val file: File = File(path)

        fun getParentDirectory(): Resource { return Resource(file.parentFile.absolutePath) }
        fun remove() { file.deleteRecursively() }
        override fun toString(): String { return path }
    }

    fun getFileFromResourceDirectory(path: String): Resource {
        val purgedPath = Paths.get(path).toString()
        return Resource(this.javaClass.getResource(purgedPath).path)
    }

    /**
     * Return the absolute directory, which contains the passed file located
     * in the resources directory.
     */
    fun getDirectoryFromResourceFile(path: String): Resource {
        val purgedPath = Paths.get(path).toString()
        val filePath = this.javaClass.getResource(purgedPath).path
        val index = filePath.lastIndexOf('/')
        return if (index > 0) {
            Resource(filePath.substring(0, index))
        } else {
            Resource("/")
        }
    }

    fun getTmpDirectory(path: Resource): Resource {
        val tmpDir = createTempDir(directory = path.file)
        return Resource(tmpDir.absolutePath)
    }

    fun removeFiles(files: MutableList<Resource>) {
        files.forEach { it.remove() }
    }

    fun getFileChecksum(file: Resource): String {
        // Use crc, because it is pretty fast
        val hc = Files.asByteSource(file.file).hash(Hashing.crc32())
        return hc.toString()
    }

    /**
     * Inspired by: https://stackoverflow.com/a/27050680/2047688
     */
    fun unzip(zipFile: File, targetDirectory: File) {
        val zis = ZipInputStream(BufferedInputStream(FileInputStream(zipFile)))
        try {
            var ze: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(8192)
            while (ze != null || zis.available() != 0) {
                val file = File(targetDirectory, ze!!.name)
                val dir = if (ze.isDirectory) file else file.parentFile
                if (!dir.isDirectory && !dir.mkdirs()) {
                    throw FileNotFoundException("Failed to ensure directory: " + dir.absolutePath)
                }
                if (ze.isDirectory) { continue }
                val fout = FileOutputStream(file)
                try {
                    var count: Int = zis.read(buffer)
                    while (count != -1) {
                        fout.write(buffer, 0, count)
                        count = zis.read(buffer)
                    }
                } finally {
                    fout.close()
                }

                ze = zis.nextEntry
            }
        } finally {
            zis.close()
        }
    }

    /**
     * Compares all the passed apks against each other, by unzipping them
     * and comparing the checksums of classes.dex, resources.arsc and AndroidManifest.xml.
     * This could be more sophisticated by comparing more files, but should
     * be sufficient for now.
     * Note: Comparing only the checksum of the .apk does not work, some
     * files differ with each inlining process.
     */
    fun areInlinedApksEqual(vararg apks: Resource): Boolean {
        val apkEntries: Array<String> = arrayOf("classes.dex", "resources.arsc", "AndroidManifest.xml")
        val concatenatedHashes: MutableList<String> = mutableListOf()

        for (apk in apks) {
            assert(apk.path.endsWith(".apk"))
            val unzippedApk = getTmpDirectory(apk.getParentDirectory())
            unzip(apk.file, unzippedApk.file)

            concatenatedHashes.add(apkEntries.joinToString {
                getFileChecksum(Resource("${unzippedApk.path}/$it")) })
            unzippedApk.remove()
        }

        return if (apks.size > 0) {
            concatenatedHashes.distinct().size == 1
        } else {
            true
        }
    }

//    TODO maybe work with a mocked file system
//        val mockedFs = MockFileSystem(arrayListOf("GuiApkFixture-debug"))
//        val apks = mockedFs.apks
//        val apk1 = apks.single { it.fileName == "GuiApkFixture-debug.apk" }
//        Resource().extractTo()
    // Act
//                val exitStatus = DroidmateFrontend.execute(
//                        cfg.args,
//                        { ExploreCommand.build(cfg, { ExplorationStrategyPool.build(it, cfg) }, deviceToolsMock) },
//                        mockedFs.fs,
//                        handler
//                )
    @Test
    fun `Inline apk`() {

        val apkName = "GuiApkFixture-debug"
        val filesToBeDeleted: MutableList<Resource> = mutableListOf()

        try {
            val targetApksDir: Resource = getDirectoryFromResourceFile("/apks/notinlined/$apkName.apk")
            val outputDir: Resource = getTmpDirectory(targetApksDir)
            filesToBeDeleted.addAll(arrayOf(outputDir, targetApksDir))

            val args = arrayOf(
                    "--ExecutionMode-inline=true",
                    "--Exploration-apksDir=" + targetApksDir,
                    "--Output-droidmateOutputDirPath=" + outputDir)
            val exitStatus = DroidmateFrontend.execute(args)

            // exitStatus=4 => assertion failed
            assert(exitStatus == 0) { "Exit status was: $exitStatus" }

            // Compare checksums of the just inlined apk and the provided inlined apk from the resources folder
            val referenceApk = getFileFromResourceDirectory("/apks/inlined/$apkName-inlined.apk")
            val computedApk = getFileFromResourceDirectory("/apks/notinlined/$apkName-inlined.apk")

            assert(areInlinedApksEqual(referenceApk, computedApk)) { "Reference inlined apk and computed apk are not equal." }
        } finally {
            // Clean up
            // Note that, the original resources are copied into a temporary build folder containing the resources
            removeFiles(filesToBeDeleted)
        }

    }

}
