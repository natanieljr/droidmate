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

/*package org.droidmate.apk_inliner

import com.konradjamrozik.ResourcePath
import org.apache.commons.io.FileUtils
import org.droidmate.misc.EnvironmentConstants
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.nio.file.Files
import java.nio.file.Paths

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ApkInlinerFrontendTest {

	/**
	 * <p>
	 * The test check if apk inliner successfully inlines an apk without throwing an exception. It doesn't check if the inlined
	 * functionality works as expected. For that, please refer to tests using {@code org.droidmate.test.MonitoredInlinedApkFixture}.
	 *
	 * </p>
	 */
	@Test
	fun inlineApk() {
		val inputApkFixturesDir = ResourcePath(EnvironmentConstants.apk_fixtures).path
		assert(Files.isDirectory(inputApkFixturesDir))
		assert(Files.list(inputApkFixturesDir).count() == 1L)

		val inputApkFixture = Files.list(inputApkFixturesDir).findFirst()
		assert(inputApkFixture.isPresent)
		assert(inputApkFixture.get().fileName.toString() == "com.estrongs.android.taskmanager.apk")

		val inputDir = Paths.get("tmp-test-toremove_input-apks")
		val outputDir = Paths.get("tmp-test-toremove_output-apks")
		FileUtils.deleteDirectory(inputDir.toFile())
		FileUtils.deleteDirectory(outputDir.toFile())
		Files.createDirectory(inputDir)
		Files.createDirectory(outputDir)

		Files.copy(inputApkFixture.get(), inputDir.resolve(inputApkFixture.get().fileName))

		ApkInlinerFrontend.handleException = { e -> throw e }
		// Act
		ApkInlinerFrontend.main(arrayOf(EnvironmentConstants.apk_inliner_param_input,
				inputDir.toAbsolutePath().toString(),
				EnvironmentConstants.apk_inliner_param_output_dir,
				outputDir.toAbsolutePath().toString()))

		assert(Files.list(outputDir).count() == 1L)
		assert(Files.list(outputDir).findFirst().isPresent)
	}
}*/