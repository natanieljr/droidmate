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

import org.droidmate.exploration.actions.Direction
import java.awt.Point
import java.awt.Rectangle
import java.io.Serializable

interface IWidget : Serializable {
    var id: String
    var xpath: String
    var index: Int
    var text: String
    var resourceId: String
    var className: String
    var packageName: String
    var contentDesc: String
    var checkable: Boolean
    var checked: Boolean
    var clickable: Boolean
    var enabled: Boolean
    var focusable: Boolean
    var focused: Boolean
    var scrollable: Boolean
    var longClickable: Boolean
    var password: Boolean
    var selected: Boolean
    var bounds: Rectangle
    var parent: IWidget?
    /**
     * The widget is associated with a rectangle representing visible device display. This is the same visible display from whose
     * GUI structure this widget was parsed.
     *
     * The field is necessary to determine if at least one pixel of the widget is within the visible display and so, can be clicked.
     *
     * Later on DroidMate might add the ability to scroll first to make invisible widgets visible.
     */
    var deviceDisplayBounds: Rectangle?
    val boundsString: String

    fun center(): Point
    fun getStrippedResourceId(): String
    fun toShortString(): String
    fun toTabulatedString(includeClassName: Boolean = true): String
    fun canBeActedUpon(): Boolean
    fun isVisibleOnCurrentDeviceDisplay(): Boolean
    fun getClickPoint(): Point
    fun getAreaSize(): Double
    fun getDeviceAreaSize(): Double
    fun getResourceIdName(): String
    fun getSwipePoints(direction: Direction, percent: Double): List<Point>
}