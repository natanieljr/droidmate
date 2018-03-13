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

import org.droidmate.device.datatypes.IWidget
import java.io.Serializable

/**
 * Information about a widget which has been seen during exploration as part of an [widget context][WidgetContext]
 *
 * @author Nataniel P. Borges Jr.
 */
open class WidgetInfo protected constructor(val widget: IWidget) : Serializable {
    /**
     * Number of times the widget has ben clicked (including checked or unchecked) + long clicked
     */
    var actedUponCount = 0
    /**
     * Number of times the widget has been long clicked
     */
    var longClickedCount = 0
    /**
     * If the widget if blacklisted or not
     */
    var blackListed = false
    /**
     * Probability of having an event attached
     */
    var probabilityHaveEvent = 0.0

    /**
     * Unique identifier of the widget composed of:
     * - Class name
     * - Resource ID
     * - Text (when available)
     * - Description
     * - Bounds
     */
    val uniqueString: String
        get() {
            return if (arrayListOf("Switch", "Toggle").any { widget.className.contains(it) })
                "${widget.className} ${widget.resourceId} ${widget.contentDesc} ${widget.bounds}"
            else
                "${widget.className} ${widget.resourceId} ${widget.text} ${widget.contentDesc} ${widget.bounds}"
        }

    override fun toString(): String {
        return "WI: bl? ${if (blackListed) 1 else 0} act#: $actedUponCount lcc#: $longClickedCount ${widget.toShortString()}"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is WidgetInfo)
            return false

        return this.uniqueString == other.uniqueString
    }

    override fun hashCode(): Int {
        var result = widget.hashCode()
        result = 31 * result + actedUponCount
        result = 31 * result + longClickedCount
        result = 31 * result + blackListed.hashCode()
        return result
    }

    companion object {
        /**
         * Creates a new widget information container from a UI widget
         */
        fun from(widget: IWidget): WidgetInfo {
            return WidgetInfo(widget)
        }

        @JvmStatic
        private val serialVersionUID = 1
    }
}
