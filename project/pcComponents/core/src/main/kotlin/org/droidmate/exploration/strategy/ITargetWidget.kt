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

import org.droidmate.exploration.statemodel.Widget
import java.io.Serializable

/**
 * Target widget to be searched for
 *
 * @author Nataniel P. Borges Jr.
 */
interface ITargetWidget : Serializable {
	/**
	 * UI OldWidget
	 */
	val widget: Widget

	/**
	 * Widgets which should be interacted with before the target should be acted upon
	 */
	val dependencies: List<ITargetWidget>

	/**
	 * Checks if the target has dependencies
	 *
	 * @return If the target has dependencies
	 */
	fun hasDependencies(): Boolean

	/**
	 * Has the target been satisfied?
	 *
	 * @return If target has already been satisfied
	 */
	val isSatisfied: Boolean

	/**
	 * Are all dependencies satisfied?
	 *
	 * @return If target's dependencies have been satisfied
	 */
	fun isDependenciesSatisfied(): Boolean

	/**
	 * Satisfy the target. By default can only be done when all dependencies have been satisfied.
	 * This behavior can be overridden by parameter
	 *
	 * @param ignoreDependencies Ignore check that all dependencies have been satisfied when satisfying the target
	 */
	fun satisfy(ignoreDependencies: Boolean = false)

	/**
	 * Marks the target or one of its dependencies as satisfied given a
	 * [located widget which was satisfied][satisfiedWidget]
	 */
	fun trySatisfyWidgetOrDependency(satisfiedWidget: ITargetWidget)

	/**
	 * Gets the target which represents de [specified widget][widget]
	 *
	 * @return located target. Dummy target if none
	 */
	fun getTarget(widget: Widget): ITargetWidget

	/**
	 * Get the next widget which can be satisfied. This widget can be either the own target or one of its dependencies
	 *
	 * @return List of widgets which could be immediately satisfied
	 */
	fun getNextWidgetsCanSatisfy(): List<ITargetWidget>
}
