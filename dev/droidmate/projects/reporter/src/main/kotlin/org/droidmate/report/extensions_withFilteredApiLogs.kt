// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
package org.droidmate.report

import org.droidmate.exploration.actions.ExplorationActionRunResult
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.droidmate.exploration.actions.RunnableExplorationActionWithResult
import org.droidmate.exploration.data_aggregators.ExplorationLog
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.exploration.device.IDeviceLogs
import java.net.URI

// WISH use instead lazy extension property implemented with workaround: https://youtrack.jetbrains.com/issue/KT-13053#comment=27-1510399
val List<IExplorationLog>.withFilteredApiLogs: List<IExplorationLog>
    get() {

        fun filterApiLogs(output: IExplorationLog): IExplorationLog {

        fun filterApiLogs(results: List<RunnableExplorationActionWithResult>): MutableList<RunnableExplorationActionWithResult> {

            fun filterApiLogs(result: IExplorationActionRunResult): IExplorationActionRunResult {

                fun filterApiLogs(deviceLogs: IDeviceLogs): IDeviceLogs = FilteredDeviceLogs(deviceLogs.apiLogs)

                return ExplorationActionRunResult(
                        result.successful,
                        result.exploredAppPackageName,
                        filterApiLogs(result.deviceLogs),
                        result.guiSnapshot,
                        result.exception,
                        URI.create("file://."))
            }

            return results.map { RunnableExplorationActionWithResult(it.first, filterApiLogs(it.second)) }.toMutableList()
        }

            return ExplorationLog(output.apk,
                filterApiLogs(output.actRes),
                output.explorationStartTime,
                output.explorationEndTime)
                .apply { exception = output.exception }
    }

    return this.map { filterApiLogs(it) }
}

