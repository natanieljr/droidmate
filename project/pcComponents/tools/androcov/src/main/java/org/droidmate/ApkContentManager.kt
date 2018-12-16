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

package org.droidmate

import com.konradjamrozik.Resource
import com.google.common.base.Stopwatch
import org.apache.commons.lang3.ArrayUtils
import org.droidmate.manifest.ManifestInstrumenter
import org.droidmate.misc.SysCmdExecutor
import org.slf4j.LoggerFactory

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Originally copied to a large extent from the aggregator project.
 */
class ApkContentManager @Throws(IOException::class)
constructor(private val originalApkPath: Path, private val apkContentDir: Path, globalOutputDir: Path) {

    private val apkToolName = "apktool.jar"
    private val apkToolPath: Path = globalOutputDir.resolve(apkToolName)

    init {
        if (Files.exists(apkToolPath)) {
            Files.delete(apkToolPath)
        }

        Resource(apkToolName).extractTo(globalOutputDir)
    }

    @Throws(IOException::class)
    fun extractApk(forceOverwriteApkContentDir: Boolean) {
        // do not extract again if app has not changed since last extraction
        if (!forceOverwriteApkContentDir && Files.exists(apkContentDir)
            && Files.getLastModifiedTime(apkContentDir).compareTo(Files.getLastModifiedTime(originalApkPath)) >= 0) {
            LOGGER.info(
                "Apk hasn't changed since last extraction. Omitting ApkTool invocation. Use 'forceOverwriteApkContentDir' to force an update!")
            return
        }

        LOGGER.info("Invoking apk tool to extract apks content")
        val stopWatch = Stopwatch.createStarted()
        // Added -r, otherwise some apps invoked:
        // brut.androlib.AndrolibException: brut.common.BrutException: could not exec
        invokeApkTool("-s", "-f", "-r", "d", "-o", apkContentDir.toString(), originalApkPath.toString())
        LOGGER.info("Apk tool finished after {}", stopWatch)
    }

    @Throws(IOException::class)
    fun buildApk(outApk: Path) {
        LOGGER.info("Invoking apk tool to build apk from content dir")
        val stopWatch = Stopwatch.createStarted()
        invokeApkTool("b", apkContentDir.toString(), "-o", outApk.toString())
        LOGGER.info("Apk tool finished after {}", stopWatch)
    }

    private fun invokeApkTool(vararg params: String) {
        try {
            val sysCmdExecutor = SysCmdExecutor()
            val cmdDescription = "Command for invoking the apk tool"
            sysCmdExecutor.executeWithTimeout(cmdDescription, 10000, "java", "-jar", apkToolPath.toString(), *params)
        } catch (e: Exception) {
            LOGGER.error("Error during ApkTool execution", e)
            throw RuntimeException(e)
        }

    }

    fun addPermissionsToApp(vararg permissions: String) {
        val mi = ManifestInstrumenter(apkContentDir.resolve("AndroidManifest.xml"))
        for (permission in permissions) {
            mi.addPermission(permission)
        }
        mi.writeOut()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ApkContentManager::class.java)
    }
}
