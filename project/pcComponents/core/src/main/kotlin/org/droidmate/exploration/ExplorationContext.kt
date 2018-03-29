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
package org.droidmate.exploration

import kotlinx.coroutines.experimental.launch
import org.droidmate.device.android_sdk.IApk
import org.droidmate.exploration.statemodel.*
import org.droidmate.exploration.statemodel.features.ModelFeature
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.exploration.statemodel.config.ModelConfig
import java.awt.Rectangle
import java.time.LocalDateTime
import java.util.*

class ExplorationContext @JvmOverloads constructor(override val apk: IApk,
                                                   override var explorationStartTime: LocalDateTime = LocalDateTime.MIN,
                                                   override var explorationEndTime: LocalDateTime = LocalDateTime.MIN,
                                                   override val watcher: LinkedList<ModelFeature> = LinkedList(),
                                                   override val model: Model = Model.emptyModel(ModelConfig(apk.packageName)),
		                                               override val actionTrace: Trace = model.initNewTrace(watcher)
) : AbstractContext() {

	override var deviceDisplayBounds: Rectangle? = null

	init {
		if (explorationStartTime > LocalDateTime.MIN)
			this.verify()
	}

	companion object {
		private const val serialVersionUID: Long = 1
	}

	override fun getCurrentState(): StateData = actionTrace.currentState

	override fun belongsToApp(state: StateData): Boolean {
		return state.topNodePackageName == apk.packageName
	}

	override fun add(action: IRunnableExplorationAction, result: ActionResult) {
		deviceDisplayBounds = Rectangle(result.guiSnapshot.deviceDisplayWidth, result.guiSnapshot.deviceDisplayHeight)
		lastDump = result.guiSnapshot.windowHierarchyDump

		model.S_updateModel(result, actionTrace)
		this.also { context -> watcher.forEach { launch(it.context, parent = it.job) { it.onContextUpdate(context) } } }
	}

	override fun dump() {
		model.P_dumpModel(model.config)
		this.also { context -> watcher.forEach { launch(it.context, parent = it.job) { it.dump(context) } } }
	}

	override fun areAllWidgetsExplored(): Boolean {
		return actionTrace.unexplored(getCurrentState().actionableWidgets).isNotEmpty()
	}

	override fun assertLastGuiSnapshotIsHomeOrResultIsFailure() {
		actionTrace.last()?.let {
			assert(!it.successful || getCurrentState().isHomeScreen)
		}
	}

}