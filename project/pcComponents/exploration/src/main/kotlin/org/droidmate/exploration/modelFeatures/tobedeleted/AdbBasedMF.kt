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

package org.droidmate.exploration.modelFeatures.tobedeleted

import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.exploration.modelFeatures.ModelFeature
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

abstract class AdbBasedMF(val cfg: ConfigurationWrapper): ModelFeature() {
	internal fun runAdbCommand(commandArgs: List<String>,
							   timeout : Int = 1,
							   timeoutUnit: TimeUnit = TimeUnit.SECONDS): List<String> {
		val command = listOf(cfg.adbCommand, "-s", cfg.deviceSerialNumber, *commandArgs.toTypedArray())

		val builder = ProcessBuilder(command)

		val process = builder.start()

		val inputReader = InputStreamReader(process.inputStream)

		// Fixed maximum time because sometimes the process is not stopping automatically
		val success = process.waitFor(timeout.toLong(), timeoutUnit)

		val stdout = BufferedReader(inputReader).lines().toList()

		if (success) {
			val exitVal = process.exitValue()
			assert(exitVal == 0) { "Logcat process exited with error $exitVal." }
		}

		process.destroy()
		return stdout
	}
}