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

package org.droidmate.device.datatypes

import org.droidmate.exploration.actions.Direction
import java.awt.Point
import java.awt.Rectangle


open class Widget @JvmOverloads constructor(override var id: String = "",
                                            override var index: Int = -1,
                                            override var text: String = "",
                                            override var resourceId: String = "",
                                            override var className: String = "",
                                            override var packageName: String = "",
                                            override var contentDesc: String = "",
                                            override var xpath: String = "",
                                            override var checkable: Boolean = false,
                                            override var checked: Boolean = false,
                                            override var clickable: Boolean = false,
                                            override var enabled: Boolean = false,
                                            override var focusable: Boolean = false,
                                            override var focused: Boolean = false,
                                            override var scrollable: Boolean = false,
                                            override var longClickable: Boolean = false,
                                            override var password: Boolean = false,
                                            override var selected: Boolean = false,
                                            override var bounds: Rectangle = Rectangle(),
                                            override var parent: IWidget? = null) : IWidget {
    /** Id is used only for tests, for:
     * - easy determination by human which widget is which when looking at widget string representation
     * - For asserting actual widgets match expected.
     * */

    companion object {
        private const val serialVersionUID: Long = 1

        val ONTOUCH_DISPLAY_RELATION = 0.7

        /**
         * <p>
         * Parses into a {@link java.awt.Rectangle} the {@code bounds} string, having format as output by
         * {@code android.graphics.Rect #toShortString(java.lang.StringBuilder)},
         * that is having form {@code [Xlow ,Ylow][Xhigh,Yhigh]}
         *
         * </p><p>
         * Such rectangle bounds format is being used internally by<br/>
         * {@code com.android.uiautomator.core.UiDevice #dumpWindowHierarchy(java.lang.String)}
         *
         * </p>
         */
        @JvmStatic
        fun parseBounds(bounds: String): Rectangle {
            assert(bounds.isNotEmpty())

            // The input is of form "[xLow,yLow][xHigh,yHigh]" and the regex below will capture four groups: xLow yLow xHigh yHigh
            val boundsMatcher = Regex("\\[(-?\\p{Digit}+),(-?\\p{Digit}+)\\]\\[(-?\\p{Digit}+),(-?\\p{Digit}+)\\]")
            val foundResults = boundsMatcher.findAll(bounds).toList()
            if (foundResults.isEmpty())
                throw InvalidWidgetBoundsException("The window hierarchy bounds matcher was unable to match $bounds against the regex")

            val matchedGroups = foundResults[0].groups

            val lowX = matchedGroups[1]!!.value.toInt()
            val lowY = matchedGroups[2]!!.value.toInt()
            val highX = matchedGroups[3]!!.value.toInt()
            val highY = matchedGroups[4]!!.value.toInt()

            return Rectangle(lowX, lowY, highX - lowX, highY - lowY)
        }
    }

    /* WISH this actually shouldn't be necessary as the [dump] call is supposed to already return only the visible part, as
      it makes call to [visible-bounds] to obtain the "bounds" property of widget. I had problems with negative coordinates in
      com.indeed.android.jobsearch in.
      [dump] com.android.uiautomator.core.UiDevice.dumpWindowHierarchy
      [visible-bounds] com.android.uiautomator.core.AccessibilityNodeInfoHelper.getVisibleBoundsInScreen
      */
    /**
     * The widget is associated with a rectangle representing visible device display. This is the same visible display from whose
     * GUI structure this widget was parsed.
     *
     * The field is necessary to determine if at least one pixel of the widget is within the visible display and so, can be clicked.
     *
     * Later on DroidMate might add the ability to scroll first to make invisible widgets visible.
     */
    override var deviceDisplayBounds: Rectangle? = null

    override fun center(): Point
            = Point(bounds.centerX.toInt(), bounds.centerY.toInt())

    override fun toString(): String =
            "Widget: $className ResourceID: $resourceId, text: $text, $boundsString, clickable: $clickable enabled: $enabled checkable: $checkable deviceDisplayBounds: [x=${deviceDisplayBounds?.x},y=${deviceDisplayBounds?.y},dx=${deviceDisplayBounds?.width},dy=${deviceDisplayBounds?.height}]"

    override val boundsString: String
        get() = "[x=${bounds.x},y=${bounds.y},dx=${bounds.width},dy=${bounds.height}]"

    override fun getStrippedResourceId(): String
            = this.resourceId.removePrefix(this.packageName + ":")

    override fun toShortString(): String {
        val classSimpleName = className.substring(className.lastIndexOf(".") + 1)
        return "Wdgt:$classSimpleName/\"$text\"/\"$resourceId\"/[${bounds.centerX.toInt()},${bounds.centerY.toInt()}]"
    }

    override fun toTabulatedString(includeClassName: Boolean): String {
        val classSimpleName = className.substring(className.lastIndexOf(".") + 1)
        val pCls = classSimpleName.padEnd(20, ' ')
        val pResId = resourceId.padEnd(64, ' ')
        val pText = text.padEnd(40, ' ')
        val pContDesc = contentDesc.padEnd(40, ' ')
        val px = "${bounds.centerX.toInt()}".padStart(4, ' ')
        val py = "${bounds.centerY.toInt()}".padStart(4, ' ')

        val clsPart = if (includeClassName) "Wdgt: $pCls / " else ""

        return "${clsPart}resId: $pResId / text: $pText / contDesc: $pContDesc / click xy: [$px,$py]"
    }

    override fun canBeActedUpon(): Boolean {
        return this.enabled && (this.clickable || this.checkable || this.longClickable || this.scrollable) && isVisibleOnCurrentDeviceDisplay()
    }

    override fun isVisibleOnCurrentDeviceDisplay(): Boolean {
        if (deviceDisplayBounds == null)
            return true

        return bounds.intersects(deviceDisplayBounds)
    }

    override fun getClickPoint(): Point {
        if (deviceDisplayBounds == null) {
            val center = this.center()
            return Point(center.x, center.y)
        }

        assert(bounds.intersects(deviceDisplayBounds))

        val clickRectangle = bounds.intersection(deviceDisplayBounds)

        return Point(clickRectangle.centerX.toInt(), clickRectangle.centerY.toInt())
    }

    override fun getAreaSize(): Double {
        return bounds.height.toDouble() * bounds.width
    }

    override fun getDeviceAreaSize(): Double {
        return if (deviceDisplayBounds != null)
            deviceDisplayBounds!!.height.toDouble() * deviceDisplayBounds!!.width
        else
            -1.0
    }

    override fun getResourceIdName(): String {
        return this.resourceId.removeSuffix(this.packageName + ":id/")
    }

    override fun getSwipePoints(direction: Direction, percent: Double): List<Point> {

        assert(bounds.intersects(deviceDisplayBounds))

        val swipeRectangle = bounds.intersection(deviceDisplayBounds)
        val offsetHor = (swipeRectangle.getWidth() * (1 - percent)) / 2
        val offsetVert = (swipeRectangle.getHeight() * (1 - percent)) / 2

        when (direction) {
            Direction.LEFT -> return arrayListOf(Point((swipeRectangle.maxX - offsetHor).toInt(), swipeRectangle.centerY.toInt()),
                    Point((swipeRectangle.minX + offsetHor).toInt(), swipeRectangle.centerY.toInt()))
            Direction.RIGHT -> return arrayListOf(Point((this.bounds.minX + offsetHor).toInt(), this.bounds.getCenterY().toInt()),
                    Point((this.bounds.getMaxX() - offsetHor).toInt(), this.bounds.getCenterY().toInt()))
            Direction.UP -> return arrayListOf(Point(this.bounds.centerX.toInt(), (this.bounds.maxY - offsetVert).toInt()),
                    Point(this.bounds.centerX.toInt(), (this.bounds.getMinY() + offsetVert).toInt()))
            Direction.DOWN -> return arrayListOf(Point(this.bounds.centerX.toInt(), (this.bounds.getMinY() + offsetVert).toInt()),
                    Point(this.bounds.centerX.toInt(), (this.bounds.maxY - offsetVert).toInt()))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Widget)
            return false

        return this.index == other.index &&
                this.text == other.text &&
                this.resourceId == other.resourceId &&
                this.className == other.className &&
                this.packageName == other.packageName &&
                this.contentDesc == other.contentDesc &&
                this.checkable == other.checkable &&
                this.checked == other.checked &&
                this.clickable == other.clickable &&
                this.enabled == other.enabled &&
                this.focusable == other.focusable &&
                this.focused == other.focused &&
                this.scrollable == other.scrollable &&
                this.longClickable == other.longClickable &&
                this.password == other.password &&
                this.selected == other.selected &&
                this.bounds == other.bounds &&
                this.parent == other.parent
    }

    override fun hashCode(): Int {
        var result = index.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + resourceId.hashCode()
        result = 31 * result + className.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + contentDesc.hashCode()
        result = 31 * result + checkable.hashCode()
        result = 31 * result + checked.hashCode()
        result = 31 * result + clickable.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + focusable.hashCode()
        result = 31 * result + focused.hashCode()
        result = 31 * result + scrollable.hashCode()
        result = 31 * result + longClickable.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + selected.hashCode()
        result = 31 * result + bounds.hashCode()
        result = 31 * result + (parent?.hashCode() ?: 0)
        result = 31 * result + (deviceDisplayBounds?.hashCode() ?: 0)
        return result
    }
}
