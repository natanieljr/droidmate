// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Saarland University
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
package org.droidmate.exploration.strategy.back

import org.droidmate.device.datatypes.statemodel.Widget
import org.droidmate.exploration.strategy.StrategyPriority

/**
 * Receives a set of constraints based on widgets and triggers a back is all constraints are satisfied.
 *
 * Scenario (based on sample of Google ads):
 * - "On a screen with a View whose resID is 'ad_Container' and which contains a Button with resID 'close_button',
 *    press back"
 */
@Suppress("unused")
class WidgetConstraintsBack(private val selectors: List<(Widget) -> Boolean>) : Back() {


	override fun getFitness(): StrategyPriority {
		val satisfyConstraints = selectors.all { selector ->
			context.getCurrentState().widgets.any { selector(it) }
		}

		return if (satisfyConstraints)
			StrategyPriority.SPECIFIC_WIDGET
		else
			StrategyPriority.NONE
	}

	override fun equals(other: Any?): Boolean {
		return other != null &&
				other is WidgetConstraintsBack &&
				other.selectors == this.selectors
	}

	override fun hashCode(): Int {
		var result = super.hashCode()
		result = 31 * result + selectors.hashCode()
		return result
	}
}