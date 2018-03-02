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

import org.droidmate.android_sdk.IApk
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.exploration.actions.ExplorationAction

/**
 * Interface for a strategy pool which stores a list of exploration strategies and, for each new GUI state,
 * selects the strategy with best fitness to perform an action
 *
 * @author Nataniel P. Borges Jr.
 */
interface IStrategyPool : IControlObserver {
    /**
     * Registers a new exploration strategy in the pool if it is not already there.
     *
     * @return If the strategy has been successfully registered
     */
    fun registerStrategy(strategy: ISelectableExplorationStrategy): Boolean

    /**
     * Resets the internal memory and notifies all strategies of the memory change
     */
    fun resetMemory(apk: IApk)

    /**
     * Removes all internal strategies from the pool
     */
    fun clear()

    /**
     * Number of strategies stored in the pool
     */
    val size: Int

    /**
     * Internal memory which is shared with all internal strategies
     */
    var memory: Memory
}
