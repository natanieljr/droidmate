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

package org.droidmate.uiautomator_daemon

import org.droidmate.uiautomator_daemon.guimodel.Action

import java.io.Serializable

class DeviceCommand @JvmOverloads constructor(var command: String, var guiAction: Action? = null) : Serializable {

	override fun equals(other: Any?): Boolean {
		if (other !is DeviceCommand) return false
		if (this === other) return true

		return command == other.command &&
				if (guiAction != null) guiAction == other.guiAction else other.guiAction == null

	}

	override fun hashCode(): Int {
		var result = command.hashCode()
		result = 31 * result + if (guiAction != null) guiAction!!.hashCode() else 0
		return result
	}

	override fun toString(): String {
		return "DeviceCommand{" +
				"command='" + command + '\''.toString() +
				", guiAction=" + guiAction +
				'}'.toString()
	}

	companion object {

		private const val serialVersionUID = 8439619323391358530L
	}
}
