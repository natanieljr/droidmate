// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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

package org.droidmate.test_tools.exploration.strategy

import org.droidmate.configuration.Configuration
import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.exploration.data_aggregators.ExplorationContext
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.exploration.strategy.IExplorationStrategy
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.exploration.strategy.reset.AppCrashedReset
import org.droidmate.exploration.strategy.reset.CannotExploreReset
import org.droidmate.exploration.strategy.reset.InitialReset
import org.droidmate.exploration.strategy.reset.IntervalReset
import org.droidmate.test_tools.android_sdk.ApkTestHelper
import org.droidmate.test_tools.configuration.ConfigurationForTests

class ExplorationStrategyTestHelper {
    companion object {
        @JvmStatic
        fun buildStrategy(explorationLog: AbstractContext, actionsLimit: Int, resetEveryNthExplorationForward: Int): IExplorationStrategy {
            val cfg = ConfigurationForTests().apply {
                setArg(arrayListOf(Configuration.pn_actionsLimit, "$actionsLimit"))
                setArg(arrayListOf(Configuration.pn_resetEveryNthExplorationForward, "$resetEveryNthExplorationForward"))
            }.get()

            return ExplorationStrategyPool.build(explorationLog, cfg)
        }

        @JvmStatic
        fun getTestExplorationLog(packageName: String): AbstractContext {
            val testApk = ApkTestHelper.build(packageName, ".", "", "")
            return ExplorationContext(testApk)
        }

        @JvmStatic
        fun getResetStrategies(cfg: Configuration): List<ISelectableExplorationStrategy>{
            val strategies : MutableList<ISelectableExplorationStrategy> = ArrayList()
            strategies.add(InitialReset())
            strategies.add(AppCrashedReset())
            strategies.add(CannotExploreReset())

            // Interval reset
            if (cfg.resetEveryNthExplorationForward > 0)
                strategies.add(IntervalReset(cfg.resetEveryNthExplorationForward))

            return strategies
        }


    }
}
