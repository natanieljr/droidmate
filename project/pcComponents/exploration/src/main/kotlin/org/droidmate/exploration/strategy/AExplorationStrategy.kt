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

import kotlinx.coroutines.runBlocking
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.launchApp
import org.droidmate.exploration.strategy.manual.getLogger
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger

abstract class AExplorationStrategy: IActionSelector {
	override val log: Logger = getLogger()

	override suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> hasNext(
		eContext: ExplorationContext<M, S, W>
	): Boolean =
		true

	override suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> nextAction(
		eContext: ExplorationContext<M, S, W>
	): ExplorationAction =
		if(eContext.isEmpty()) eContext.launchApp() else computeNextAction(eContext)

	protected open suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> computeNextAction(
		eContext: ExplorationContext<M, S, W>
	): ExplorationAction = TODO("you have to override either nextAction or computeNextAction in your strategy")

	override fun equals(other: Any?): Boolean =
		other != null
				&& (other as? IActionSelector)?.let{ it.uniqueStrategyName == this.uniqueStrategyName } ?: false

	override fun hashCode(): Int = uniqueStrategyName.hashCode()
}

