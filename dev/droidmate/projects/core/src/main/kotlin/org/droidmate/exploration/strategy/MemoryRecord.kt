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
package org.droidmate.exploration.strategy

import com.google.common.base.MoreObjects
import org.droidmate.android_sdk.DeviceException
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.device.datatypes.MissingGuiSnapshot
import org.droidmate.exploration.actions.DeviceExceptionMissing
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.device.IDeviceLogs
import org.droidmate.exploration.device.MissingDeviceLogs
import java.net.URI
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Performed action record to be consumed by exploration strategies if necessary
 */
class MemoryRecord(override val action: ExplorationAction,
                   override val startTimestamp: LocalDateTime,
                   override val endTimestamp: LocalDateTime,
                   override val deviceLogs: IDeviceLogs = MissingDeviceLogs(),
                   override val guiSnapshot: IDeviceGuiSnapshot = MissingGuiSnapshot(),
                   override val exception: DeviceException = DeviceExceptionMissing(),
                   override val screenshot: URI = emptyURI) : IMemoryRecord {

    companion object {
        private const val serialVersionUID: Long = 1
        private val emptyURI = URI.create("test://empty")
    }

    override var widgetContext: WidgetContext = EmptyWidgetContext()

    override val decisionTime: Long
        get() = ChronoUnit.MILLIS.between(startTimestamp, endTimestamp)

    override val successful: Boolean
        get() = exception is DeviceExceptionMissing

    override val guiState: IGuiState
        get() = guiSnapshot.guiState

    override val appPackageName: String
        get() = guiSnapshot.getPackageName()

    override val hasScreenshot: Boolean
        get() = this.screenshot != emptyURI

    override fun equals(other: Any?): Boolean {
        if (other !is MemoryRecord)
            return false

        return this.action.toString() == other.action.toString()
        // TODO Add check on exploration state as well
    }

    override fun hashCode(): Int {
        return this.action.hashCode()
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
                .add("action", action)
                .add("successful", successful)
                .add("snapshot", guiSnapshot)
                .addValue(deviceLogs)
                .add("exception", exception)
                .toString()
    }
}
