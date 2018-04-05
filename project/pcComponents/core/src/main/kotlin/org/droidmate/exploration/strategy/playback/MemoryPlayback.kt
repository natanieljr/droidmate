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
package org.droidmate.exploration.strategy.playback

import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.statemodel.*
import org.droidmate.exploration.statemodel.config.ModelConfig
import org.droidmate.exploration.statemodel.Model
import org.droidmate.exploration.strategy.widget.Explore

@Suppress("unused")
open class MemoryPlayback private constructor() : Explore() {

	private lateinit var packageName: String
	private var model: Model? = null
	var traceIdx = 0
	var actionIdx = 0

	constructor(packageName: String) : this() {
		this.packageName = packageName
		model = ModelLoader.loadModel(ModelConfig(packageName))
	}

	private fun isComplete(): Boolean {
		return model?.getPaths()?.let { traceIdx+1 == it.size && actionIdx+1 == it[traceIdx].size } ?: false
	}

	private fun getNextTraceAction(peek: Boolean = false): ActionData {
		model!!.let{
			it.getPaths()[traceIdx].let{ currentTrace ->
				if(currentTrace.size-1 == actionIdx){ // check if all actions of this trace were handled
					return it.getPaths()[traceIdx + 1].first().also {
						if(!peek) {
							traceIdx += 1
							actionIdx = 0
						}
					}
				}
				return currentTrace.getActions()[actionIdx+1].also {
					if(!peek) actionIdx += 1
				}
			}
		}
	}

	private fun StateData.similarity(other: StateData): Double {
		val otherWidgets = other.widgets
		val mappedWidgets = this.widgets.map { w ->
			if (otherWidgets.any { it.uid == w.uid })
				1
			else
				0
		}
		return mappedWidgets.sum() / this.widgets.size.toDouble()
	}

	private fun Widget?.canExecute(context: StateData, ignoreLocation: Boolean = false): Boolean {
		if( this == null ) return false
		return if (ignoreLocation)
			(!(this.text.isEmpty() && (this.resourceId.isEmpty()))) &&
					(context.widgets.any { it.uid == this.uid })
		else
			(context.widgets.any { it.uid == this.uid })
	}

	private fun getNextAction(): ExplorationAction {

		// All traces completed. Finish
		if (isComplete())
			return TerminateExplorationAction()

		val currTraceData = getNextTraceAction()
		val action = currTraceData.actionType
		when (action) {
			WidgetExplorationAction::class.simpleName -> {
				return when {
					currTraceData.targetWidget.canExecute(context.getCurrentState()) -> {
						PlaybackExplorationAction(currTraceData.targetWidget!!)
						// not found, try ignoring the location if it has text and or resourceID
					}
					currTraceData.targetWidget.canExecute(context.getCurrentState(), true) -> {
						logger.warn("Same widget not found. Located similar (text and resourceID) widget in different position. Selecting it.")
						PlaybackExplorationAction(currTraceData.targetWidget!!)
					}

				// not found, go to the next
					else -> getNextAction()
				}
			}
			TerminateExplorationAction::class.simpleName -> {
				return PlaybackTerminateAction()
			}
			ResetAppExplorationAction::class.simpleName -> {
				return PlaybackResetAction()
			}
			PressBackExplorationAction::class.simpleName -> {
				// If already in home screen, ignore
				if (context.getCurrentState().isHomeScreen)
					return getNextAction()

				val similarity = context.getCurrentState().similarity(runBlocking {model!!.getState(currTraceData.resState)!!})

				// Rule:
				// 0 - Doesn't belong to app, skip
				// 1 - Same screen, press back
				// 2 - Not same screen and can execute next widget action, stay
				// 3 - Not same screen and can't execute next widget action, press back
				// Known issues: multiple press back / reset in a row

				return if (similarity == 1.0) {
					PlaybackPressBackAction()
				} else {
					val nextTraceData = getNextTraceAction(peek = true)

						val nextWidget = nextTraceData.targetWidget

						if (nextWidget.canExecute(context.getCurrentState(), true))
							getNextAction()

					PlaybackPressBackAction()
				}
			}
			else -> {
				return WidgetExplorationAction(currTraceData.targetWidget!!, useCoordinates = true)
			}
		}
	}

	fun getExplorationRatio(widget: Widget? = null): Double {
		TODO()
//		val totalSize = traces.map { it.getSize(widget) }.sum()
//
//		return traces
//				.map { trace -> trace.getExploredRatio(widget) * (trace.getSize(widget) / totalSize.toDouble()) }
//				.sum()
	}

	override fun internalDecide(): ExplorationAction {
		val allWidgetsBlackListed = this.updateState()
		if (allWidgetsBlackListed)
			this.notifyAllWidgetsBlacklisted()

		return chooseAction()
	}

	override fun chooseAction(): ExplorationAction {
		return getNextAction()
	}

	// TODO
	/*override fun getFitness(): StrategyPriority {
		return StrategyPriority.PLAYBACK
	}*/

	// region Java overrides

	override fun toString(): String {
		return "${this.javaClass}\tApk: $packageName"
	}

	override fun equals(other: Any?): Boolean {
		if (other !is MemoryPlayback)
			return false

		return this.packageName == other.packageName
	}

	override fun hashCode(): Int {
		return this.model?.hashCode() ?: 0
	}

	// endregion
}