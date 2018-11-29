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

import junit.framework.TestCase
import org.droidmate.logging.LogbackUtilsRequiringLogbackLog
import org.droidmate.misc.BuildConstants
import org.junit.Before
import java.util.*

open class DroidmateTestCase : TestCase() {

	fun<T> expect(res:T, ref: T){
		val refSplit = ref.toString().split(";")
		val diff = res.toString().split(";").mapIndexed { index, s ->
			if(refSplit.size>index) s.replace(refSplit[index],"#CORRECT#")
			else s
		}
		TestCase.assertTrue("expected \n${ref.toString()} \nbut result was \n${res.toString()}\n DIFF = $diff", res == ref)
	}
	/*
		Used for profiling the JUnit test runs with VisualVM. Uncomment, run the tests with -Xverify:none JVM option and make sure
		that in those 5 seconds you will select the process in VisualVM, click the "profiler" tab and start CPU profiling.
		For more, see Konrad's OneNote / Reference / Technical / Java / Profiling.

	 */
//  static {
//    println "Waiting for profiler for 5 seconds"
//    Thread.sleep(5000)
//    println "Done waiting!"
//  }
	companion object {
		init {
			Locale.setDefault(BuildConstants.locale)
		}
	}

	@Before
	public override fun setUp() {
		LogbackUtilsRequiringLogbackLog.cleanLogsDir()
	}
}
