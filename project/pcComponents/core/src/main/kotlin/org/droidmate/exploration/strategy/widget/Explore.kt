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

import org.apache.commons.lang3.StringUtils
import org.droidmate.device.datatypes.statemodel.Widget
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy

/**
 * Abstract class for implementing widget exploration strategies.
 *
 * @author Nataniel P. Borges Jr.
 */
abstract class Explore : AbstractStrategy() {

	// region overrides


	override fun mustPerformMoreActions(): Boolean {
		return false
	}

	override fun start() {
		// Nothing to do here.
	}

	override fun internalDecide(): ExplorationAction {
		assert(context.explorationCanMoveOn())

		val allWidgetsBlackListed = context.getCurrentState().actionableWidgets.isEmpty() // || TODO Blacklist
		if (allWidgetsBlackListed)
			this.notifyAllWidgetsBlacklisted()

		return chooseAction()
	}

	// endregion

	// region extensions


	protected fun Widget.getRefinedType(): String {
		return if (VALID_WIDGETS.contains(this.className.toLowerCase()))
			className.toLowerCase()
		else {
			//Get last part
			val parts = className.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			var refType = parts[parts.size - 1].toLowerCase()
			refType = findClosestView(refType)

			refType.toLowerCase()
		}
	}

	protected fun Widget.getActionableParent(): Widget? {
		var parent: Widget? = context.getCurrentState().widgets.find { it.id == this.parentId }
		// Just check for layouts
		while (parent != null && (parent.getRefinedType().contains("layout") || parent.getRefinedType().contains("group"))) {
			if (parent.canBeActedUpon()) {
				return parent
			}

			parent = context.getCurrentState().widgets.find { it.id == parent!!.parentId }
		}

		return null
	}

	// endregion

	private fun findClosestView(target: String): String {
		var distance = Integer.MAX_VALUE
		var closest = ""

		for (compareObject in VALID_WIDGETS) {
			val currentDistance = StringUtils.getLevenshteinDistance(compareObject, target)
			if (currentDistance < distance) {
				distance = currentDistance
				closest = compareObject
			}
		}
		return closest
	}

	protected fun updateState(): Boolean {
		if (!context.belongsToApp(context.getCurrentState())) {
			if (!context.isEmpty()) {
//                this.context.lastTarget.blackListed = true //TODO blacklist missing in current model
				logger.debug("Blacklisted ${this.context.lastTarget}")
			}
		}

		return false //currentState.allWidgetsBlacklisted()
	}

	abstract fun chooseAction(): ExplorationAction
}