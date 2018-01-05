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

package org.droidmate.device.datatypes

import org.droidmate.uiautomator_daemon.guimodel.GuiAction
import org.slf4j.LoggerFactory

class ClickGuiAction constructor(val guiAction: GuiAction) : AndroidDeviceAction() {
    companion object {
        private val log = LoggerFactory.getLogger(ClickGuiAction::class.java)
    }

    override fun toString(): String = "${this.javaClass.simpleName}{$guiAction}"

    fun getSingleMatchingWidget(widgets: List<IWidget>): IWidget {
        //TODO Nataniel Review later
        val x = this.guiAction.clickXCoor
        val y = this.guiAction.clickYCoor

        val matchedWidgets = widgets.filter { it.bounds.contains(x, y) }

        assert(!matchedWidgets.isEmpty(), { "Expected to match at least one widget to coordinates $x $y" })
        if (matchedWidgets.size > 1)
            log.warn("Expected to match at most one widget to coordinates $x $y. " +
                    "Instead matched ${matchedWidgets.size} widgets. " +
                    "The matched widgets bounds:\n" +
                    matchedWidgets.joinToString(System.lineSeparator()) { it.boundsString })

        return matchedWidgets[0]
    }
}