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

import kotlinx.coroutines.experimental.Job
import org.droidmate.device.datatypes.statemodel.ActionData
import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.exploration.data_aggregators.ExplorationContext
import kotlin.coroutines.experimental.CoroutineContext

@Suppress("unused")
abstract class ModelFeature {
	/** used in the strategy to ensure that the updating coroutine function already finished.
	 * calling job.joinChildren() will wait for all currently running [onNewAction] and [update] instances to complete*/
	var job = Job()

	/** the context in which the update tasks of the class are going to be started,
	 * for performance reasons they should run within the same pool for each feature */
	abstract val context:CoroutineContext

  /** this is called after the model was completely updated with the new action and state
   * this method gives access to the complete [context] inclusive other ModelFeatures */
  open suspend fun update(context: ExplorationContext) { /* do nothing */ }

  /** called whenever a new [action] was executed on the device resulting in [state]
   * this function may be used instead of update for simpler access to the action and result state*/
  open suspend fun onNewAction(action: ActionData, prevState:StateData, newState:StateData): Unit { /* do nothing */ }

  open suspend fun dump(context: ExplorationContext) { /* do nothing */ }
}