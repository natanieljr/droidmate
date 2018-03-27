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

package org.droidmate.tests.android_sdk

import org.droidmate.android_sdk.AaptWrapper
import org.droidmate.configuration.Configuration
import org.droidmate.misc.SysCmdExecutor
import org.droidmate.test_tools.ApkFixtures
import org.droidmate.test_tools.DroidmateTestCase
import org.droidmate.tests.fixture_aaptBadgingDump
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.nio.file.Path
import java.nio.file.Paths

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class AaptWrapperTest : DroidmateTestCase() {
	companion object {
		//region Helper methods
		@JvmStatic
		val newAaptBadgingDump: (Path) -> String = {
			val aaptBadgingDump = fixture_aaptBadgingDump
			assert(aaptBadgingDump.contains("package: name='com.box.android'"))
			assert(aaptBadgingDump.contains("launchable-activity: name='com.box.android.activities.SplashScreenActivity'"))
			aaptBadgingDump
		}

		@JvmStatic
		private val expectedLaunchableActivityName: String = "com.box.android/com.box.android.activities.SplashScreenActivity"

		//endregion Helper methods
	}

	@Test
	fun `Gets launchable activity component name from badging dump`() {
		val aaptBadgingDump = newAaptBadgingDump(Paths.get("."))

		// Act
		val launchableActivityName = AaptWrapper.tryGetLaunchableActivityComponentNameFromBadgingDump(aaptBadgingDump)

		assert(launchableActivityName == expectedLaunchableActivityName)
	}

	/*@Test
	fun `Gets launchable activity component name`() {

		val sut = AaptWrapper(Configuration.getDefault(), SysCmdExecutor())
		sut.aaptDumpBadgingInstr = newAaptBadgingDump

		val ignoredApk = ApkFixtures.build().monitoredInlined_api23

		// Act
		val launchableActivityName = sut.getLaunchableActivityComponentName(Paths.get(ignoredApk.absolutePath))

		assert(launchableActivityName == expectedLaunchableActivityName)
	}*/
}
