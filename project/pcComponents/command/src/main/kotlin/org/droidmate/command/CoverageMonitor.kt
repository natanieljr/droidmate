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
package org.droidmate.command

import org.droidmate.configuration.Configuration
import org.droidmate.logging.Markers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Class to monitor the Logcat to extract method coverage information from the exploration
 */
class CoverageMonitor(private val apkName: String,
                      private val cfg: Configuration) : Runnable {
	private val log: Logger = LoggerFactory.getLogger(CoverageMonitor::class.java)
	private var p: Process? = null

	override fun run() {
		val rootReportDir = cfg.coverageReportDirPath.toAbsolutePath()
		log.info(Markers.appHealth, "Starting coverage monitor for $apkName. Output to $$")

		try {
			val script = cfg.coverageMonitorScriptPath

			val outputDir = cfg.coverageReportDirPath
			Files.createDirectories(outputDir)
			val pb = ProcessBuilder("python", script.toString(), outputDir.toString(), apkName, cfg.deviceIndex.toString())

			println(pb.command())

			pb.directory(rootReportDir.toFile())
			pb.redirectErrorStream(true)

			p = pb.start()
			p?.waitFor()
			println("End run")

		} catch (ex: Exception) {
			ex.printStackTrace()
		}
	}

	fun stop() {
		p?.destroy()
		println("Process destroyed")
	}

}