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

package org.droidmate.explorationModel.interaction

import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.EmptyAction
import org.droidmate.deviceInterface.exploration.ExplorationAction
import java.io.Serializable
import java.time.LocalDateTime

/**
 * Interface for a eContext record which stores the performed action, alongside the GUI state before the action
 *
 * this should be only used for state model instantiation and not for exploration strategies
 *
 * @param action ExplorationAction which was sent (by the ExplorationStrategy) to DroidMate
 * @param startTimestamp Time the action selection started (used to sync logcat)
 * @param endTimestamp Time the action selection started (used to sync logcat)
 * @param deviceLogs APIs triggered by this action
 * @param guiSnapshot Device snapshot after executing the action
 * @param exception Exception during execution which crashed the action (if any), or MissingDeviceException (otherwise)
 * @param screenshot Path to the screenshot (taken after the action was executed)
 *expl
 * @author Nataniel P. Borges Jr.
 */
class ActionResult(val action: ExplorationAction,
                        val startTimestamp: LocalDateTime,
                        val endTimestamp: LocalDateTime,
                        val deviceLogs: List<DeviceLog> = emptyList(),
                        val guiSnapshot: DeviceResponse = DeviceResponse.empty,
                        val screenshot: ByteArray = ByteArray(0),
                        val exception: String = "") : Serializable {
	companion object {
		private const val serialVersionUID: Long = 1
	}

	/**
	 * Identifies if the action was successful or crashed
	 */
	val successful: Boolean
		get() = exception == "N/A (no device exception available)"  //fixme this should probably be a const in common lib

}
val EmptyActionResult = ActionResult(EmptyAction, LocalDateTime.MIN, LocalDateTime.MIN)
