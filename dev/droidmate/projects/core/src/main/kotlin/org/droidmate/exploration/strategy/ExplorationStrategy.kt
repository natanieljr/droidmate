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

package org.droidmate.exploration.strategy

import com.google.common.base.Ticker
import org.droidmate.configuration.Configuration
import org.droidmate.device.datatypes.EmptyGuiState
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newPressBackExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newResetAppExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newTerminateExplorationAction
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.actions.TerminateExplorationAction
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory

class ExplorationStrategy constructor(private val resetEveryNthExplorationForward: Int,
                                      private val widgetStrategy: IWidgetStrategy,
                                      private val terminationCriterion: ITerminationCriterion,
                                      private val specialCases: IForwardExplorationSpecialCases) : IExplorationStrategy {

    companion object {
        private val log = LoggerFactory.getLogger(ExplorationStrategy::class.java)

        fun build(cfg: Configuration): ExplorationStrategy {
            val widgetStrategy = WidgetStrategy(cfg.randomSeed.toLong(), cfg.alwaysClickFirstWidget, cfg.widgetIndexes)
            val terminationCriterion = TerminationCriterion(cfg, cfg.timeLimit, Ticker.systemTicker())
            val specialCases = ForwardExplorationSpecialCases()
            return ExplorationStrategy(cfg.resetEveryNthExplorationForward, widgetStrategy, terminationCriterion, specialCases)
        }
    }

    /** Determines if last call to {@link #decide} returned {@link ResetAppExplorationAction}. */
    private var lastActionWasToReset = false

    /** Determines if during execution of any method in this class, at least one call to {@link #decide} has already fully finished.
     * This is set to false until the first call to {@link #decide} will set it to true near the end of its execution. */
    private var firstCallToDecideFinished = false

    private var forwardExplorationResetCounter = resetEveryNthExplorationForward

    // WISH super ugly, taken from widgetStrategy. Instead, it should be incorporated in
    // ExplorationStrategy.explorationCanMoveForwardOn,
    // which also takes WidgetStrategy as input, and then is asked.
    private var allWidgetsBlackListed = false


    init {
        assert(this.resetEveryNthExplorationForward >= 0)
    }

    override fun decide(result: IExplorationActionRunResult): ExplorationAction {
        log.debug("decide($result)")

        assert(result.successful)

        val guiState = result.guiSnapshot.guiState
        val exploredAppPackageName = result.exploredAppPackageName

        terminationCriterion.initDecideCall(firstDecisionIsBeingMade())

        allWidgetsBlackListed = widgetStrategy.updateState(guiState, exploredAppPackageName)

        var exploredForward = false
        val outExplAction = when {
            terminateExploration(guiState, exploredAppPackageName) -> newTerminateExplorationAction()
            resetExploration(guiState, exploredAppPackageName) -> newResetAppExplorationAction()
            backtrack(guiState, exploredAppPackageName) -> newPressBackExplorationAction()
            else -> {
                exploredForward = true
                exploreForward(guiState, exploredAppPackageName)
            }
        }

        updateState(outExplAction, exploredForward)

        logExplorationProgress(outExplAction)
        /* WISH Log clicked widgets indexes for manual repro preparation. It can be displayed in "run_data.txt" in a format that can
        be copy-pasted as input arg. This might require an upgrade to be able to also handle special actions like reset, etc.,
        not only widget indexes. It has to work for all kinds of exploration actions.
         */

        terminationCriterion.assertPostDecide(outExplAction)

        frontendHook(outExplAction)

        return outExplAction

    }

    @Suppress("UNUSED_PARAMETER")
            /**
             * Allows to hook into the the next ExplorationAction to be executed on the device.
             */
    fun frontendHook(explorationAction: ExplorationAction) {
        /*
        // To-do for SE team

        switch (explorationAction) {
            case WidgetExplorationAction:
                Widget w = (explorationAction as WidgetExplorationAction).widget

                String text = w.text // For other properties, see Widget

                // Otherwise the widget is not interesting (DroidMate will never do anything with it)
                boolean canBeActedUpon = w.canBeActedUpon()

                break
            case ResetAppExplorationAction:
                // No interesting properties, but just knowing the class is useful.
                break
            case TerminateExplorationAction:
                // No interesting properties, but just knowing the class is useful.
                break
            case EnterTextExplorationAction:
				break

			case PressBackExplorationAction:
				break
            default:
                throw UnexpectedIfElseFallthroughError()
        }
        */
    }

    private fun logExplorationProgress(outExplAction: ExplorationAction) {
        if (outExplAction is TerminateExplorationAction)
            log.info(outExplAction.toString())
        else
            log.info(terminationCriterion.getLogMessage() + " " + outExplAction.toString())
    }

    private fun exploreForward(guiState: IGuiState, exploredAppPackageName: String): ExplorationAction {
        assert(!terminateExploration(guiState, exploredAppPackageName))
        assert(!resetExploration(guiState, exploredAppPackageName))
        assert(!backtrack(guiState, exploredAppPackageName))
        assert(explorationCanMoveForwardOn(guiState, exploredAppPackageName))

        val outExplAction = if (decideToDoForwardExplorationReset())
            newResetAppExplorationAction()
        else {
            val processedData = specialCases.process(guiState, exploredAppPackageName)
            val specialCaseApplied = processedData.first

            if (!specialCaseApplied) {
                widgetStrategy.decide(guiState)
            } else {
                // WISH hackish, should happen in ExplorationStrategy.updateStrategyState
                // We do not include special exploration case in the reset counter
                forwardExplorationResetCounter++
                processedData.second!!
            }
        }

        return outExplAction
    }

    private fun decideToDoForwardExplorationReset(): Boolean {
        if (forwardExplorationResetCounter == 1) {
            log.info("Forward exploration reset.")
            return true
        }
        return false
    }

    /**
     * Determines if exploration shall be terminated. Obviously, exploration shall be terminated when the terminationCriterion is
     * met. However, two more cases justify termination:<br/>
     * - If exploration cannot move forward after reset. Resetting is supposed to unstuck exploration, and so if it doesn't help,
     * exploration cannot proceed forward at all.<br/>
     * - A special case of the above, if exploration cannot move at the first time exploration strategy makes a decision. This
     * is a special case because first time exploration strategy makes a decision is immediately after the initial app launch,
     * which is technically also a kind of reset.
     *
     */
    private fun terminateExploration(guiState: IGuiState, exploredAppPackageName: String): Boolean {
        assert(!lastActionWasToReset || firstCallToDecideFinished)

        if (terminationCriterion.met()) {
            log.info("Terminating exploration: " + terminationCriterion.metReason())
            return true
        }

        // first snapshot is always missing
        if (firstDecisionIsBeingMade() && (guiState is EmptyGuiState))
            return false

        // WISH if !explorationCanMoveForwardOn(guiState) after launch main activity, try again, but with longer wait delay.

        // If the exploration cannot move forward after reset or during initial attempt (just after first launch,
        // which is also a reset) then it shall be terminated.
        if (!explorationCanMoveForwardOn(guiState, exploredAppPackageName) && (lastActionWasToReset || firstDecisionIsBeingMade())) {
            val guiStateMsgPart = if (firstDecisionIsBeingMade()) "Initial GUI state" else "GUI state after reset"

            // This case is observed when e.g. the app shows empty screen at startup.
            if (!guiState.belongsToApp(exploredAppPackageName))
                log.info(Markers.appHealth, "Terminating exploration: $guiStateMsgPart doesn't belong to the app. " +
                        "The GUI state: $guiState")

            // This case is observed when e.g. the app has nonstandard GUI, e.g. game native interface.
            // Also when all widgets have been blacklisted because they e.g. crash the app.
            else if (!hasActionableWidgets(guiState)) {
                log.info(Markers.appHealth, "Terminating exploration: $guiStateMsgPart doesn't contain actionable widgets. " +
                        "The GUI state: $guiState")
                // log.info(guiState.debugWidgets())
            } else
                throw UnexpectedIfElseFallthroughError()

            return true
        }

        // At this point we know termination is not necessary, thus following assertions hold:
        assert(explorationCanMoveForwardOn(guiState, exploredAppPackageName) || !lastActionWasToReset || firstCallToDecideFinished)
        assert(!firstDecisionIsBeingMade() || explorationCanMoveForwardOn(guiState, exploredAppPackageName))
        assert(!lastActionWasToReset || explorationCanMoveForwardOn(guiState, exploredAppPackageName))

        return false
    }

    private fun resetExploration(guiState: IGuiState, exploredAppPackageName: String): Boolean {
        assert(!terminateExploration(guiState, exploredAppPackageName))

        // If any of these two asserts would be violated, the exploration would terminate.
        assert(!firstDecisionIsBeingMade() || explorationCanMoveForwardOn(guiState, exploredAppPackageName) || (guiState is EmptyGuiState))
        assert(!lastActionWasToReset || explorationCanMoveForwardOn(guiState, exploredAppPackageName))

        if (explorationCanMoveForwardOn(guiState, exploredAppPackageName)) {
            return false
        } else {
            assert(firstCallToDecideFinished || (guiState is EmptyGuiState))
            assert(!lastActionWasToReset)
            assert(!explorationCanMoveForwardOn(guiState, exploredAppPackageName))
            return true
        }
    }

    private fun firstDecisionIsBeingMade(): Boolean {
        return !firstCallToDecideFinished
    }

    private fun backtrack(guiState: IGuiState, exploredAppPackageName: String): Boolean {
        assert(!terminateExploration(guiState, exploredAppPackageName))
        assert(!resetExploration(guiState, exploredAppPackageName))
        /* As right now we never backtrack and backtracking is the last possibility to do something if exploration cannot move
        forward, thus we have this precondition. If backtracking will have some implementation, then it will handle some cases which
        are right now handled by terminateExploration and resetExploration, and this precondition will no longer hold.
         */
        assert(explorationCanMoveForwardOn(guiState, exploredAppPackageName))

        // Placeholder for possible future functionality.

        assert(explorationCanMoveForwardOn(guiState, exploredAppPackageName))
        return false
    }

    private fun explorationCanMoveForwardOn(guiState: IGuiState, exploredAppPackageName: String): Boolean =
            (guiState.belongsToApp(exploredAppPackageName) && hasActionableWidgets(guiState)) || guiState.isRequestRuntimePermissionDialogBox

    private fun hasActionableWidgets(guiState: IGuiState): Boolean {
        return (guiState.widgets.isNotEmpty()) &&
                guiState.widgets.any {
                    it.canBeActedUpon() && !allWidgetsBlackListed
                }
    }

    private fun updateState(action: ExplorationAction, exploredForward: Boolean) {
        if (!firstCallToDecideFinished)
            firstCallToDecideFinished = true

        terminationCriterion.updateState()

        val currentActionIsToReset = action is ResetAppExplorationAction

        if (exploredForward) {
            if (resetEveryNthExplorationForward > 0) {

                forwardExplorationResetCounter--
                assert(forwardExplorationResetCounter >= 0)

                if (forwardExplorationResetCounter == 0) {
                    assert(currentActionIsToReset)
                    forwardExplorationResetCounter = resetEveryNthExplorationForward
                }

                assert(forwardExplorationResetCounter >= 1)
            }
        } else if (currentActionIsToReset)
            forwardExplorationResetCounter = resetEveryNthExplorationForward

        lastActionWasToReset = currentActionIsToReset
    }
}