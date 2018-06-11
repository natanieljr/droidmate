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

import org.droidmate.configuration.ConfigProperties.Exploration.deviceSerialNumber
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.logging.Markers
import org.droidmate.misc.SysCmdInterruptableExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Class to monitor the Logcat to extract method coverage information from the exploration
 */
class CoverageMonitor(private val apkName: String,
					  private val adbWrapper: IAdbWrapper,
					  private val cfg: ConfigurationWrapper) : Runnable {
	private val log: Logger by lazy { LoggerFactory.getLogger(CoverageMonitor::class.java) }
	private val outputDir: Path = cfg.coverageReportDirPath.toAbsolutePath().resolve(apkName)
	private val sysCmdExecutor = SysCmdInterruptableExecutor()
	private var running: AtomicBoolean = AtomicBoolean(true)

	override fun run() {
		log.info(Markers.appHealth, "Starting coverage monitor for $apkName. Output to ${cfg.coverageReportDirPath.toAbsolutePath()}")

		try {
			Files.createDirectories(outputDir)
			var counter = 0

			while (running.get()) {
				counter = startLogcatIfNecessary(counter)
				Thread.sleep(5)
			}

		} catch (ex: Exception) {
			ex.printStackTrace()
		}
	}

	private fun startLogcatIfNecessary(counter: Int): Int {

		val file = getLogFilename(counter)
		val output = adbWrapper.executeCommand(sysCmdExecutor, cfg[deviceSerialNumber], "", "Logcat coverage monitor",
								"logcat", "-v", "threadtime", "-s", "System.out")
		log.info("Writing logcat output into $file")
		write(file, output)

		return counter + 1
	}

	private fun getLogFilename(counter: Int): Path {
		return Paths.get(
                "$outputDir${File.separator}$apkName-logcat_${cfg[deviceSerialNumber].toString().replace(":", "-")}_%03d"
				.format(counter))
	}

	private fun write(file: Path, content: String) {
		Files.write(file, content.toByteArray())
	}

	fun stop() {
		running.set(false)
		sysCmdExecutor.stopCurrentExecutionIfExisting()
		log.info("Process destroyed")
	}

}