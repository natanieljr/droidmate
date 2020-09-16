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
package org.droidmate.exploration.strategy

import kotlinx.coroutines.delay
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.deviceInterface.exploration.LaunchApp
import org.droidmate.deviceInterface.exploration.isQueueEnd
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.actions.launchApp
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.actions.terminateApp
import org.droidmate.exploration.strategy.manual.Logging
import org.droidmate.exploration.strategy.manual.getLogger
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.HashMap
import java.util.Random
import java.util.UUID

@Suppress("unused")
object DefaultStrategies : Logging {
    override val log = getLogger()

    /**
     * Terminate the exploration after a predefined elapsed time (in ms)
     */
    fun timeBasedTerminate(priority: Int, maxMilliSeconds: Int) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "timeBasedTerminate"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
            val diff = eContext.getExplorationTimeInMs()
            log.info("remaining exploration time: ${"%.1f".format((maxMilliSeconds - diff) / 1000.0)}s")
            return maxMilliSeconds in 1..diff
        }

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
            return ExplorationAction.terminateApp()
        }
    }

    /**
     * Terminate the exploration after a predefined number of actions
     */
    fun actionBasedTerminate(priority: Int, maxActions: Int) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "actionBasedTerminate"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean =
            eContext.explorationTrace.size >= maxActions

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
            log.debug("Maximum number of actions reached. Terminate")
            return ExplorationAction.terminateApp()
        }
    }

    /**
     * Restarts the exploration when the current state is an "app not responding" dialog
     */
    fun resetOnAppCrash(priority: Int) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "resetOnAppCrash"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean =
            eContext.getCurrentState().isAppHasStoppedDialogBox

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
            log.debug("Current screen is 'App has stopped'. Reset")
            return eContext.resetApp()
        }
    }

    /**
     * Resets the exploration once a predetermined number of non-reset actions has been executed
     */
    fun intervalReset(priority: Int, interval: Int) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "intervalReset"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
            val lastReset = eContext.explorationTrace.P_getActions()
                .indexOfLast { it.actionType == LaunchApp.name }

            val currAction = eContext.explorationTrace.size
            val diff = currAction - lastReset

            return diff > interval
        }

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
            return eContext.resetApp()
        }
    }

    /**
     * Randomly presses back.
     *
     * Expected bundle: [Probability (Double), java.util.Random].
     *
     * Passing a different bundle will crash the execution.
     */
    fun randomBack(priority: Int, probability: Double, rnd: Random) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "randomBack"

        override fun getPriority() = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
            val value = rnd.nextDouble()

            val lastLaunchDistance = with(eContext.explorationTrace.getActions()) {
                size - lastIndexOf(findLast { !it.actionType.isQueueEnd() })
            }
            return (lastLaunchDistance > 3 && value > probability)
        }

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
            log.debug("Has triggered back probability and previous action was not to press back. Returning 'Back'")
            return ExplorationAction.closeAndReturn()
        }
    }

    private operator fun GlobalAction.times(multiplier: Int) =
        (0..multiplier)
            .map { GlobalAction(this.actionType) }
            .toTypedArray()

    /**
     * Check the current state for interactive UI elements to interact with,
     * if none are available we try to
     * 1. close keyboard & press back
     *   (per default keyboard items would be interactive but the user may use a custom model where this is not the case)
     * 2. reset the app (if the last action already was a press-back)
     * 3. if there was a reset within the last 3 actions or the last action was a Fetch
     *  - we try to wait for up to ${maxWaittime}s (default 5s) if any interactive element appears
     *  - if the app has crashed we terminate
     */
    fun handleTargetAbsence(priority: Int, maxWaitTime: Long = 1000, numFetches: Int = 10) = object : AExplorationStrategy() {
        private lateinit var launchInstruction: ExplorationAction

        override fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> initialize(initialContext: ExplorationContext<M, S, W>) {
            super.initialize(initialContext)
            launchInstruction = initialContext.launchApp()
        }

        val defaultAbsenceBehavior: List<ExplorationAction> by lazy {
            listOf(
                *(GlobalAction(ActionType.FetchGUI) * numFetches),
                ExplorationAction.pressBack(),
                *(GlobalAction(ActionType.FetchGUI) * numFetches),
                launchInstruction,
                *(GlobalAction(ActionType.FetchGUI) * numFetches)
            )
        }

        private var pendingActions: MutableList<ExplorationAction> = mutableListOf()

        // may be used to terminate if there are no targets after waiting for maxWaitTime
        private var terminate = false

        override val uniqueStrategyName: String = "handleTargetAbsence"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
            val canExplore = eContext.explorationCanMoveOn()

            // reset the counter if we can proceed
            if (canExplore) {
                // if can continue, reset list of actions
                pendingActions = defaultAbsenceBehavior.toMutableList()
            }

            return !canExplore
        }

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
            return if (eContext.getCurrentState().isHomeScreen && pendingActions.size == defaultAbsenceBehavior.size) {
                // if we ended in the homeScreen speedup the process by directly resetting the app
                pendingActions.removeAt(0)
                eContext.resetApp()
            } else if (pendingActions.isNotEmpty()) {
                // has action to execute
                log.debug("Cannot explore. Following predefined action queue. Remaining actions: ${pendingActions.size}")
                val nextAction = pendingActions.removeAt(0)

                if (nextAction.isFetch()) {
                    delay(maxWaitTime)
                }

                nextAction
            } else {
                // has no other action. if the app crashed, terminate, otherwise reset
                val currState = eContext.getCurrentState()
                if (currState.isAppHasStoppedDialogBox) {
                    log.debug("Cannot explore. Already tried to reset. Currently on an 'App has stopped' dialog. Returning 'Terminate'")
                    ExplorationAction.terminateApp()
                } else {
                    log.debug("Cannot explore. Out of options. Try restarting the app again")
                    eContext.resetApp()
                }
            }
        }
    }

    /**
     * Always clicks allow/ok for any runtime permission request
     */
    fun allowPermission(priority: Int, maxTries: Int = 5) = object : AExplorationStrategy() {
        private var numPermissions =
            HashMap<UUID, Int>()  // avoid some options which are misinterpreted as permission request to be infinitely triggered

        override val uniqueStrategyName: String = "allowPermission"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean =
            numPermissions.compute(eContext.getCurrentState().uid) { _, v -> v?.inc() ?: 0 } ?: 0 < maxTries
                    && eContext.getCurrentState().isRequestRuntimePermissionDialogBox

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
            // we do not require the element with the text ALLOW or OK to be clickabe since there may be overlaying elements
            // which handle the touch event for this button, however as a consequence we may click non-interactive labels
            // that is why we restricted this strategy to be executed at most [maxTries] from the same state
            val allowButton: Widget = eContext.getCurrentState().widgets.filter { it.isVisible }.let { widgets ->
                widgets.firstOrNull { it.resourceId == "com.android.packageinstaller:id/permission_allow_button" }
                    ?: widgets.firstOrNull { it.resourceId == "com.android.permissioncontroller:id/permission_allow_always_button" }
                    ?: widgets.firstOrNull { it.text.toUpperCase() == "ALLOW" }
                    ?: widgets.firstOrNull { it.text.toUpperCase() == "ALLOW ALL THE TIME" }
                    ?: widgets.firstOrNull { it.text.toUpperCase() == "OK" }
                    ?: widgets.first{ it.text.toUpperCase() == "CONTINUE" }
            }

            return allowButton.click(ignoreClickable = true)
        }
    }

    fun denyPermission(priority: Int) = object : AExplorationStrategy() {
        var denyButton: Widget? = null

        override val uniqueStrategyName: String = "denyPermission"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
            denyButton = eContext.getCurrentState().widgets.let { widgets ->
                widgets.find { it.resourceId == "com.android.packageinstaller:id/permission_deny_button" }
                    ?: widgets.find { it.resourceId == "com.android.permissioncontroller:id/permission_deny_button" }
                    ?: widgets.find { it.text.toUpperCase() == "DENY" }
            }
            return denyButton != null
        }

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction =
            denyButton?.click(ignoreClickable = true)
                ?: throw IllegalStateException("Error In denyPermission strategy, strategy was executed but hasNext should be false")
    }

    /**
     * Finishes the exploration once all widgets have been explored
     * FIXME this strategy is insanely inefficient right now and should be avoided
     */
    fun explorationExhausted(priority: Int) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "explorationExhausted"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean =
            eContext.explorationTrace.size > 2 && eContext.areAllWidgetsExplored()

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction =
            ExplorationAction.terminateApp()
    }

    /** press back if advertisement is detected */
    fun handleAdvertisement(priority: Int) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "handleAdvertisement"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean =
            eContext.getCurrentState().widgets.any { it.packageName == "com.android.vending" }

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction =
            ExplorationAction.pressBack()
    }

    /** press back if the app is in google chrome */
    fun handleChrome(priority: Int) = object : AExplorationStrategy() {
        override val uniqueStrategyName: String = "handleChrome"

        override fun getPriority(): Int = priority

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean =
            eContext.getCurrentState().widgets.any { it.packageName == "com.android.chrome" }

        override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction =
            ExplorationAction.pressBack()
    }
}