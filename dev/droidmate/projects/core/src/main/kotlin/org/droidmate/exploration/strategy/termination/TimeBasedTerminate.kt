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
package org.droidmate.exploration.strategy.termination

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import org.droidmate.device.datatypes.statemodel.ActionResult
import org.droidmate.exploration.strategy.WidgetContext
import java.util.concurrent.TimeUnit

/**
 * Determines if exploration shall be terminated based on the based on the elapsed time
 *
 * @author Nataniel P. Borges Jr.
 */
class TimeBasedTerminate @JvmOverloads constructor(private val timeLimit: Int,
                                                   private val ticker: Ticker = Ticker.systemTicker()) : Terminate() {
    private var stopwatch: Stopwatch = Stopwatch.createUnstarted(ticker)
    private var currentDecideElapsedSeconds: Long = 0

    /**
     * Used when printing out current exploration progress. Expected to match current exploration action ordinal.
     * Starts at 2, because the first exploration action is issued before a request to log is issued.
     */
    private var logRequestIndex = 2

    /**
     * Reset the time, used only in unit tests
     */
    internal fun resetClock() {
        stopwatch = Stopwatch.createUnstarted(ticker)
        stopwatch.start()
    }

    override fun getLogMessage(): String {
        val m = this.currentDecideElapsedSeconds / 60
        val s = this.currentDecideElapsedSeconds - m * 60
        val lm = (this.timeLimit / 60).toLong()
        val ls = (this.timeLimit % 60).toLong()

        return String.format("%3dm %2ds / %3dm %2ds i: %4d", m, s, lm, ls, this.logRequestIndex++)
    }

    override fun start() {
        stopwatch.start()
    }

    override fun met(widgetContext: WidgetContext): Boolean {
        return this.currentDecideElapsedSeconds >= timeLimit
    }

    override fun metReason(widgetContext: WidgetContext): String {
        return "Allocated exploration time exhausted."
    }

    override fun updateState(actionNr: Int, record: ActionResult) {
        super.updateState(actionNr, record)

        this.currentDecideElapsedSeconds = this.stopwatch.elapsed(TimeUnit.SECONDS)
    }

    override fun equals(other: Any?): Boolean {
        return (other is TimeBasedTerminate) &&
                (other.timeLimit == this.timeLimit)
    }
}