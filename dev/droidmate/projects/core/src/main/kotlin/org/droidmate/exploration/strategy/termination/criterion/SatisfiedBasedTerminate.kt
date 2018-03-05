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

import org.droidmate.exploration.strategy.ITargetWidget
import org.droidmate.exploration.strategy.WidgetContext
import org.droidmate.exploration.strategy.termination.Terminate

/**
 * Termination based on the number of non-reached targets
 *
 * @author Nataniel P. Borges Jr.
 */
class SatisfiedBasedTerminate constructor(private val targets: List<ITargetWidget>) : Terminate() {

    override fun met(widgetContext: WidgetContext): Boolean {
        // All widgets have been explored, no need to continue exploration
        return this.unsatisfied.isEmpty()
    }

    override fun start() {
        // Do nothing
    }

    override fun metReason(widgetContext: WidgetContext): String {
        return "All target widgets have been explored"
    }

    init {
        assert(!targets.isEmpty())
    }

    private val unsatisfied: List<ITargetWidget>
        get() = this.targets
                .filter { p -> !p.isSatisfied }
                .toList()


    override fun getLogMessage(): String {
        val unsatisfied = this.unsatisfied
        return "${this.targets.size - unsatisfied.size}/${this.targets.size}"
    }

    override fun equals(other: Any?): Boolean {
        return (other is SatisfiedBasedTerminate) &&
                (other.targets == this.targets)
    }

    override fun hashCode(): Int {
        return targets.hashCode()
    }
}
