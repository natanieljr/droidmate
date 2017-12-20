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

package org.droidmate.device.datatypes

import org.droidmate.errors.ForbiddenOperationError
import java.io.Serializable

class MissingGuiSnapshot : IDeviceGuiSnapshot, Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    private val internalGuiState = EmptyGuiState("EMPTY")

    override val androidLauncherPackageName: String
        get() = internalGuiState.androidLauncherPackageName

    override val windowHierarchyDump: String
        get() = throw ForbiddenOperationError()

    override fun getPackageName(): String {
        return internalGuiState.topNodePackageName
    }

    override val guiState: IGuiState
        get() = internalGuiState

    override val validationResult: ValidationResult
        get() = throw ForbiddenOperationError()

    override val id: String
        get() = throw ForbiddenOperationError()

    override fun toString(): String = "N/A (lack of ${IDeviceGuiSnapshot::class.java.simpleName})"
}