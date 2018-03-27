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
package org.droidmate.test_tools.configuration

import org.droidmate.configuration.Configuration
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.misc.BuildConstants

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Paths

class ConfigurationForTests() {
	private var fs: FileSystem = FileSystems.getDefault()
	private val argsList: MutableList<String>

	companion object {
		private val zeroedTestConfig = arrayListOf(
				Configuration.pn_randomSeed, "0",
				Configuration.pn_uiautomatorDaemonWaitForWindowUpdateTimeout, "50",
				Configuration.pn_launchActivityDelay, "0",
				Configuration.pn_checkAppIsRunningRetryDelay, "0",
				// Commented out, as there are no tests simulating rebooting. However, sometimes I am manually testing real-world rebooting.
				// Such real-world rebooting require the delays to be present, not zeroed.
//    Configuration.pn_checkDeviceAvailableAfterRebootFirstDelay, "0",
//    Configuration.pn_checkDeviceAvailableAfterRebootLaterDelays, "0",
//    Configuration.pn_waitForCanRebootDelay, "0",
				Configuration.pn_clearPackageRetryDelay, "0",
				Configuration.pn_getValidGuiSnapshotRetryDelay, "0",
				Configuration.pn_stopAppSuccessCheckDelay, "0",
				Configuration.pn_closeANRDelay, "0"
		)
	}

	fun get(): Configuration {
		return ConfigurationBuilder().build(this.argsList.toTypedArray(), this.fs)
	}

	fun withFileSystem(fs: FileSystem): ConfigurationForTests {
		this.fs = fs
		// false, because plots require gnuplot, which does not work on non-default file system
		// For details, see org.droidmate.report.plot
		this.setArg(arrayListOf(Configuration.pn_reportIncludePlots, "false"))

		return this
	}

	fun forDevice(): ConfigurationForTests {
		this.setArg(arrayListOf(Configuration.pn_useApkFixturesDir, "true"))
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

	internal fun setArg(argNameAndVal: List<String>) {
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
				Configuration.pn_droidmateOutputDir, Paths.get(BuildConstants.test_temp_dir_name).toString(),
				Configuration.pn_reportInputDir, Paths.get(BuildConstants.test_temp_dir_name).toString(),
				Configuration.pn_reportOutputDir, Paths.get(BuildConstants.test_temp_dir_name).toString(),
				Configuration.pn_runOnNotInlined)
		newData.addAll(zeroedTestConfig)
		this.argsList = newData
	}
}
