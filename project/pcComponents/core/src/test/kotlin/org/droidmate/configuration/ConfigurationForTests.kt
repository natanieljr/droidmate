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

package org.droidmate.configuration

import org.droidmate.configuration.ConfigProperties.Deploy.useApkFixturesDir
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkAppIsRunningRetryDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.deviceOperationDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.stopAppSuccessCheckDelay
import org.droidmate.configuration.ConfigProperties.Exploration.launchActivityDelay
import org.droidmate.configuration.ConfigProperties.Exploration.runOnNotInlined
import org.droidmate.configuration.ConfigProperties.Output.outputDir
import org.droidmate.configuration.ConfigProperties.Output.reportDir
import org.droidmate.configuration.ConfigProperties.Report.includePlots
import org.droidmate.configuration.ConfigProperties.Selectors.randomSeed
import org.droidmate.misc.EnvironmentConstants

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Paths

class ConfigurationForTests() {
	private var fs: FileSystem = FileSystems.getDefault()
	private val argsList: MutableList<String>

	companion object {
		private val zeroedTestConfig = arrayListOf(
				randomSeed.name, "0",
				launchActivityDelay.name, "0",
				checkAppIsRunningRetryDelay.name, "0",
				// Commented out, as there are no tests simulating rebooting. However, sometimes I am manually testing real-world rebooting.
				// Such real-world rebooting require the delays to be present, not zeroed.
//    Configuration.pn_checkDeviceAvailableAfterRebootFirstDelay, "0",
//    Configuration.pn_checkDeviceAvailableAfterRebootLaterDelays, "0",
//    Configuration.pn_waitForCanRebootDelay, "0",
				stopAppSuccessCheckDelay.name, "0",
				deviceOperationDelay.name, "0"
		)
	}

	fun get(): ConfigurationWrapper {
		return ConfigurationBuilder().build(this.argsList.toTypedArray(), this.fs)
	}

	fun withFileSystem(fs: FileSystem): ConfigurationForTests {
		this.fs = fs
		// false, because plots require gnuplot, which does not work on non-default file system
		// For details, see org.droidmate.report.plot
		this.setArg(arrayListOf(includePlots.name, "false"))

		return this
	}

	fun forDevice(): ConfigurationForTests {
		this.setArg(arrayListOf(useApkFixturesDir.name, "true"))
		return this
	}

	fun setArgs(args: List<String>): ConfigurationForTests {

		assert(args.isNotEmpty())
		assert(args.size % 2 == 0)

		this.setArg(args.take(2))

		if (args.drop(2).isNotEmpty())
			this.setArgs(args.drop(2))

		return this
	}

	private fun setArg(argNameAndVal: List<String>) {
		assert(argNameAndVal.size == 2)

		// Index of arg name
		val index = this.argsList.indexOfFirst { it == argNameAndVal[0] }

		// if arg with given name is already present in argsList
		if (index != -1) {
			this.argsList.removeAt(index) // arg name
			this.argsList.removeAt(index) // arg val
		}

		this.argsList += argNameAndVal
	}

	init {
		val newData = mutableListOf(
				outputDir.name, Paths.get(EnvironmentConstants.test_temp_dir_name).toAbsolutePath().toString(),
				reportDir.name, Paths.get(EnvironmentConstants.test_temp_dir_name).toAbsolutePath().toString(),
				runOnNotInlined.name, "true")
		newData.addAll(zeroedTestConfig)
		this.argsList = newData
	}
}
