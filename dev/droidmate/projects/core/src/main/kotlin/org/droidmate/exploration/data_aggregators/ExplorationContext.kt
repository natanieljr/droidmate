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
package org.droidmate.exploration.data_aggregators

import kotlinx.coroutines.experimental.launch
import org.droidmate.android_sdk.IApk
import org.droidmate.device.datatypes.statemodel.*
import org.droidmate.device.datatypes.statemodel.features.ActionCounterMF
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.device.datatypes.statemodel.features.IModelFeature
import java.awt.Rectangle
import java.time.LocalDateTime

class ExplorationContext @JvmOverloads constructor(override val apk: IApk,
                                                   override val actionTrace: Trace = Trace(),
                                                   override var explorationStartTime: LocalDateTime = LocalDateTime.MIN,
                                                   override var explorationEndTime: LocalDateTime = LocalDateTime.MIN,
                                                   override val watcher:List<IModelFeature> = listOf(ActionCounterMF())) : IExplorationLog() {
	override val model: Model = Model.emptyModel(ModelDumpConfig(apk.packageName))

	private var lastState = StateData.emptyState()
	private var prevState = StateData.emptyState()

	override var deviceDisplayBounds: Rectangle? = null

	fun getPreviousState():StateData = prevState
	override fun getCurrentState(): StateData = lastState

	override fun dump() {
		model.P_dumpModel(model.config)
	}

	companion object {
		private const val serialVersionUID: Long = 1
	}

	init {
		if (explorationStartTime > LocalDateTime.MIN)
			this.verify()
	}

	override fun add(action: IRunnableExplorationAction, result: ActionResult) {
		deviceDisplayBounds = result.guiSnapshot.guiStatus.deviceDisplayBounds

		prevState = lastState // TODO refactor as model.update
		lastState = result.resultState(model.config).also { launch { it.dump(model.config) } }
		model.addState(lastState)
		lastState.widgets.forEach { model.addWidget(it) }
		actionTrace.apply {
			addAction(ActionData(result, lastState.stateId, getLastAction().resState))
			launch { dump(model.config) }
		}
		this.also { context -> watcher.forEach { launch { it.update(context) } } }
	}

	override fun areAllWidgetsExplored(): Boolean {
//        return (!this.isEmpty()) &&
//                this.foundWidgetContexts.isNotEmpty() &&
//                this.foundWidgetContexts.all { context ->
//                    context.actionableWidgetsInfo.all { it.actedUponCount > 0 }
//                }
		return false // TODO() meta information of widget.uid's which were not yet interacted
	}

	override fun assertLastGuiSnapshotIsHomeOrResultIsFailure() {
		actionTrace.last()?.let {
			assert(!it.successful || getCurrentState().isHomeScreen)
		}
	}

}