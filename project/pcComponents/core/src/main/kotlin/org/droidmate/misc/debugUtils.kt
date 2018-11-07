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

@file:Suppress("unused")

package org.droidmate.misc

import kotlin.system.measureNanoTime

// TODO we would like to read this property from the DroidMate.Configuration instead
var measurePerformance = true
var debugOutput = true

inline fun <T> nullableDebugT(msg: String, block: () -> T?, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T? {
	var res: T? = null
	@Suppress("ConstantConditionIf")
	if (measurePerformance) {
		measureNanoTime {
			res = block.invoke()
		}.let {
			timer(it)
			println("time ${if (inMillis) "${it / 1000000.0} ms" else "${it / 1000.0} ns/1000"} \t $msg")
		}
	} else res = block.invoke()
	return res
}

inline fun <T> debugT(msg: String, block: () -> T, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T {
	return nullableDebugT(msg, block, timer, inMillis)!!
}
