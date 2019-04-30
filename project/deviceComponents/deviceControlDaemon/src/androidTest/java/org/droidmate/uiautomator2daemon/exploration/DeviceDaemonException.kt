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

package org.droidmate.uiautomator2daemon.exploration

import org.droidmate.deviceInterface.exploration.DeviceResponse

@Suppress("unused")
class DeviceDaemonException : Throwable {

	constructor() : super()

	constructor(arg0: String, arg1: Throwable) : super(arg0, arg1)

	constructor(arg0: String) : super(arg0)

	constructor(arg0: Throwable) : super(arg0)

}

private val error = DeviceResponse.create(isSuccessful = false, capturedScreen = false,
	appWindows = emptyList(), isHomeScreen = false, uiHierarchy = emptyList(), launchedActivity = "",
	screenshot = ByteArray(0), uiDump = "ERROR in device controlling app")
val ErrorResponse:(Throwable?) ->DeviceResponse = {error.apply { throwable = it }}
