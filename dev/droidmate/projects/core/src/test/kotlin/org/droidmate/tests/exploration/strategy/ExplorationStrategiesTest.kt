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
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.device.datatypes.IGuiStatus
import org.droidmate.device.datatypes.WidgetData
import org.droidmate.device.datatypes.statemodel.ActionResult
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.reset.InitialReset
import org.droidmate.exploration.strategy.reset.IntervalReset
import org.droidmate.exploration.strategy.termination.ActionBasedTerminate
import org.droidmate.exploration.strategy.termination.CannotExploreTerminate
import org.droidmate.exploration.strategy.termination.TimeBasedTerminate
import org.droidmate.exploration.strategy.widget.AllowRuntimePermission
import org.droidmate.exploration.strategy.widget.AlwaysFirstWidget
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.test_tools.DroidmateTestCase
import org.droidmate.test_tools.exploration.strategy.ExplorationStrategyTestHelper.Companion.getResetStrategies
import org.droidmate.test_tools.exploration.strategy.ExplorationStrategyTestHelper.Companion.getTestExplorationLog
import org.droidmate.tests.exploration.strategy.stubs.DummyExplorationAction
import org.droidmate.tests.exploration.strategy.stubs.TripleActionExploration
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.net.URI
import java.time.LocalDateTime

