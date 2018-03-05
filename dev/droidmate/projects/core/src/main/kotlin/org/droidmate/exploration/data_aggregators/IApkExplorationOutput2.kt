// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
package org.droidmate.exploration.data_aggregators

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IApk
import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.errors.DroidmateError
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.exploration.actions.RunnableExplorationActionWithResult
import org.droidmate.storage.IStorage2
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime

interface IApkExplorationOutput2 : Serializable {

    fun add(action: IRunnableExplorationAction, result: IExplorationActionRunResult)

    var explorationStartTime: LocalDateTime

    var explorationEndTime: LocalDateTime

    val actRes: MutableList<RunnableExplorationActionWithResult>

    @Throws(DroidmateError::class)
    fun verify()

    val exceptionIsPresent: Boolean

    var exception: DeviceException

    val apk: IApk

    val packageName: String

    val apiLogs: List<List<IApiLogcatMessage>>

    val actions: List<IRunnableExplorationAction>

    val guiSnapshots: List<IDeviceGuiSnapshot>

    fun getExplorationTimeInMs(): Int

    fun getExplorationDuration(): Duration

    fun serialize(storage2: IStorage2)
}