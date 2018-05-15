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
package org.droidmate.test_tools.device_simulation

import org.droidmate.exploration.ExplorationContext

class GuiScreensBuilderFromApkExplorationOutput2(private val output: ExplorationContext) : IGuiScreensBuilder {
	override fun build(): List<IGuiScreen> {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}
	// TODO Fix tests
	/*override fun build(): List<IGuiScreen> {
			return buildGuiScreens(output)
	}

	private fun buildGuiScreens(output: ExplorationContext): List<IGuiScreen> {
			output.verify()

			var guiScreens = output.guiSnapshots.map {
					assert((it.id in GuiScreen.reservedIds) == !(it.guiState.belongsToApp(output.packageName)))
					GuiScreen(it)
			}

			// Remove duplicate representations of the GuiScreens.
			guiScreens = guiScreens.distinctBy { it.getId() }

			// Ensure the set of GuiScreens contains home screen.
			if (!(guiScreens.any { it.getId() == GuiScreen.idHome })) {
					val home = GuiScreen(GuiScreen.idHome)
					home.buildInternals()
					guiScreens += home
			}

			// Ensure the set of GuiScreens contains chrome screen.
			if (!(guiScreens.any { it.getId() == GuiScreen.idChrome })) {
					val chrome = GuiScreen(GuiScreen.idChrome)
					chrome.buildInternals()
					guiScreens += chrome
			}

			// Obtain references to special Screens.
			val home = guiScreens.single { it.getId() == GuiScreen.idHome }
			val main = guiScreens[0]
			assert(main.getId() !in GuiScreen.reservedIds)
			assert(main.getGuiSnapshot().guiState.belongsToApp(output.packageName))

			guiScreens.forEach {
					it.addHomeScreenReference(home)
					it.addMainScreenReference(main)
			}

			output.logRecords.forEachIndexed { i, action ->

					val explAction = action.getAction().base

					when (explAction) {
					// Do not any transition: all GuiScreens already know how to transition on device actionTrace resulting from
					// this exploration action.
							is ResetAppExplorationAction -> { /* Do nothing */ }
							is WidgetExplorationAction -> addWidgetTransition(guiScreens, i, explAction.widget)
							is EnterTextExplorationAction -> addWidgetTransition(guiScreens, i, explAction.widget)
							is PressBackExplorationAction -> { /* Do nothing */ }
							is TerminateExplorationAction -> {
									assert(i == output.logRecords.size - 1)
									// Do not add any transition: all GuiScreens already know how to transition on device actionTrace resulting from
									// this exploration action.
							}
							else -> throw UnexpectedIfElseFallthroughError(
											"Unsupported ExplorationAction class while extracting transitions from IApkExplorationOutput2. " +
															"The unsupported class: ${explAction.javaClass}")
					}
			}

			guiScreens.forEach { it.verify() }
			return guiScreens
	}

	private fun addWidgetTransition(guiScreens: List<IGuiScreen>, i: Int, widget: Widget) {
			assert(i > 0)
			val sourceScreen = guiScreens.single { output.guiSnapshots[i - 1].id == it.getId() }
			val targetScreen = guiScreens.single { output.guiSnapshots[i].id == it.getId() }
			sourceScreen.addWidgetTransition(widget.id, targetScreen, true)
	}*/
}