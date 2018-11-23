// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
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
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.explorationModel

import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * this class contains three different observer methods to keep track of model changes.
 * the Feature should try to follow the least principle policy and prefer [onNewInteracted] over the alternatives
 * onAppExplorationFinished are calling cancel and join so your function should override it if any data is to be persisted
 */
@Suppress("unused", "UNUSED_ANONYMOUS_PARAMETER")
abstract class ModelFeatureI : CoroutineScope {

	companion object {
		@JvmStatic
		val log: Logger by lazy { LoggerFactory.getLogger(ModelFeatureI::class.java) }
	}

	/** may be used in the strategy to ensure that the updating coroutine function already finished.
	 * your ModelFeature should override this function if it requires any synchronization
	 *
	 * WARNING: this method should not be called form within the model feature (unless running in a different job), otherwise it will cause a deadlock
	 */
	open suspend fun join() = coroutineContext[Job]?.children?.forEach { it.join() }  // we do not join the parent job as it usually does not complete until this feature is canceled

	open suspend fun cancelAndJoin() { if(coroutineContext.isActive) coroutineContext[Job]?.cancelAndJoin() }

	/** the eContext in which the update tasks of the class are going to be started,
	 * for performance reasons they should run within the same pool for each feature
	 * e.g. `newCoroutineContext(context = CoroutineName("FeatureNameMF"), parent = job)`
	 * or you can use `newSingleThreadContext("MyOwnThread")` to ensure that your update methods get its own thread
	 * However, you should not use the main thread dispatcher or you may end up in deadlock situations.
	 * (Simply using the default dispatcher is fine)
	 */
	abstract override val coroutineContext: CoroutineContext

	/** called whenever an action or actionqueue was executed on [targetWidgets] the device resulting in [newState]
	 * this function may be used instead of update for simpler access to the action and result state.
	 * The [targetWidgets] belong to the actions with hasWidgetTarget = true and are in the same order as they appeared
	 * in the actionqueue.
	 **/
	open suspend fun onNewInteracted(traceId: UUID, targetWidgets: List<Widget>, prevState: State, newState: State) { /* do nothing [to be overwritten] */
	}

	/** called whenever an action or actionqueue was executed on [targetWidgets] the device resulting in [newState]
	 * this function may be used instead of update for simpler access to the action and result state.
	 * The [targetWidgets] belong to the actions with hasWidgetTarget = true and are in the same order as they appeared
	 * in the actionqueue.
	 *
	 * WARNING: this method only gets `EmptyAction` when loading an already existing model
	 **/
	open suspend fun onNewInteracted(traceId: UUID, actionIdx: Int, action: ExplorationAction,
	                                 targetWidgets: List<Widget>, prevState: State, newState: State) {
		/* do nothing [to be overwritten] */
	}

	// TODO check if an additional method with (targets,actions:ExplorationAction) would prove useful

	/** called whenever a new action was executed on the device resulting in [newState]
	 * this function may be used instead of update for simpler access to the action and result state.
	 *
	 * If possible the use of [onNewInteracted] should be preferred instead, since the action computation may introduce an additional timeout to this computation. Meanwhile [onNewInteracted] is directly ready to run.*/
	open suspend fun onNewAction(traceId: UUID, interactions: List<Interaction>, prevState: State, newState: State) { /* do nothing [to be overwritten] */
	}

	/** can be used to persist any data during Exploration whenever ExplorationContext.dump is called.
	 * The exploration never waits for this method to complete, as it is launched in an independent scope.
	 * Therefore, it is your features responsibility to guarantee that your last state is persisted, e.g. by implementing [cancelAndJoin].
	 */
	open suspend fun dump() {
	}

}