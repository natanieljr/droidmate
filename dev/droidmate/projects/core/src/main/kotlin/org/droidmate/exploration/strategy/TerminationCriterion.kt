// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import org.droidmate.configuration.Configuration
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.TerminateExplorationAction

import java.util.concurrent.TimeUnit

class TerminationCriterion constructor(config: Configuration,
                                       private val timeLimit: Int,
                                       ticker: Ticker?) : ITerminationCriterion {
    private val startingActionsLeft: Int
    private var actionsLeft: Int

    private val timeLimited: Boolean
    private val stopwatch: Stopwatch?

    /** Used when printing out current exploration progress. Expected to match current exploration action ordinal.
     * Starts at 2, because the first exploration action is issued before a request to log is issued.*/
    private var logRequestIndex = 2

    private var currentDecideElapsedSeconds: Long = 0

    init {
        if (timeLimit > 0) {
            timeLimited = true
            assert(ticker != null)
            stopwatch = Stopwatch.createUnstarted(ticker!!)
            actionsLeft = -1
            startingActionsLeft = -1
        } else {
            timeLimited = false

            this.startingActionsLeft = if (config.widgetIndexes.isNotEmpty())
                config.widgetIndexes.size
            else
                config.actionsLimit

            this.actionsLeft = this.startingActionsLeft

            stopwatch = null
        }
    }

    override fun getLogMessage(): String {
        if (timeLimited) {
            val m = (this.currentDecideElapsedSeconds / 60)
            val s = this.currentDecideElapsedSeconds - m * 60
            val lm = (this.timeLimit / 60)
            val ls = this.timeLimit % 60

            return String.format("%3dm %2ds / %3dm %2ds i: %4d", m, s, lm, ls, this.logRequestIndex++)
        } else
            return (this.startingActionsLeft - this.actionsLeft).toString() + "/" + "${this.startingActionsLeft}"
    }

    override fun initDecideCall(firstCallToDecideFinished: Boolean) {
        if (timeLimited) {
            if (firstCallToDecideFinished)
                stopwatch?.start()

            this.currentDecideElapsedSeconds = this.stopwatch?.elapsed(TimeUnit.SECONDS) ?: 0
        } else if (!timeLimited)
            assert(actionsLeft >= 0)

    }

    override fun assertPostDecide(outExplAction: ExplorationAction) {
        if (timeLimited) {
            assert(!met() || (met() && outExplAction is TerminateExplorationAction))
        } else
            assert(actionsLeft >= 0 || (actionsLeft == -1 && outExplAction is TerminateExplorationAction))
    }

    override fun met(): Boolean {
        if (timeLimited) {
            return this.currentDecideElapsedSeconds >= timeLimit
        } else
            return actionsLeft == 0
    }

    override fun metReason(): String {
        if (timeLimited) {
            return "Allocated exploration time exhausted."
        } else
            return "No actions left."
    }

    override fun updateState() {
        if (timeLimited) {
            // Nothing to do here.
        } else {
            assert(met() || actionsLeft > 0)
            actionsLeft--
        }
    }

}
