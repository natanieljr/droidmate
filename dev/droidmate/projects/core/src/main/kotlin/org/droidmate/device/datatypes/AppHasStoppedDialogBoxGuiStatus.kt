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

import org.droidmate.device.datatypes.statemodel.WidgetData
import java.awt.Rectangle

/**
 * Specialized GuiStatus class that represents an application with an active "App has stopped" dialog box
 */
@Deprecated("We only need one GuiStatus class no reason for this nearly empty subclasses")
class AppHasStoppedDialogBoxGuiStatus(topNodePackageName: String, widgets: List<WidgetData>, androidLauncherPackageName: String, deviceDisplayBounds: Rectangle) :
        GuiStatus(topNodePackageName, "", widgets, androidLauncherPackageName,deviceDisplayBounds) {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    val okWidget: WidgetData
        get() = this.widgets.first { it.text == "OK" }
}
