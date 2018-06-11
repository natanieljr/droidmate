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

import org.droidmate.test_tools.DroidmateTestCase
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
	// TODO Fix tests
	@Test
	fun dummy() {
	}
	/*companion object {

			private fun getStrategy(explorationLog: ExplorationContext,
															actionsLimit: Int = Configuration.defaultActionsLimit,
															resetEveryNthExplorationForward: Int = Configuration.defaultResetEveryNthExplorationForward): IExplorationStrategy = ExplorationStrategyTestHelper.buildStrategy(explorationLog, actionsLimit, resetEveryNthExplorationForward)

			/** After this method call the strategy should go from "before the first decision" to
			 * "after the first decision, in the main decision loop" mode.
			 * */
			@JvmStatic
			private fun makeIntoNormalExplorationMode(strategy: IExplorationStrategy, explorationLog: ExplorationContext): ActionResult {
					val guiState = newGuiStateWithWidgets(1)
					val action = strategy.decide(newResultFromGuiState(guiState))
					assert(action is ResetAppExplorationAction)

//            val ctx = explorationLog.getState(guiState)
					val record = ActionResult(action, LocalDateTime.now(), LocalDateTime.now())
//                    .apply { this.state = ctx }
					val runnable = RunnableResetAppExplorationAction(action as ResetAppExplorationAction, LocalDateTime.now(), false)
					explorationLog.add(runnable, record)
					strategy.update(record)

					return record
			}

			@JvmStatic
			private fun newResultFromGuiState(guiStatus: IGuiStatus): ActionResult {
					val builder = ExplorationOutput2Builder()
					return builder.buildActionResult(mapOf("guiSnapshot" to UiautomatorWindowDumpTestHelper.fromGuiState(guiStatus),
									"packageName" to ApkFixtures.apkFixture_simple_packageName)).apply {
							//                state = WidgetContext(guiStatus.widgets.map { WidgetInfo.from(it) }, guiStatus, guiStatus.topNodePackageName)
					}
			}

			@JvmStatic
			private fun memoryRecordFromAction(action: ExplorationAction, guiStatus: IGuiStatus): ActionResult {
					return ActionResult(action, LocalDateTime.now(), LocalDateTime.now()).apply {
							//                state = WidgetContext(guiStatus.widgets.map { WidgetInfo.from(it) }, guiStatus, guiStatus.topNodePackageName)
					}
			}

			@JvmStatic
			private fun verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy: IExplorationStrategy, explorationLog: ExplorationContext, gs: IGuiStatus, w: Widget? = null) {
					val action = strategy.decide(newResultFromGuiState(newGuiStateWithWidgets(1)))
					val record = memoryRecordFromAction(action, gs)
					assert(action is ClickExplorationAction)

					val runnable = RunnableWidgetExplorationAction(action as ClickExplorationAction, LocalDateTime.now(), false)
					explorationLog.add(runnable, record)
					strategy.update(record)

					if (w == null)
							assert(true)
					else
							assert(action == newWidgetExplorationAction(w, true))
			}

			@JvmStatic
			private fun verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy: IExplorationStrategy, explorationLog: ExplorationContext, gs: IGuiStatus) {
					val action = strategy.decide(newResultFromGuiState(gs))
					val record = memoryRecordFromAction(action, gs)
					assert(action is TerminateExplorationAction)

					val runnable = RunnableTerminateExplorationAction(action as TerminateExplorationAction, LocalDateTime.now(), false)
					explorationLog.add(runnable, record)
					strategy.update(record)
					assert(action == newTerminateExplorationAction())
			}

			@JvmStatic
			private fun verifyProcessOnGuiStateReturnsResetExplorationAction(strategy: IExplorationStrategy, explorationLog: ExplorationContext, gs: IGuiStatus) {
					val guiStateResult = newResultFromGuiState(gs)
					val action = strategy.decide(guiStateResult)
					assert(action is ResetAppExplorationAction)

					val record = memoryRecordFromAction(action, gs)
					val runnable = RunnableResetAppExplorationAction(action as ResetAppExplorationAction, LocalDateTime.now(), false)
					explorationLog.add(runnable, record)
					strategy.update(record)
					assert(action == newResetAppExplorationAction())
			}

			@JvmStatic
			private fun verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy: IExplorationStrategy, explorationLog: ExplorationContext, gs: IGuiStatus) {
					val action = strategy.decide(newResultFromGuiState(gs))
					assert(action is PressBackExplorationAction)

					val record = memoryRecordFromAction(action, gs)
					val runnable = RunnablePressBackExplorationAction(action as PressBackExplorationAction, LocalDateTime.now(), false)
					explorationLog.add(runnable, record)
					strategy.update(record)
					assert(action == newPressBackExplorationAction())
			}
	}

	@Test
	fun `Given no clickable widgets after app was initialized or reset, attempts ot press back then requests termination`() {
			// Act 1 & Assert
			var explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			var strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, newGuiStateWithTopLevelNodeOnly())
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, newGuiStateWithTopLevelNodeOnly())
			verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, explorationLog, newGuiStateWithTopLevelNodeOnly())

			// Act 2 & Assert
			explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, newGuiStateWithDisabledWidgets(1))
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, newGuiStateWithTopLevelNodeOnly())
			verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, explorationLog, newGuiStateWithDisabledWidgets(1))
	}

	@Test
	fun `Given no clickable widgets during normal exploration, press back, it doesn't work then requests app reset`() {
			val explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			val strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, newGuiStateWithTopLevelNodeOnly())
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, newGuiStateWithTopLevelNodeOnly())
	}

	@Test
	fun `Given other app during normal exploration, requests press back`() {
			// ----- Test 1 -----

			val explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			val strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)

			// Act & assert(1
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, newHomeScreenGuiState())
	}

	@Test
	fun `Given other app or 'home screen' screen during normal exploration, requests press back`() {
			// ----- Test 1 -----

			var explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			var strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)

			// Act & assert(1
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, newHomeScreenGuiState())

			// ----- Test 2 -----

			explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)

			// Act & assert(2
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, newOutOfAppScopeGuiState())
	}


	@Test
	fun `Given 'app has stopped' screen during normal exploration, requests app reset`() {
			val explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			val strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)

			// Act & assert(3
			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, newGuiStateWithWidgets(3, ApkFixtures.apkFixture_simple_packageName))
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, newAppHasStoppedGuiState())
	}

	@Test
	fun `Given 'complete action using' dialog box, requests press back`() {
			val explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			val strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)

			// Act & Assert
			val actionWithGUIState = newCompleteActionUsingGuiState()
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, actionWithGUIState)
	}


	@Test
	fun `If normally would request second app reset in a row, instead terminates exploration, to avoid infinite loop`() {
			val explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			val strategy = getStrategy(explorationLog)
			makeIntoNormalExplorationMode(strategy, explorationLog)

			verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, explorationLog, newAppHasStoppedGuiState())
	}

	@Test
	fun `When exploring forward and configured so, resets exploration every second time`() {
			val explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			val strategy = getStrategy(explorationLog, 5, 2)
			val gs = newGuiStateWithWidgets(3, ApkFixtures.apkFixture_simple_packageName)

			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, gs)
			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, gs)
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, gs)
			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, gs)
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, gs)
			verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, explorationLog, gs)
	}

	@Test
	fun `When exploring forward and configured so, resets exploration every third time`() {
			val explorationLog = ExplorationStrategyTestHelper.getTestExplorationLog(ApkFixtures.apkFixture_simple_packageName)
			val strategy = getStrategy(explorationLog, 10, 3)
			makeIntoNormalExplorationMode(strategy, explorationLog)
			val gs = newGuiStateWithWidgets(3, ApkFixtures.apkFixture_simple_packageName)
			val egs = newGuiStateWithTopLevelNodeOnly()

			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, gs) // 1st exploration forward: widget click
			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, gs) // 2nd exploration forward: widget click
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, gs) // 3rd exploration forward: reset

			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, gs) // 1st exploration forward: widget click
			verifyProcessOnGuiStateReturnsPressBackExplorationAction(strategy, explorationLog, egs) // press back because cannot move forward
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, egs) // reset because cannot move forward
			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, gs) // 1st exploration forward: widget click
			verifyProcessOnGuiStateReturnsWidgetExplorationAction(strategy, explorationLog, gs) // 2nd exploration forward: widget click
			verifyProcessOnGuiStateReturnsResetExplorationAction(strategy, explorationLog, gs) // 3rd exploration forward: reset

			// At this point all 8 actionTrace have been executed.

			verifyProcessOnGuiStateReturnsTerminateExplorationAction(strategy, explorationLog, gs)
	}*/
}