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
package org.droidmate.exploration

import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.playback.Playback
import org.droidmate.exploration.strategy.widget.AllowRuntimePermission
import org.droidmate.exploration.strategy.widget.ModelBased
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

typealias SelectorFunction = suspend (context: AbstractContext, explorationPool:ExplorationStrategyPool, bundle: Array<out Any>?) -> ISelectableExplorationStrategy?
typealias OnSelected = (context: AbstractContext) -> Unit

class StrategySelector constructor(val priority: Int,
									private val description: String,
									val selector: SelectorFunction,
									val onSelected: OnSelected? = null,
									vararg val bundle: Any){
	constructor(priority: Int,
				description: String,
				selector: SelectorFunction,
				bundle: Any): this(priority, description, selector, null, bundle)


	override fun toString(): String {
		return "($priority)-$description"
	}

    companion object {
        val logger: Logger = LoggerFactory.getLogger(StrategySelector::class.java)

		/**
		 * Get action before the last one.
		 *
		 * Used by some strategies (ex: Terminate and Back) to prevent loops (ex: Reset -> Back -> Reset -> Back)
		 */
		private suspend fun AbstractContext.getSecondLastAction(): ActionData {
			if (this.getSize() < 2)
				return ActionData.empty

			return this.actionTrace.P_getActions().dropLast(1).last()
		}

        /**
         * Terminate the exploration after a predefined number of actions
         */
		@JvmStatic
        val actionBasedTerminate : SelectorFunction = { context, pool, bundle ->
            val maxActions = bundle!![0] .toString().toInt()
            if (context.actionTrace.size == maxActions) {
                logger.debug("Maximum number of actions reached. Returning 'Terminate'")
                pool.getFirstInstanceOf(Terminate::class.java)
            }
            else
                null
        }

        /**
         * Terminate the exploration after a predefined elapsed time
         */
        @JvmStatic
        val timeBasedTerminate : SelectorFunction = { context, pool, bundle ->
	        val timeLimit = bundle!![0].toString().toInt()
	        if(timeLimit<=0) null
	        else {
		        val diff = context.getExplorationTimeInMs() //TODO check if this works and doesn't raise an exception because context start time is not yet initialized

		        if (diff >= timeLimit) {
			        logger.debug("Exploration time exhausted. Returning 'Terminate'")
			        pool.getFirstInstanceOf(Terminate::class.java)
		        } else
			        null
	        }
        }

        /**
         * Restarts the exploration when the current state is an "app not responding" dialog
         */
		@JvmStatic
        val appCrashedReset: SelectorFunction = { context, pool, _ ->
            val currentState = context.getCurrentState()

            if (currentState.isAppHasStoppedDialogBox) {
                logger.debug("Current screen is 'App has stopped'. Returning 'Reset'")
                pool.getFirstInstanceOf(Reset::class.java)
            }
            else
                null
        }

        /**
         * Sets the device to a known state (wifi on, empty logcat) and starts the app
         */
		@JvmStatic
        val startExplorationReset: SelectorFunction = { context, pool, _ ->
            if (context.isEmpty()) {
                logger.debug("Context is empty, must start exploration. Returning 'Reset'")
                pool.getFirstInstanceOf(Reset::class.java)
            }
            else
                null
        }

        /**
         * Resets the exploration once a predetermined number of non-reset actions has been executed
         */
		@JvmStatic
        val intervalReset: SelectorFunction = { context, pool, bundle ->
            val interval = bundle!![0].toString().toInt()

            val lastReset = context.actionTrace.P_getActions()
                    .indexOfLast { it -> it.actionType == ResetAppExplorationAction::class.java.simpleName }

            val currAction = context.actionTrace.size
            val diff = currAction - lastReset

            if (diff > interval){
                logger.debug("Has not restarted for $diff actions. Returning 'Reset'")
                pool.getFirstInstanceOf(Reset::class.java)
            }
            else
                null
        }

        /**
         * Selects a random widget and acts over it
         */
		@JvmStatic
        val randomWidget: SelectorFunction = { _, pool, _ ->
            pool.getFirstInstanceOf(RandomWidget::class.java)
        }

		/**
		 * Randomly selects a widget among those classified by a static model as "has event" and acts over it
		 */
		@JvmStatic
		val randomWithModel: SelectorFunction = { _, pool, _ ->
			pool.getFirstInstanceOf(ModelBased::class.java)
		}

		/**
		 * Selects a widget among those classified by a static model as "has event" and acts over it
		 */
		@JvmStatic
		val randomBiased: SelectorFunction = { _, pool, _ ->
			pool.getFirstInstanceOf(ModelBased::class.java)
		}

        /**
         * Randomly presses back.
         *
         * Expected bundle: Array: [Probability (Double), java.util.Random].
         *
         * Passing a different bundle will crash the execution.
         */
		@JvmStatic
        val randomBack: SelectorFunction = {context, pool, bundle ->
            val bundleArray = bundle!!
            val probability = bundleArray[0] as Double
            val random = bundleArray[1] as Random
            val value = random.nextDouble()

            if ((context.getLastActionType() == ResetAppExplorationAction::class.java.simpleName) || (value > probability))
                null
            else {
                logger.debug("Has triggered back probability and previous action was not to press back. Returning 'Back'")
                pool.getFirstInstanceOf(Back::class.java)
            }
        }

		@JvmStatic
        val cannotExplore: SelectorFunction = { context, pool, _ ->
			if (!context.explorationCanMoveOn()){
				val lastActionType = context.getLastActionType()

				// last action was reset
				when (lastActionType){
					PressBackExplorationAction::class.simpleName -> {
						logger.debug("Cannot explore. Last action was back. Returning 'Reset'")
						pool.getFirstInstanceOf(Reset::class.java)
					}

					ResetAppExplorationAction::class.java.simpleName -> {
						// if previous action was back, terminate
						when {
							context.getCurrentState().isAppHasStoppedDialogBox -> {
								logger.debug("Cannot explore. Last action was reset. Currently on an 'App has stopped' dialog. Returning 'Terminate'")
								pool.getFirstInstanceOf(Terminate::class.java)
							}
							context.getSecondLastAction().actionType == PressBackExplorationAction::class.java.simpleName -> {
								logger.debug("Cannot explore. Last action was reset. Previous action was to press back. Returning 'Terminate'")
								pool.getFirstInstanceOf(Terminate::class.java)
							}

						// otherwise, press back
							else -> {
								logger.debug("Cannot explore. Returning 'Back'")
								pool.getFirstInstanceOf(Back::class.java)
							}
						}
					}

					// by default, if it cannot explore, presses back
					else -> pool.getFirstInstanceOf(Back::class.java)
				}
			}
			else
			// can move forwards
				null
		}

		/**
		 * Selects the allow runtime permission command
		 */
		@JvmStatic
		val allowPermission: SelectorFunction = { context, pool, _ ->
			val widgets = context.getCurrentState().widgets
			var hasAllowButton = widgets.any { it.resourceId == "com.android.packageinstaller:id/permission_allow_button" }

			if (!hasAllowButton)
				hasAllowButton = widgets.any { it.text.toUpperCase() == "ALLOW" }

			if (hasAllowButton) {
				logger.debug("Runtime permission dialog. Returning 'AllowRuntimePermission'")
				pool.getFirstInstanceOf(AllowRuntimePermission::class.java)
			}
			else
				null
		}

		/**
		 * Finishes the exploration once all widgets have been explored
		 */
		@JvmStatic
		val explorationExhausted: SelectorFunction = { context, pool, _ ->
			val exhausted = !context.isEmpty() && context.areAllWidgetsExplored()

			if (exhausted)
				pool.getFirstInstanceOf(Terminate::class.java)
			else
				null
		}

		@JvmStatic
		val playback: SelectorFunction = { context, pool, _ ->
			logger.debug("Playback. Returning 'MemoryPlayback'")
			pool.getFirstInstanceOf(Playback::class.java)
		}
    }
}