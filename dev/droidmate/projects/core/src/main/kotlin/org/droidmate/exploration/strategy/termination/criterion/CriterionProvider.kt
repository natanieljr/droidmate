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
package org.droidmate.exploration.strategy.termination.criterion

import com.google.common.base.Ticker
import org.droidmate.configuration.Configuration
import org.droidmate.exploration.strategy.ITargetWidget
import org.droidmate.exploration.strategy.ITerminationCriterion

/**
 * Builder pattern for termination construction
 *
 * @author Nataniel P. Borges Jr.
 */
object CriterionProvider {
    /**
     * Provider a list of termination criterion based on [configuration][cfg] (nr. of actions and max. time)
     * as well as based on the number of missing targets (based on the [list of available targets][targets])
     */
    fun build(cfg: Configuration, targets: List<ITargetWidget>): List<ITerminationCriterion> {

        val result = ArrayList<ITerminationCriterion>()

        if (cfg.widgetIndexes.isNotEmpty() || cfg.actionsLimit > 0)
            result.add(ActionBasedCriterion(cfg))

        if (cfg.timeLimit > 0)
            result.add(TimeBasedCriterion(cfg.timeLimit, Ticker.systemTicker()))

        if (!targets.isEmpty())
            result.add(SatisfiedBasedCriterion(targets))

        return result
    }
}
