// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.report.misc

import org.droidmate.device.datatypes.IWidget
import org.droidmate.device.datatypes.MissingGuiSnapshot
import org.droidmate.exploration.actions.EnterTextExplorationAction
import org.droidmate.exploration.actions.ExplorationRecord
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.report.EventApiPair

val ExplorationRecord.clickedWidget: Set<IWidget>
    get() {
        val action = this.getAction().base
        return when (action) {
            is WidgetExplorationAction -> setOf(action.widget)
            is EnterTextExplorationAction -> setOf(action.widget)
            else -> emptySet()
        }
    }

val ExplorationRecord.actionableWidgets: Iterable<IWidget>
  get() {
      val result = getResult()
      return when (result.guiSnapshot) {
          is MissingGuiSnapshot -> this.clickedWidget
          else -> {
              if (!(result.guiSnapshot.guiState.belongsToApp(result.appPackageName)))
                  return this.clickedWidget
              else
                  return result.guiSnapshot.guiState.getActionableWidgets().union(this.clickedWidget)
          }
      }
  }

fun ExplorationRecord.extractEventApiPairs(): List<EventApiPair> =
        this.getResult().deviceLogs.apiLogs.map { apiLog -> EventApiPair(this, apiLog) }
