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
package org.droidmate.exploration.strategy.widget

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.strategy.AExplorationStrategy
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

/**
 * Exploration strategy that always clicks "Deny" on runtime permission dialogs.
 */
@Deprecated("to be deleted you should just invke the exploration action directly")
class DenyRuntimePermission : AExplorationStrategy() {
	override fun getPriority(): Int {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun <M: AbstractModel<S, W>,S: State<W>,W: Widget> computeNextAction(
		eContext: ExplorationContext<M, S, W>
	): ExplorationAction {
		val denyButton = eContext.getCurrentState().widgets.let { widgets ->
			widgets.firstOrNull { it.resourceId == "com.android.packageinstaller:id/permission_deny_button" }
					?: widgets.first { it.text.toUpperCase() == "DENY" }
		}

		return denyButton.click()
	}
}