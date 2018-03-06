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
package org.droidmate.exploration.strategy

import org.droidmate.android_sdk.DeviceException
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.exploration.actions.ActionType
import org.droidmate.exploration.actions.DeviceExceptionMissing
import org.droidmate.exploration.actions.EmptyAction
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.device.DeviceLogs
import org.droidmate.exploration.device.IDeviceLogs
import java.net.URI
import java.time.LocalDateTime

class EmptyMemoryRecord : IMemoryRecord {
    override val action: ExplorationAction = EmptyAction()

    override var widgetContext: WidgetContext = EmptyWidgetContext()

    override val guiSnapshot: IDeviceGuiSnapshot = EmptyGUISnapshot()

    override val exception: DeviceException = DeviceExceptionMissing()

    override val screenshot: URI = URI(this.javaClass.name)

    override val type: ActionType
        get() = ActionType.None

    override val decisionTime: Long
        get() = Long.MIN_VALUE

    override val startTimestamp: LocalDateTime
        get() = LocalDateTime.MIN

    override val endTimestamp: LocalDateTime
        get() = LocalDateTime.MIN

    override val deviceLogs: IDeviceLogs
        get() = DeviceLogs()

    override val guiState: IGuiState
        get() = guiSnapshot.guiState

    override val appPackageName: String
        get() = guiSnapshot.getPackageName()

    override val successful: Boolean
        get() = true
}