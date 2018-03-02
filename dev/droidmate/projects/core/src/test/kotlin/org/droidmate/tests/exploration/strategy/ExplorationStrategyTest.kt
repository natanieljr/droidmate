// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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

package org.droidmate.tests.exploration.strategy

import org.droidmate.configuration.Configuration
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.device.datatypes.IWidget
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newPressBackExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newResetAppExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newTerminateExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newWidgetExplorationAction
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.exploration.strategy.IExplorationStrategy
import org.droidmate.test_tools.ApkFixtures
import org.droidmate.test_tools.DroidmateTestCase
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper.Companion.newAppHasStoppedGuiState
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper.Companion.newCompleteActionUsingGuiState
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper.Companion.newGuiStateWithDisabledWidgets
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper.Companion.newGuiStateWithTopLevelNodeOnly
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper.Companion.newGuiStateWithWidgets
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper.Companion.newHomeScreenGuiState
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper.Companion.newOutOfAppScopeGuiState
import org.droidmate.test_tools.device.datatypes.UiautomatorWindowDumpTestHelper
import org.droidmate.test_tools.exploration.data_aggregators.ExplorationOutput2Builder
import org.droidmate.test_tools.exploration.strategy.ExplorationStrategyTestHelper
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

/**
 * Untested behavior:
 * <ul>
 *   <li>Chooses only <i>clickable</i> widgets to click from the input GUI state.</li>
 * </ul>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ExplorationStrategyTest : DroidmateTestCase() {
    companion object {

        private fun getStrategy(actionsLimit: Int = Configuration.defaultActionsLimit,
                                resetEveryNthExplorationForward: Int = Configuration.defaultResetEveryNthExplorationForward): IExplorationStrategy
                = ExplorationStrategyTestHelper.buildStrategy(actionsLimit, resetEveryNthExplorationForward)

        /** After this method call the strategy should go from "before the first decision" to
         * "after the first decision, in the main decision loop" mode.
         * */
        @JvmStatic
        private fun makeIntoNormalExplorationMode(strategy: IExplorationStrategy): ExplorationAction
                = strategy.decide(newResultFromGuiState(newGuiStateWithWidgets(1)))

        @JvmStatic
        private fun newResultFromGuiState(guiState: IGuiState): IExplorationActionRunResult {
            val builder = ExplorationOutput2Builder()
            return builder.buildActionResult(mapOf("guiSnapshot" to UiautomatorWindowDumpTestHelper.fromGuiState(guiState),
                    "packageName" to ApkFixtures.apkFixture_simple_packageName))
        }

        @JvmStatic
        private fun verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy: IExplorationStrategy, gs: IGuiState, w: IWidget? = null) {
            if (w == null)
                assert(strategy.decide(newResultFromGuiState(gs)) is WidgetExplorationAction)
            else
                assert(strategy.decide(newResultFromGuiState(gs)) == newWidgetExplorationAction(w))
        }

        @JvmStatic
        private fun verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy: IExplorationStrategy, gs: IGuiState) {
            assert(strategy.decide(newResultFromGuiState(gs)) == newTerminateExplorationAction())
        }

        @JvmStatic
        private fun verifyProcessOnGuiStateReturnsResetExplorationAction(strategy: IExplorationStrategy, gs: IGuiState) {
            val guiStateResult = newResultFromGuiState(gs)
            val chosenAction = strategy.decide(guiStateResult)
            assert(chosenAction == newResetAppExplorationAction())
        }

        @Suppress("unused")
        private fun verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy: IExplorationStrategy, gs: IGuiState) {
            assert(strategy.decide(newResultFromGuiState(gs)) == newPressBackExplorationAction())
        }

    }

    @Test
    fun `Given no clickable widgets after app was initialized or reset, attempts ot press back then requests termination`() {
        // Act 1 & Assert
        var strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, newGuiStateWithTopLevelNodeOnly())
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, newGuiStateWithTopLevelNodeOnly())
        verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, newGuiStateWithTopLevelNodeOnly())

        // Act 2 & Assert
        strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, newGuiStateWithDisabledWidgets(1))
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, newGuiStateWithTopLevelNodeOnly())
        verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, newGuiStateWithDisabledWidgets(1))
    }

    @Test
    fun `Given no clickable widgets during normal exploration, press back, it doesn't work then requests app reset`() {
        val strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, newGuiStateWithTopLevelNodeOnly())
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, newGuiStateWithTopLevelNodeOnly())
    }

    @Test
    fun `Given other app during normal exploration, requests press back`() {
        // ----- Test 1 -----

        var strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)

        // Act & assert(1
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, newHomeScreenGuiState())
    }

    @Test
    fun `Given other app or 'app has stopped' screen during normal exploration, requests press back`() {
        // ----- Test 1 -----

        var strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)

        // Act & assert(1
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, newHomeScreenGuiState())

        // ----- Test 2 -----

        strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)

        // Act & assert(2
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, newOutOfAppScopeGuiState())
    }


    @Test
    fun `Given 'app has stopped' screen during normal exploration, requests app reset`() {
        val strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)

        // Act & assert(3
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, newAppHasStoppedGuiState())
    }

    @Test
    fun `Given 'complete action using' dialog box, requests press back`() {
        val strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)

        // Act & Assert
        val actionWithGUIState = newCompleteActionUsingGuiState()
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, actionWithGUIState)
    }


    @Test
    fun `If normally would request second app reset in a row, instead terminates exploration, to avoid infinite loop`() {
        val strategy = getStrategy()
        makeIntoNormalExplorationMode(strategy)

        verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, newAppHasStoppedGuiState())
        //verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, newAppHasStoppedGuiState())
        //verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, newGuiStateWithTopLevelNodeOnly())
    }

    @Test
    fun `When exploring forward and configured so, resets exploration every time`() {
        val strategy = getStrategy(/* actionsLimit */ 3, /* resetEveryNthExplorationForward */ 1
        )
        val gs = newGuiStateWithWidgets(3, ApkFixtures.apkFixture_simple_packageName)

        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, gs)
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, gs)
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, gs)
        verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, gs)
    }

    @Test
    fun `When exploring forward and configured so, resets exploration every third time`() {
        val strategy = getStrategy( 10, 3)
        makeIntoNormalExplorationMode(strategy)
        val gs = newGuiStateWithWidgets(3, ApkFixtures.apkFixture_simple_packageName)
        val egs = newGuiStateWithTopLevelNodeOnly()

        verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, gs) // 1st exploration forward: widget click
        verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, gs) // 2nd exploration forward: widget click
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, gs) // 3rd exploration forward: reset

        verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, gs) // 1st exploration forward: widget click
        verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, egs) // press back because cannot move forward
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, egs) // reset because cannot move forward
        verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, gs) // 1st exploration forward: widget click
        verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, gs) // 2nd exploration forward: widget click
        verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, gs) // 3rd exploration forward: reset

        // At this point all 8 actions have been executed.

        verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, gs)
    }
}