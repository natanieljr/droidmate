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

import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.device.datatypes.IWidget
import org.droidmate.exploration.actions.ExplorationRecord
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.misc.setByUniqueString
import org.droidmate.misc.uniqueString
import org.droidmate.report.EventApiPair

val IExplorationLog.uniqueActionableWidgets: Set<IWidget>
  get() = this.logRecords.setByUniqueString(
          extractItems = ExplorationRecord::actionableWidgets,
          uniqueString = IWidget::uniqueString
  )

val IExplorationLog.uniqueClickedWidgets: Set<IWidget>
  get() = this.logRecords.setByUniqueString(
          extractItems = ExplorationRecord::clickedWidget,
          uniqueString = IWidget::uniqueString
  )

val IExplorationLog.uniqueApis: Set<IApiLogcatMessage>
  get() = this.logRecords.setByUniqueString(
          extractItems = { it.getResult().deviceLogs.apiLogs },
    uniqueString = { it.uniqueString } 
  )

val IExplorationLog.uniqueEventApiPairs: Set<EventApiPair>
  get() = this.logRecords.setByUniqueString(
          extractItems = ExplorationRecord::extractEventApiPairs,
    uniqueString = { it.uniqueString }
  )

val IExplorationLog.resetActionsCount: Int
  get() = actions.count { it.base is ResetAppExplorationAction }

val IExplorationLog.apkFileNameWithUnderscoresForDots: String
  get() = apk.fileName.replace(".", "_")