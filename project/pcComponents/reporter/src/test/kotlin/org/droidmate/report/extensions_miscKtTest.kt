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
package org.droidmate.report

import org.droidmate.misc.replaceVariable
import org.droidmate.misc.zeroLeastSignificantDigits
import org.junit.Test
import kotlin.test.assertEquals

class extensions_miscKtTest {

	@Test
	fun zeroDigitsTest() {
		assertEquals(1299.zeroLeastSignificantDigits(2), 1200L)
	}

	@Test
	fun replaceVariableTest() {
		assertEquals(
				StringBuilder(
						"Value of var_1 is \$var_1, value of xyz is \$xyz, and again, \$var_1 is the value of var_1.")
						.replaceVariable("var_1", "777")
						.replaceVariable("xyz", "magic")
						.toString(),
				"Value of var_1 is 777, value of xyz is magic, and again, 777 is the value of var_1."
				)
	}
}

