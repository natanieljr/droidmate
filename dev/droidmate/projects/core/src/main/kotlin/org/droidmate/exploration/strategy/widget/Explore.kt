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
package org.droidmate.exploration.strategy.widget

import org.apache.commons.lang3.StringUtils
import org.droidmate.device.datatypes.IWidget
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.exploration.strategy.WidgetContext
import org.droidmate.exploration.strategy.WidgetInfo
import org.droidmate.misc.uniqueString

//import org.droidmate.report.uniqueString

/**
 * Abstract class for implementing widget exploration strategies.
 *
 * @author Nataniel P. Borges Jr.
 */
abstract class Explore : AbstractStrategy() {

    // region overrides

    override fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean {
        return false
    }

    override fun start() {
        // Nothing to do here.
    }

    override fun internalDecide(widgetContext: WidgetContext): ExplorationAction {
        assert(widgetContext.explorationCanMoveForwardOn())

        val allWidgetsBlackListed = this.updateState(widgetContext)
        if (allWidgetsBlackListed)
            this.notifyAllWidgetsBlacklisted()

        return chooseAction(widgetContext)
    }

    // endregion

    // region extensions

    private fun addWidgets(result: MutableList<WidgetInfo>, widgetInfo: WidgetInfo, widgetContext: WidgetContext) {
        val widget = widgetInfo.widget

        // Does not check can be acted upon because it was checked on the parent
        if (widget.isVisibleOnCurrentDeviceDisplay() && widget.enabled) {
            if (!result.any { it.widget == widget })
                result.add(widgetInfo)

            // Add children
            widgetContext.widgetsInfo
                    .filter { it.widget.parent == widget }
                    .forEach { addWidgets(result, it, widgetContext) }
        }
    }

    fun WidgetContext.getActionableWidgetsInclChildren(): List<WidgetInfo> {
        val result = ArrayList<WidgetInfo>()

        this.widgetsInfo
                .filter { !it.blackListed }
                .filter { it.widget.canBeActedUpon() }
                .forEach { p -> addWidgets(result, p, this) }

        return result
    }

    protected fun IWidget.getRefinedType(): String {
        return if (VALID_WIDGETS.contains(this.className.toLowerCase()))
            className.toLowerCase()
        else {
            //Get last part
            val parts = className.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var refType = parts[parts.size - 1].toLowerCase()
            refType = findClosestView(refType)

            refType.toLowerCase()
        }
    }

    protected fun IWidget.isEquivalent(other: IWidget): Boolean {
        return this.uniqueString == other.uniqueString
    }

    protected fun IWidget.getActionableParent(): IWidget? {
        var parent: IWidget? = this.parent
        // Just check for layouts
        while (parent != null && (parent.getRefinedType().contains("layout") || parent.getRefinedType().contains("group"))) {
            if (parent.canBeActedUpon()) {
                return this.parent
            }

            parent = parent.parent
        }

        return null
    }

    // endregion

    private fun findClosestView(target: String): String {
        var distance = Integer.MAX_VALUE
        var closest = ""

        for (compareObject in VALID_WIDGETS) {
            val currentDistance = StringUtils.getLevenshteinDistance(compareObject, target)
            if (currentDistance < distance) {
                distance = currentDistance
                closest = compareObject
            }
        }
        return closest
    }

    protected fun updateState(widgetContext: WidgetContext): Boolean {
        widgetContext.seenCount += 1

        if (widgetContext.seenCount == 1)
            logger.debug("Encountered a NEW widget context:\n${widgetContext.uniqueString}")
        else
            logger.debug("Encountered an existing widget context:\n${widgetContext.uniqueString}")

        if (!widgetContext.belongsToApp()) {
            if (!memory.isEmpty()) {
                this.memory.lastWidgetInfo.blackListed = true
                logger.debug("Blacklisted ${this.memory.lastWidgetInfo}")
            }
        }

        return widgetContext.allWidgetsBlacklisted()
    }

    abstract fun chooseAction(widgetContext: WidgetContext): ExplorationAction
}