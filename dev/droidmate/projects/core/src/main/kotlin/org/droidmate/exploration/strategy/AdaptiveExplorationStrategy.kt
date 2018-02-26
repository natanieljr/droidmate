// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
package org.droidmate.exploration.strategy

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.slf4j.LoggerFactory

/**
 * Adaptive exploration strategy that selects an exploration for a pool
 * of possible strategies based on their fitness for the solution
 *
 * @author Nataniel P. Borges Jr.
 */
class AdaptiveExplorationStrategy(private val strategyPool: IStrategyPool) : IExplorationStrategy {
    private var poolInitialized = false

    private fun initialize() {
        this.strategyPool.initialize()
        this.poolInitialized = true
    }

    override fun decide(result: IExplorationActionRunResult): ExplorationAction {

        logger.debug("decide($result)")


        assert(result.successful)

        if (!this.poolInitialized)
            this.initialize()

        val guiState = result.guiSnapshot.guiState
        val appPackageName = result.exploredAppPackageName

        val selectedAction = this.strategyPool.decide(guiState, appPackageName)

        logger.info("(${this.strategyPool.memory.getSize()}) $selectedAction")

        return selectedAction
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AdaptiveExplorationStrategy::class.java)
    }

}
