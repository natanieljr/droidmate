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

import org.droidmate.device.datatypes.statemodel.ActionResult
import org.droidmate.exploration.actions.ExplorationRecord
import org.droidmate.exploration.data_aggregators.ExplorationContext
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.exploration.device.IDeviceLogs

// WISH use instead lazy extension property implemented with workaround: https://youtrack.jetbrains.com/issue/KT-13053#comment=27-1510399
val List<IExplorationLog>.withFilteredApiLogs: List<IExplorationLog>
    get() {
TODO("no idea what's the intention of this method")
        /*
        fun filterApiLogs(output: IExplorationLog): IExplorationLog {

            fun filterApiLogs(results: List<ExplorationRecord>): MutableList<ExplorationRecord> {

                fun filterApiLogs(result: org.droidmate.exploration.strategy.ActionResult): org.droidmate.exploration.strategy.ActionResult {

                fun filterApiLogs(deviceLogs: IDeviceLogs): IDeviceLogs = FilteredDeviceLogs(deviceLogs.apiLogs)

                    return ActionResult(
                        result.action,
                        result.startTimestamp,
                        result.endTimestamp,
                        filterApiLogs(result.deviceLogs),
                        result.guiSnapshot,
                        result.exception,
                        result.screenshot
                    )
            }

                return results.map { ExplorationRecord(it.first, filterApiLogs(it.second)) }.toMutableList()
        }

            return ExplorationContext(output.apk,
                    filterApiLogs(output.logRecords),
                output.explorationStartTime,
                output.explorationEndTime)
                .apply { exception = output.exception }
    }

    return this.map { filterApiLogs(it) }
    */
}

