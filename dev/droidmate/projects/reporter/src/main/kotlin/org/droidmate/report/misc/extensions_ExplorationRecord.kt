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
package org.droidmate.report.misc

import org.droidmate.device.datatypes.statemodel.Widget
import org.droidmate.exploration.actions.EnterTextExplorationAction
import org.droidmate.exploration.actions.ExplorationRecord
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.report.EventApiPair
import org.droidmate.uiautomator_daemon.GuiStatusResponse

val ExplorationRecord.clickedWidget: Set<Widget>
    get() {
        val action = this.getAction().base
        return when (action) {
            is WidgetExplorationAction -> setOf(action.widget)
            is EnterTextExplorationAction -> setOf(action.widget)
            else -> emptySet()
        }
    }

val ExplorationRecord.actionableWidgets: Iterable<Widget>
  get() {
      val result = getResult()
      return when {
          result.guiSnapshot.equals(GuiStatusResponse.empty) -> this.clickedWidget
          else -> {
              if (!(result.guiSnapshot.belongsToApp(result.guiSnapshot.topNodePackageName)))
                  return this.clickedWidget
              else
                  TODO("this is no longer supported, use the State to determine actionable widgets instead")
//                  return result.guiSnapshot.guiStatus.getActionableWidgets().union(this.clickedWidget)
          }
      }
  }

fun ExplorationRecord.extractEventApiPairs(): List<EventApiPair> =
        this.getResult().deviceLogs.apiLogs.map { apiLog -> EventApiPair(this, apiLog) }
