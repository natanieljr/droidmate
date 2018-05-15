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

import org.droidmate.exploration.actions.AbstractExplorationAction
import org.droidmate.exploration.actions.WidgetExplorationAction

/**
 * Exploration strategy that always clicks "Allow" on runtime permission dialogs.
 *
 * It has maximum priority (0.99) when it identifies a runtime permission dialog,
 * otherwise its priority is 0.
 */
class AllowRuntimePermission : Explore() {
	override fun chooseAction(): AbstractExplorationAction {
		val allowButton = context.getCurrentState().widgets.let { widgets ->
			widgets.firstOrNull { it.resourceId == "com.android.packageinstaller:id/permission_allow_button" }
					?: widgets.first { it.text.toUpperCase() == "ALLOW" }
		}

		// Remove blacklist restriction from previous action since it will need to be executed again
//        this.context.lastTarget.blackListed = false    //TODO

		return WidgetExplorationAction(allowButton).apply { runtimePermission = true }
	}
}