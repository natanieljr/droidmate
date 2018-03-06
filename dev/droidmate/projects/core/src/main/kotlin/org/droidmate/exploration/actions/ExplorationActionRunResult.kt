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
package org.droidmate.exploration.actions

/*class ExplorationActionRunResult @JvmOverloads constructor(override val successful: Boolean,
                                                           override val exploredAppPackageName: String,
                                                           override val deviceLogs: IDeviceLogs,
                                                           override val guiSnapshot: IDeviceGuiSnapshot,
                                                           override val exception: DeviceException,
                                                           override val screenshot: URI,
                                                           missingSnapshot: Boolean = false) : IExplorationActionRunResult {

    companion object {
        private const val serialVersionUID: Long = 1

    }

    init {
        assert(exploredAppPackageName.isNotEmpty())

        assert(!successful || this.deviceLogs !is MissingDeviceLogs)

        if (!missingSnapshot)
            assert(!successful || this.guiSnapshot !is MissingGuiSnapshot)
        assert(successful == (this.exception is DeviceExceptionMissing))
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
                .add("successful", successful)
                .add("snapshot", guiSnapshot)
                .addValue(deviceLogs)
                .add("exception", exception)
                .toString()
    }
}*/