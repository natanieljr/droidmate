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
import org.droidmate.uiautomator_daemon.guimodel.*

abstract class AndroidDeviceAction : IAndroidDeviceAction {

    companion object {

        @JvmStatic
        fun newSwipeGuiDeviceAction(widget: IWidget, direction: Direction): ClickGuiAction {
            val swipe_percentage = 0.8
            val points = widget.getSwipePoints(direction, swipe_percentage)
            val startPoint = points[0]
            val targetPoint = points[1]
            return ClickGuiAction(SwipeAction(_start=Pair(startPoint.x, startPoint.y), _dst=Pair(targetPoint.x, targetPoint.y)))
        }

        @JvmStatic
        fun newClickGuiDeviceAction(clickedWidget: IWidget, longClick: Boolean = false, useCoordinates: Boolean): ClickGuiAction {
            return if (longClick)
                ClickGuiAction(LongClickAction(clickedWidget.xpath, clickedWidget.resourceId))
            else
                ClickGuiAction(ClickAction(clickedWidget.xpath, clickedWidget.resourceId))
        }

//        @JvmStatic
//        fun newClickGuiDeviceAction(p: Point, longClick: Boolean = false): ClickAction
//                = newClickGuiDeviceAction(p.x, p.y, longClick)
//
//        @JvmStatic
//        fun newClickGuiDeviceAction(clickX: Int, clickY: Int, longClick: Boolean = false): LongClickAction
//                = ClickAction(Action(clickX, clickY, longClick))

        @JvmStatic
        fun newEnterTextDeviceAction(resourceId: String, textToEnter: String): ClickGuiAction
                = ClickGuiAction(TextAction(xPath = "",resId = resourceId,text = textToEnter))

        @JvmStatic
        fun newPressBackDeviceAction(): ClickGuiAction
                = ClickGuiAction(PressBack())

        @JvmStatic
        fun newPressHomeDeviceAction(): ClickGuiAction
                = ClickGuiAction(PressHome())

        @JvmStatic
        fun newTurnWifiOnDeviceAction(): ClickGuiAction
                = ClickGuiAction(EnableWifi())

        @JvmStatic
        fun newLaunchAppDeviceAction(iconLabel: String): ClickGuiAction
                = ClickGuiAction(LaunchApp(iconLabel))
    }
}
