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

package org.droidmate.test_tools

import com.konradjamrozik.Resource
import org.droidmate.device.android_sdk.AaptWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.device.android_sdk.IAaptWrapper
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.misc.BuildConstants
import org.droidmate.misc.SysCmdExecutor
import org.droidmate.misc.text
import java.nio.file.Path
import java.nio.file.Paths

class ApkFixtures(aapt: IAaptWrapper) {
	companion object {
		@JvmStatic
		val apkFixture_simple_packageName = "org.droidmate.fixtures.apks.simple"

		@JvmStatic
		fun build(): ApkFixtures = ApkFixtures(AaptWrapper(ConfigurationWrapper.getDefault(), SysCmdExecutor()))

	}

	val gui: Apk
	val monitoredInlined_api23: Apk

	val Resource.extractedPath: Path
		get() {
			val resDir = Paths.get("out",BuildConstants.dir_name_temp_extracted_resources)
			return this.extractTo(resDir).toAbsolutePath()
		}

	val Resource.extractedPathString: String
		get() {
			return this.extractedPath.toString()
		}

	val Resource.extractedText: String
		get() {
			return extractedPath.text
		}

	init {
		gui = Apk.build(aapt, Resource("${BuildConstants.apk_fixtures}/GuiApkFixture-debug.apk").extractedPath)
		monitoredInlined_api23 = Apk.build(aapt, Resource("${BuildConstants.apk_fixtures}/${BuildConstants.monitored_inlined_apk_fixture_api23_name}").extractedPath)
	}
}
