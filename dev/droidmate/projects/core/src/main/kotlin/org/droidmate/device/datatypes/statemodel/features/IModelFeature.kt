// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Jenny Hotzkow
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
package org.droidmate.device.datatypes.statemodel.features

import kotlinx.coroutines.experimental.Deferred
import org.droidmate.device.datatypes.statemodel.ActionData
import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.exploration.data_aggregators.ExplorationContext

@Suppress("unused")
interface IModelFeature {

	/** use this if your strategy needs to ensure that the [onNewAction] function already finished. This value will be automatically set for the invocation of [onNewAction] */
	var actionTask: Deferred<Unit>?
	/** like [actionTask] but for the method [update] */
	var updateTask: Deferred<Unit>?

  /** this is called after the model was completely updated with the new action and state
   * this method gives access to the complete [context] inclusive other ModelFeatures */
  suspend fun update(context: ExplorationContext)

  /** called whenever a new [action] was executed on the device resulting in [state]
   * this function may be used instead of update for simpler access to the action and result state*/
  suspend fun onNewAction(action: ActionData, prevState:StateData, newState:StateData)

  suspend fun dump(context: ExplorationContext)
}