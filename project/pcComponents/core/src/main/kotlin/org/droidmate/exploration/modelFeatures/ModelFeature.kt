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

package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.experimental.Job
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.ModelFeatureI
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * this class contains three different observer methods to keep track of model changes.
 * the Feature should try to follow the least principle policy and prefer [onNewInteracted] over the alternatives
 */
@Suppress("unused", "UNUSED_ANONYMOUS_PARAMETER")
abstract class ModelFeature: ModelFeatureI() {
	companion object {
		@JvmStatic
		val log: Logger by lazy { LoggerFactory.getLogger(ModelFeature::class.java) }
		/** dump and onAppExplorationFinished are waiting for other job's completion, therefore they need their own independent job,
		 * the eContext.dump and eContext.close wait for the children of this job */
		@JvmStatic val auxiliaryJob = Job()
	}

	/** this is called after the model was completely updated with the new action and state
	 * this method gives access to the complete [context] inclusive other ModelFeatures
	 *
	 * WARNING: this method is not triggered when loading an already existing model
	 */
	open suspend fun onContextUpdate(context: ExplorationContext) { /* do nothing [to be overwritten] */
	}


	// TODO check if an additional method with (targets,actions:ExplorationAction) would prove usefull


	/** this method is called on each call to [ExplorationContext].close(), executed after [ModelFeature].dump()
	 * this method should call `job.joinChildren()` to ensure all updates have been applied before restarting the feature state
	 */
	open suspend fun onAppExplorationFinished(context: ExplorationContext) {  /* do nothing [to be overwritten] */
	}

	/** this method is called on each call to [ExplorationContext].dump()
	 * this method should call `job.joinChildren()` to wait for all updates to be applied before persisting the modelFeatures state
	 */
	open suspend fun dump(context: ExplorationContext) {  /* do nothing [to be overwritten] */
	}
}