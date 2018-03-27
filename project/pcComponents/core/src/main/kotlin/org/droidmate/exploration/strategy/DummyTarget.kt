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

import org.droidmate.device.datatypes.statemodel.Widget

/**
 * Dummy target dependency, used to initialize dependencies as non-null
 *
 * @author Nataniel P. Borges Jr.
 */
class DummyTarget : ITargetWidget {
	override val widget: Widget
		get() = Widget()

	override val dependencies: List<ITargetWidget>
		get() = ArrayList()

	override fun hasDependencies(): Boolean {
		return false
	}

	override fun isDependenciesSatisfied(): Boolean {
		return true
	}

	override val isSatisfied: Boolean
		get() = false

	override fun satisfy(ignoreDependencies: Boolean) {
		// Nothing to do here
	}

	override fun trySatisfyWidgetOrDependency(satisfiedWidget: ITargetWidget) {
		// Do nothing
		return
	}

	override fun getNextWidgetsCanSatisfy(): List<ITargetWidget> {
		return arrayListOf(this)
	}

	override fun getTarget(widget: Widget): ITargetWidget {
		return this
	}
}