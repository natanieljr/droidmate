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
package org.droidmate.tools

import org.droidmate.android_sdk.Apk
import org.droidmate.android_sdk.IAaptWrapper
import org.droidmate.android_sdk.IApk
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.streams.toList

class ApksProvider constructor(val aapt: IAaptWrapper) : IApksProvider {
    companion object {
        private val log = LoggerFactory.getLogger(ApksProvider::class.java)
    }

    override fun getApks(apksDir: Path, apksLimit: Int, apksNames: List<String>, shuffle: Boolean): List<Apk> {
        assert(Files.isDirectory(apksDir))
        assert(apksLimit >= 0)

        log.info("Reading input apks from ${apksDir.toAbsolutePath()}")

        var apks = Files.list(apksDir)
                .filter { it.toString().endsWith(".apk") }
                .sorted()
                .toList()

        if (!(apksNames.isEmpty())) {
            apks = apks.filter { apk -> apk.fileName.toString() in apksNames }
            assert(apksNames.all { apks.map { p -> p.fileName.toString() }.toList().contains(it) })
        }

        assert(apksLimit <= apks.size)
        if (apksLimit != 0)
            apks = apks.take(apksLimit)

        if (apks.isEmpty())
            log.warn("No apks found! Apks were expected to be found in: {}", apksDir.toAbsolutePath().toString())

        val builtApks = apks.map { Apk.build(aapt, it) }.toList()

        builtApks.filter { !it.inlined }.forEach { p -> log.info("Following input apk is not inlined: ${p.fileName}") }

        if (shuffle)
            Collections.shuffle(builtApks)

        logApksUsedIntoRunData(builtApks)

        return builtApks
    }

    private fun logApksUsedIntoRunData(apks: Collection<IApk>) {
        log.info(Markers.runData, "Used input apks file paths:")
        log.info(Markers.runData, "")

        apks.forEach { log.info(Markers.runData, it.absolutePath) }

        log.info(Markers.runData, "")
        log.info(Markers.runData, "--------------------------------------------------------------------------------")
    }

}
