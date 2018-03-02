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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.droidmate.device.datatypes.EmptyGuiState
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.termination.Terminate
import org.droidmate.exploration.strategy.termination.criterion.CriterionProvider
import org.droidmate.exploration.strategy.termination.criterion.TimeBasedCriterion
import org.droidmate.exploration.strategy.widget.AllowRuntimePermission
import org.droidmate.exploration.strategy.widget.AlwaysFirstWidget
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.tests.exploration.strategy.stubs.DummyExplorationAction
import org.droidmate.tests.exploration.strategy.stubs.TripleActionExploration
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.time.LocalDateTime

/**
 * Unit tests for adaptive exploration strategy
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ExplorationStrategiesTest {

    @Test
    fun strategySelectionTest() {
        // Initialization
        val nrOfActions = 10
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        cfg.actionsLimit = nrOfActions
        val strategy = ExplorationStrategyPool(ArrayList())
        val terminationCriterionList = CriterionProvider.build(cfg, ArrayList())
        assertTrue(terminationCriterionList.size == 1)
        val terminationCriterion = terminationCriterionList[0]
        strategy.registerStrategy(Terminate.build(terminationCriterion))
        strategy.registerStrategy(Reset.build(cfg))
        strategy.registerStrategy(RandomWidget.build(cfg))
        strategy.registerStrategy(TripleActionExploration.build())

        // Mocking
        val inputData = mock<IExplorationActionRunResult>()
        val snapshot = mock<IDeviceGuiSnapshot>()
        val guiState = mock<IGuiState>()
        whenever(inputData.successful).thenReturn(true)
        whenever(inputData.exploredAppPackageName).thenReturn("STUB!")
        whenever(inputData.guiSnapshot).thenReturn(snapshot)
        whenever(inputData.guiSnapshot.getPackageName()).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.id).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.guiState).thenReturn(guiState)
        whenever(guiState.belongsToApp(any())).thenReturn(true)
        whenever(guiState.widgets).thenReturn(Auxiliary.createTestWidgets())

        // 11 calls to the decide function
        val actions = ArrayList<ExplorationAction>()

        for (i in 0..nrOfActions) {
            // Only in the last should the termination criterion be met
            assertTrue(i < nrOfActions || terminationCriterion.met())
            if (i == 0)
                actions.add(strategy.decide(EmptyExplorationActionRunResult()))
            else
                actions.add(strategy.decide(inputData))
        }

        // Expected order of actions:
        assertTrue(actions[0] is ResetAppExplorationAction)
        assertTrue(actions[1] is WidgetExplorationAction)
        assertTrue(actions[2] is WidgetExplorationAction)
        assertTrue(actions[3] is DummyExplorationAction)
        assertTrue(actions[4] is DummyExplorationAction)
        assertTrue(actions[5] is DummyExplorationAction)
        assertTrue(actions[6] is ResetAppExplorationAction)
        assertTrue(actions[7] is WidgetExplorationAction)
        assertTrue(actions[8] is WidgetExplorationAction)
        assertTrue(actions[9] is WidgetExplorationAction)
        assertTrue(actions[10] is TerminateExplorationAction)
        assertTrue(terminationCriterion.met())
    }

    @Test
    fun actionBasedTerminationStrategyTest() {
        // Initialization
        val strategy = ExplorationStrategyPool(ArrayList())

        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        cfg.actionsLimit = 1
        val terminationCriterionList = CriterionProvider.build(cfg, ArrayList())
        assertTrue(terminationCriterionList.size == 1)
        val terminationCriterion = terminationCriterionList[0]
        strategy.registerStrategy(Terminate.build(terminationCriterion))
        strategy.registerStrategy(Reset.build(cfg))
        strategy.registerStrategy(RandomWidget.build(cfg))

        // Mocking
        val inputData = mock<IExplorationActionRunResult>()
        val snapshot = mock<IDeviceGuiSnapshot>()
        val guiState = mock<IGuiState>()
        whenever(inputData.successful).thenReturn(true)
        whenever(inputData.exploredAppPackageName).thenReturn("STUB!")
        whenever(inputData.guiSnapshot).thenReturn(snapshot)
        whenever(inputData.guiSnapshot.getPackageName()).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.id).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.guiState).thenReturn(guiState)
        whenever(guiState.belongsToApp(any())).thenReturn(true)
        whenever(guiState.widgets).thenReturn(Auxiliary.createTestWidgets())

        // Criterion = 1 action
        // First is valid
        assertFalse(terminationCriterion.met())
        strategy.decide(inputData)
        // Now should meet termination
        assertTrue(terminationCriterion.met())
    }

    @Test
    fun timeBasedTerminationStrategyTest() {
        // Initialization
        val strategy = ExplorationStrategyPool(ArrayList())

        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        cfg.actionsLimit = 0
        cfg.timeLimit = 1
        val terminationCriterionList = CriterionProvider.build(cfg, ArrayList())
        assertTrue(terminationCriterionList.size == 1)
        val terminationCriterion = terminationCriterionList[0]
        strategy.registerStrategy(Terminate.build(terminationCriterion))
        strategy.registerStrategy(Reset.build(cfg))

        // Mocking
        val inputData = mock<IExplorationActionRunResult>()
        val snapshot = mock<IDeviceGuiSnapshot>()
        val guiState = mock<IGuiState>()
        whenever(inputData.successful).thenReturn(true)
        whenever(inputData.exploredAppPackageName).thenReturn("STUB!")
        whenever(inputData.guiSnapshot).thenReturn(snapshot)
        whenever(inputData.guiSnapshot.getPackageName()).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.id).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.guiState).thenReturn(guiState)
        whenever(guiState.belongsToApp(any())).thenReturn(true)
        whenever(guiState.widgets).thenReturn(Auxiliary.createTestWidgets())

        // Criterion = 1 action
        // The timer starts here
        strategy.decide(EmptyExplorationActionRunResult())
        // Reset the clock, since it had to wait the exploration action to be done
        (terminationCriterion as TimeBasedCriterion).resetClock()
        // First is valid
        assertFalse(terminationCriterion.met())

        // Sleep for one second, state is updated after deciding last action
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Assert.fail()
        }

        // After selecting the next action the time is again updated
        strategy.decide(inputData)

        // Now should meet termination
        assertTrue(terminationCriterion.met())
    }

    @Test
    fun duplicateStrategyRegistrationTest() {
        // Initialization
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        val strategyPool = ExplorationStrategyPool(ArrayList())
        val terminationCriterionList = CriterionProvider.build(cfg, ArrayList())
        assertTrue(terminationCriterionList.size == 1)
        val terminationCriterion = terminationCriterionList[0]
        // Cannot registers 2x the same strategy
        assertTrue(strategyPool.registerStrategy(Terminate.build(terminationCriterion)))
        assertFalse(strategyPool.registerStrategy(Terminate.build(terminationCriterion)))

        assertTrue(strategyPool.registerStrategy(RandomWidget.build(cfg)))
        assertFalse(strategyPool.registerStrategy(RandomWidget.build(cfg)))

        assertTrue(strategyPool.registerStrategy(Reset.build(cfg)))
        assertFalse(strategyPool.registerStrategy(Reset.build(cfg)))

        assertTrue(strategyPool.registerStrategy(PressBack.build(0.10, cfg)))
        assertFalse(strategyPool.registerStrategy(PressBack.build(0.10, cfg)))

        assertTrue(strategyPool.registerStrategy(AlwaysFirstWidget.build()))
        assertFalse(strategyPool.registerStrategy(AlwaysFirstWidget.build()))

        assertTrue(strategyPool.registerStrategy(AllowRuntimePermission.build()))
        assertFalse(strategyPool.registerStrategy(AllowRuntimePermission.build()))
    }

    @Test
    fun strategyComparisonTest() {
        // Initialization
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        val terminationCriteriaList = CriterionProvider.build(cfg, ArrayList())
        assertTrue(terminationCriteriaList.size == 1)

        val terminateStrategy = Terminate.build(terminationCriteriaList[0])
        val randomStrategy = RandomWidget.build(cfg)
        val resetStrategy = Reset.build(cfg)

        // Not equal (instanceOf check)
        assertFalse(terminateStrategy == randomStrategy)
        assertFalse(randomStrategy == terminateStrategy)
        assertFalse(randomStrategy == resetStrategy)
        assertFalse(resetStrategy == terminateStrategy)

        // Equal (different objects)
        val terminateStrategy2 = Terminate.build(terminationCriteriaList[0])
        val randomStrategy2 = RandomWidget.build(cfg)
        val resetStrategy2 = Reset.build(cfg)
        assertTrue(terminateStrategy == terminateStrategy2)
        assertTrue(randomStrategy == randomStrategy2)
        assertTrue(resetStrategy == resetStrategy2)

        // Not equal
        cfg.resetEveryNthExplorationForward++
        val resetStrategy3 = Reset.build(cfg)
        assertFalse(resetStrategy == resetStrategy3)
    }

    @Test
    fun terminateStrategyDoesNotBelongToAppTest() {
        // Initialization
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        val terminationCriterion = CriterionProvider.build(cfg, ArrayList())[0]
        val strategy = Terminate.build(terminationCriterion)
        val memory = Memory()
        strategy.initialize(memory)
        val guiState = Auxiliary.createGuiStateFromFile()
        var widgetContext = memory.getWidgetContext(guiState, "INVALID")

        // Must not be executed
        var fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.NONE)

        // First action is always reset
        val resetStrategy = Reset.build(cfg)
        resetStrategy.initialize(memory)
        widgetContext = memory.getWidgetContext(EmptyGuiState(), "")
        memory.logProgress(resetStrategy.decide(widgetContext),
                ExplorationType.Reset, widgetContext, LocalDateTime.now())

        val backStrategy = PressBack.build(0.1, cfg)
        backStrategy.initialize(memory)
        memory.logProgress(backStrategy.decide(widgetContext),
                ExplorationType.Back, widgetContext, LocalDateTime.now())
        memory.logProgress(resetStrategy.decide(widgetContext),
                ExplorationType.Reset, widgetContext, LocalDateTime.now())

        widgetContext = memory.getWidgetContext(guiState, "INVALID")
        fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.TERMINATE)

        // Produced a termination action
        val action = strategy.decide(widgetContext)
        assertTrue(action is TerminateExplorationAction)
        // Did not reach termination criteria
        assertFalse(terminationCriterion.met())
    }

    @Test
    fun terminateStrategyNoActionableWidgetsTest() {
        // Initialization
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        val terminationCriterion = CriterionProvider.build(cfg, ArrayList())[0]
        val strategy = Terminate.build(terminationCriterion)
        val memory = Memory()
        strategy.initialize(memory)
        val guiState = Auxiliary.createGuiStateFromFile()
        // Disable all widgets
        guiState.widgets.forEach { p -> p.enabled = false }

        var widgetContext = memory.getWidgetContext(guiState, guiState.topNodePackageName)
        // Must not be executed
        var fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.NONE)

        // First action is always reset
        val resetStrategy = Reset.build(cfg)
        resetStrategy.initialize(memory)
        widgetContext = memory.getWidgetContext(EmptyGuiState(), "")
        memory.logProgress(resetStrategy.decide(widgetContext),
                ExplorationType.Reset, widgetContext, LocalDateTime.now())

        // Must be executed
        widgetContext = memory.getWidgetContext(guiState, guiState.topNodePackageName)
        fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.TERMINATE)

        // Produced a termination action
        val action = strategy.decide(widgetContext)
        assertTrue(action is TerminateExplorationAction)
        // Did not reach termination criteria
        assertFalse(terminationCriterion.met())
    }

    companion object {
        private val DEFAULT_ARGS = arrayOf("-resetEvery=3", "-actionsLimit=10", "-randomSeed=0")
    }
}
