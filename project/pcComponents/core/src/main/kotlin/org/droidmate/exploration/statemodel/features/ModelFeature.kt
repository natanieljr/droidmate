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
package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.Job
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.ExplorationContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.experimental.CoroutineContext

/**
 * this class contains three different observer methods to keep track of model changes.
 * the Feature should try to follow the least principle policy and prefer [onNewInteracted] over the alternatives
 */
@Suppress("unused", "UNUSED_ANONYMOUS_PARAMETER")
abstract class ModelFeature {
	companion object {
		@JvmStatic
		val log: Logger = LoggerFactory.getLogger(ModelFeature::class.java)
	}

	/** used in the strategy to ensure that the updating coroutine function already finished.
	 * calling job.joinChildren() will wait for all currently running [onNewAction] and [update] instances to complete*/
	var job = Job()

	/** the context in which the update tasks of the class are going to be started,
	 * for performance reasons they should run within the same pool for each feature
	 * e.g. you can use `newSingleThreadContext("MyOwnThread")` to ensure that your update methods get its own thread*/
	abstract val context: CoroutineContext

	/** this is called after the model was completely updated with the new action and state
	 * this method gives access to the complete [context] inclusive other ModelFeatures */
	open suspend fun onContextUpdate(context: ExplorationContext) { /* do nothing [to be overwritten] */
	}

	/** called whenever a new [targetWidget] was executed on the device resulting in [newState]
	 * this function may be used instead of update for simpler access to the action and result state
	 **/
	open suspend fun onNewInteracted(targetWidget: Widget?, prevState: StateData, newState: StateData) { /* do nothing [to be overwritten] */
	}

	/** called whenever a new action was executed on the device resulting in [newState]
	 * this function may be used instead of update for simpler access to the action and result state.
	 *
	 * If possible the use of [onNewInteracted] should be preferred instead, since the action computation may introduce an additional delay to this computation. Meanwhile [onNewInteracted] is directly ready to run.*/
	open suspend fun onNewAction(lazyAction: Lazy<ActionData>, prevState: StateData, newState: StateData) { /* do nothing [to be overwritten] */
	}

	/** this method is called on each call to [ExplorationContext].dump() */
	open suspend fun dump(context: ExplorationContext) {  /* do nothing [to be overwritten] */
	}
}