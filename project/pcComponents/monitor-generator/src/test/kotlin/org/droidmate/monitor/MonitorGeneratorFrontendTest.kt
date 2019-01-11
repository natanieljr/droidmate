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

package org.droidmate.monitor

import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.text
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

import java.nio.file.Files
import java.nio.file.Paths

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class MonitorGeneratorFrontendTest {
	/**
	 * Running this test as a part of a regression test suite is redundant, full rebuild will run the monitor-generator anyway.
	 *
	 * Use this test when working on the class.
	 */
	@Test
	fun `Generates DroidMate monitor`() {
		val actualMonitorJava = Paths.get(EnvironmentConstants.monitor_generator_output_relative_path_api23)
		assert(Files.notExists(actualMonitorJava) || Files.isWritable(actualMonitorJava))

		MonitorGeneratorFrontend.handleException = { throw it }

		// Act
		MonitorGeneratorFrontend.main(arrayOf("api23"))

		assert(Files.isRegularFile(actualMonitorJava))
		val actualText = actualMonitorJava.text
		assert(!actualText.contains("public class MonitorJavaTemplate"))
		assert(actualText.contains("public class Monitor"))
	}
}

