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

import com.konradjamrozik.OS
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.clearPackageRetryDelay
import org.droidmate.configuration.ConfigProperties.Exploration.apiVersion
import org.droidmate.configuration.ConfigProperties.Exploration.apksDir
import org.droidmate.configuration.ConfigProperties.Output.droidmateOutputDirPath
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.test_tools.DroidmateTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.net.URI

import java.nio.file.FileSystems

/**
 * Be aware that ConfigurationBuilder reads from buildConstants.properties file.
 */
@RunWith(JUnit4::class)
class ConfigurationBuilderTest : DroidmateTestCase() {

	private val exeExt = if (OS.isWindows) ".exe" else ""

	@Test
	fun `Pass empty args and build configuration`() {
		val config = ConfigurationBuilder().build(emptyArray(), FileSystems.getDefault())

		// Default values from Configuration
		expect(30, config[actionLimit])
		expect(23, config[apiVersion])
		expect(1000, config[clearPackageRetryDelay])
		expect(100, config[resetEvery])
		expect(URI("./apks"), config[apksDir])
		expect(URI("./out"), config[droidmateOutputDirPath])


		// Values read from buildConstants.properties file
		expect("api_policies.txt", config.apiPoliciesFile.fileName.toString())
		expect(config.portFile.fileName.toString(),"port.tmp") { fileName, ref -> fileName.startsWith(ref) }
		expect(URI(config.aaptCommand).toString(), URI("build-tools/26.0.2/aapt$exeExt").toString())
			{ cmd, expectedVal -> cmd.endsWith(expectedVal) }
		expect(URI(config.adbCommand).toString(), URI("platform-tools/adb$exeExt").toString())
			{ cmd, expectedVal -> cmd.endsWith(expectedVal) }
	}

	@Test
	fun `Pass args and build configuration`() {

		val config = ConfigurationBuilder().build(
				arrayOf("--Exploration-apksDir=apks",
						"--Selectors-randomSeed=0",
						"--Selectors-resetEvery=30",
						"--Selectors-actionLimit=50"),
				FileSystems.getDefault())

		// Default values from Configuration
		expect(50, config[actionLimit])
		expect(23, config[apiVersion])
		expect(1000, config[clearPackageRetryDelay])
		expect(0, config.randomSeed)
		expect(30, config[resetEvery])
		expect(URI("apks"), config[apksDir])
		expect(URI("./out"), config[droidmateOutputDirPath])

		// Values read from buildConstants.properties file
		expect("api_policies.txt", config.apiPoliciesFile.fileName.toString())
		expect(config.portFile.fileName.toString(),"port.tmp") { fileName, ref -> fileName.startsWith(ref) }
		expect(URI(config.aaptCommand).toString(), URI("build-tools/26.0.2/aapt$exeExt").toString())
			{ cmd, expectedVal -> cmd.endsWith(expectedVal) }
		expect(URI(config.adbCommand).toString(), URI("platform-tools/adb$exeExt").toString())
			{ cmd, expectedVal -> cmd.endsWith(expectedVal) }
	}

	@Test
	fun `Unrecognized configuration`() {
		try {
			ConfigurationBuilder().build(
					arrayOf("-apksDir=apks", "-randomSeed=0", "-resetEvery=30", "-actionsLimit=50"),
					FileSystems.getDefault())
		} catch (e: com.natpryce.konfig.Misconfiguration) {
			return
		}
		fail("Expected com.natpryce.konfig.Misconfiguration exception")
	}

}