/**
 * Unit tests for adaptive exploration strategy
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ExplorationStrategiesTest: DroidmateTestCase() {
    @Test
    fun strategySelectionTest() {
        // Initialization
        val nrOfActions = 10
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        cfg.actionsLimit = nrOfActions
        val explorationLog = getTestExplorationLog("STUB!")
        val strategy = ExplorationStrategyPool(ArrayList(), explorationLog)
        strategy.registerStrategy(ActionBasedTerminate(cfg))
        getResetStrategies(cfg).forEach { strategy.registerStrategy(it) }
        strategy.registerStrategy(RandomWidget.build(cfg))
        strategy.registerStrategy(TripleActionExploration.build())

        // Mocking
        val inputData = mock<ActionResult>()
        val snapshot = mock<IDeviceGuiSnapshot>()
        val guiState = mock<IGuiStatus>()
        whenever(inputData.successful).thenReturn(true)
        whenever(inputData.guiSnapshot.guiStatus.topNodePackageName).thenReturn("STUB!")
        whenever(inputData.guiSnapshot).thenReturn(snapshot)
        whenever(inputData.guiSnapshot.getPackageName()).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.id).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.guiStatus).thenReturn(guiState)
        whenever(guiState.belongsToApp(any())).thenReturn(true)
        whenever(guiState.widgets).thenReturn(Auxiliary.createTestWidgets())

        // 11 calls to the decide function
        val actions = ArrayList<ExplorationAction>()

        for (i in 0..nrOfActions) {
            // Only in the last should the termination criterion be met
            assertTrue(i <= nrOfActions || actions.last() is TerminateExplorationAction)
            if (i == 0)
                actions.add(strategy.decide(EmptyActionResult()))
            else
                actions.add(strategy.decide(inputData))
        }

        // Expected order of actionTrace:
        assertTrue(actions[0] is ResetAppExplorationAction)
        assertTrue(actions[1] is WidgetExplorationAction)
        assertTrue(actions[2] is WidgetExplorationAction)
        assertTrue(actions[3] is DummyExplorationAction)
        assertTrue(actions[4] is DummyExplorationAction)
        assertTrue(actions[5] is DummyExplorationAction)
        assertTrue(actions[6] is ResetAppExplorationAction)
        assertTrue(actions[7] is WidgetExplorationAction)
        assertTrue(actions[8] is WidgetExplorationAction)
        assertTrue(actions[9] is ResetAppExplorationAction)
        assertTrue(actions[10] is TerminateExplorationAction)
    }

    @Test
    fun actionBasedTerminationStrategyTest() {
        // Mocking
        val inputData = mock<ActionResult>()
        val snapshot = mock<IDeviceGuiSnapshot>()
        val guiState = mock<IGuiStatus>()
        whenever(inputData.successful).thenReturn(true)
        whenever(inputData.guiSnapshot.guiStatus.topNodePackageName).thenReturn("STUB!")
        whenever(inputData.guiSnapshot).thenReturn(snapshot)
        whenever(inputData.guiSnapshot.getPackageName()).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.id).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.guiStatus).thenReturn(guiState)
        whenever(guiState.belongsToApp(any())).thenReturn(true)
        whenever(guiState.widgets).thenReturn(Auxiliary.createTestWidgets())

        // Initialization
        val explorationLog = getTestExplorationLog(inputData.guiSnapshot.guiStatus.topNodePackageName)
        val strategy = ExplorationStrategyPool(ArrayList(), explorationLog)

        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        cfg.actionsLimit = 1
        val terminateStrategy = ActionBasedTerminate(cfg)
        strategy.registerStrategy(terminateStrategy)
        getResetStrategies(cfg).forEach { strategy.registerStrategy(it) }
        strategy.registerStrategy(RandomWidget.build(cfg))

        // Criterion = 1 action
        // First is valid
        val widgetContext = EmptyWidgetContext()//explorationLog.getState(inputData.guiSnapshot.guiState)
        assertFalse(terminateStrategy.met(widgetContext))
        strategy.decide(inputData)
        // Now should meet termination
        assertTrue(terminateStrategy.met(widgetContext))
    }

    @Test
    fun timeBasedTerminationStrategyTest() {
        // Mocking
        val inputData = mock<ActionResult>()
        val snapshot = mock<IDeviceGuiSnapshot>()
        val guiState = mock<IGuiStatus>()
        whenever(inputData.successful).thenReturn(true)
        whenever(inputData.guiSnapshot.guiStatus.topNodePackageName).thenReturn("STUB!")
        whenever(inputData.guiSnapshot).thenReturn(snapshot)
        whenever(inputData.guiSnapshot.getPackageName()).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.id).thenReturn("STUB!")
        whenever(inputData.guiSnapshot.guiStatus).thenReturn(guiState)
        whenever(guiState.belongsToApp(any())).thenReturn(true)
        whenever(guiState.widgets).thenReturn(Auxiliary.createTestWidgets())

        // Initialization
        val explorationLog = getTestExplorationLog(inputData.guiSnapshot.guiStatus.topNodePackageName)
        val strategy = ExplorationStrategyPool(ArrayList(), explorationLog)

        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        cfg.actionsLimit = 0
        cfg.timeLimit = 1
        val terminateStrategy = TimeBasedTerminate(cfg.timeLimit)
        strategy.registerStrategy(terminateStrategy)
        getResetStrategies(cfg).forEach { strategy.registerStrategy(it) }

        // Criterion = 1 action
        // The timer starts here
        strategy.decide(EmptyActionResult())
        // Reset the clock, since it had to wait the exploration action to be done
        terminateStrategy.resetClock()
        // First is valid
        val widgetContext = EmptyWidgetContext()//explorationLog.getState(inputData.guiSnapshot.guiState)
        assertFalse(terminateStrategy.met(widgetContext))

        // Sleep for one second, state is updated after deciding last action
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Assert.fail()
        }

        // After selecting the next action the time is again updated
        strategy.decide(inputData)

        // Now should meet termination
        assertTrue(terminateStrategy.met(widgetContext))
    }

    @Test
    fun duplicateStrategyRegistrationTest() {
        // Initialization
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        val explorationLog = getTestExplorationLog("STUB!")
        val strategyPool = ExplorationStrategyPool(ArrayList(), explorationLog)
        // Cannot registers 2x the same strategy
        assertTrue(strategyPool.registerStrategy(ActionBasedTerminate(cfg)))
        assertFalse(strategyPool.registerStrategy(ActionBasedTerminate(cfg)))

        assertTrue(strategyPool.registerStrategy(RandomWidget.build(cfg)))
        assertFalse(strategyPool.registerStrategy(RandomWidget.build(cfg)))

        assertTrue(strategyPool.registerStrategy(IntervalReset(cfg.resetEveryNthExplorationForward)))
        assertFalse(strategyPool.registerStrategy(IntervalReset(cfg.resetEveryNthExplorationForward)))

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
        val terminateStrategy : ISelectableExplorationStrategy = ActionBasedTerminate(cfg)
        val randomStrategy : ISelectableExplorationStrategy = RandomWidget.build(cfg)
        val resetStrategy : ISelectableExplorationStrategy = IntervalReset(0)

        // Not equal (instanceOf check)
        assertFalse(terminateStrategy == randomStrategy)
        assertFalse(randomStrategy == terminateStrategy)
        assertFalse(randomStrategy == resetStrategy)
        assertFalse(resetStrategy == terminateStrategy)

        // Equal (different objects)
        val terminateStrategy2 = ActionBasedTerminate(cfg)
        val randomStrategy2 = RandomWidget.build(cfg)
        val resetStrategy2 = IntervalReset(0)
        assertTrue(terminateStrategy == terminateStrategy2)
        assertTrue(randomStrategy == randomStrategy2)
        assertTrue(resetStrategy == resetStrategy2)

        // Not equal
        val resetStrategy3 = IntervalReset(1)
        assertFalse(resetStrategy == resetStrategy3)
    }

    @Test
    fun terminateStrategyDoesNotBelongToAppTest() {
        // Initialization
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        val explorationLog = getTestExplorationLog("")
        val strategy = CannotExploreTerminate()
        strategy.initialize(explorationLog)
        val guiState = Auxiliary.createGuiStateFromFile("INVALID")
        var widgetContext = EmptyWidgetContext()//explorationLog.getState(guiState)

        // Must not be executed
        var fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.NONE)

        // First action is always reset
        val resetStrategy = InitialReset()
        resetStrategy.initialize(explorationLog)
//        state = explorationLog.getState(EmptyGuiStatus())
        var record = ActionResult(resetStrategy.decide(widgetContext), LocalDateTime.now(), LocalDateTime.now(), screenshot = URI.create("test://"))
//                .apply { this.state = state }
        explorationLog.add(RunnableResetAppExplorationAction(record.action as ResetAppExplorationAction, LocalDateTime.now(), false), record)

        val backStrategy = PressBack.build(0.1, cfg)
        backStrategy.initialize(explorationLog)
        record = ActionResult(backStrategy.decide(widgetContext), LocalDateTime.now(), LocalDateTime.now(), screenshot = URI.create("test://"))
//                .apply { this.state = state }
        explorationLog.add(RunnablePressBackExplorationAction(record.action as PressBackExplorationAction, LocalDateTime.now(), false), record)
        record = ActionResult(resetStrategy.decide(widgetContext), LocalDateTime.now(), LocalDateTime.now(), screenshot = URI.create("test://"))
//                .apply { this.state = state }
        explorationLog.add(RunnableResetAppExplorationAction(record.action as ResetAppExplorationAction, LocalDateTime.now(), false), record)
//        state = explorationLog.getState(guiState)
        fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.TERMINATE)

        // Produced a termination action
        val action = strategy.decide(widgetContext)
        assertTrue(action is TerminateExplorationAction)
    }

    @Test
    fun terminateStrategyNoActionableWidgetsTest() {
        // Initialization
        val cfg = Auxiliary.createTestConfig(DEFAULT_ARGS)
        val strategy = CannotExploreTerminate()
        val explorationLog = getTestExplorationLog("")
        strategy.initialize(explorationLog)
        val guiState = Auxiliary.createGuiStateFromFile()
        // Disable all widgets
        guiState.widgets.forEach { p -> WidgetData(p.map.toMutableMap().apply { replace(WidgetData::enabled.name,false) }) }

        var widgetContext = EmptyWidgetContext()//explorationLog.getState(guiState)
        // Must not be executed
        var fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.NONE)

        // First action is always reset
        val resetStrategy = InitialReset()
        val backStrategy = PressBack.build(0.1, cfg)
        resetStrategy.initialize(explorationLog)
        widgetContext = EmptyWidgetContext()//explorationLog.getState(EmptyGuiStatus())
        var record = ActionResult(resetStrategy.decide(widgetContext), LocalDateTime.now(), LocalDateTime.now(), screenshot = URI.create("test://"))
//                .apply { this.state = state }
        explorationLog.add(RunnableResetAppExplorationAction(record.action as ResetAppExplorationAction, LocalDateTime.now(), false), record)
        record = ActionResult(backStrategy.decide(widgetContext), LocalDateTime.now(), LocalDateTime.now(), screenshot = URI.create("test://"))
//                .apply { this.state = state }
        explorationLog.add(RunnablePressBackExplorationAction(record.action as PressBackExplorationAction, LocalDateTime.now(), false), record)
        record = ActionResult(resetStrategy.decide(widgetContext), LocalDateTime.now(), LocalDateTime.now(), screenshot = URI.create("test://"))
//                .apply { this.state = state }
        explorationLog.add(RunnableResetAppExplorationAction(record.action as ResetAppExplorationAction, LocalDateTime.now(), false), record)

        // Must be executed
//        state = explorationLog.getState(guiState)
        fitness = strategy.getFitness(widgetContext)
        assertTrue(fitness == StrategyPriority.TERMINATE)

        // Produced a termination action
        val action = strategy.decide(widgetContext)
        assertTrue(action is TerminateExplorationAction)
    }

    companion object {
        private val DEFAULT_ARGS = arrayOf("-resetEvery=3", "-actionsLimit=10", "-randomSeed=0")
    }
}
