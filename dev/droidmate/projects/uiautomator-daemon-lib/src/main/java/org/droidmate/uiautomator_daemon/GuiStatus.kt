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

package org.droidmate.uiautomator_daemon

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

open class GuiStatus protected constructor(final override val windowHierarchyDump: String,
                                           final override val topNodePackageName: String,
                                           override val widgets: List<WidgetData>,
                                           final override val androidLauncherPackageName: String,
                                           final override val deviceDisplayWidth: Int,
                                           final override val deviceDisplayHeight: Int) : IGuiStatus {
    companion object {
        private const val serialVersionUID: Long = 1

        @JvmStatic
        private fun createWidget(n: Node, parentWidget: WidgetData?): WidgetData? {
            try {
                /*Example "n": <node index="0" text="LOG IN" resource-uid="com.snapchat.android:uid/landing_page_login_button" class="android.widget.Button" package="com.snapchat.android" content-contentDesc="" check="false" check="false" clickable="true" enabled="true" focusable="true" focus="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,949][800,1077]"/>*/
                val getBool: (property: String) -> Boolean = { n.attributes.getNamedItem(it)?.nodeValue == "true" }
                val getStringVal: (property: String) -> String = { n.attributes.getNamedItem(it)?.nodeValue ?: "" }
                val boundsList = WidgetData.parseBounds(n.attributes.getNamedItem("bounds").nodeValue)

                return WidgetData(mapOf(
                        WidgetData::id.name to getStringVal("id"), // Appears only in test code simulating the device, never on actual devices or their emulators.
                        WidgetData::text.name to getStringVal("text"),
                        WidgetData::resourceId.name to getStringVal("resource-id"),
                        WidgetData::className.name to getStringVal("class"),
                        WidgetData::packageName.name to getStringVal("package"),
                        WidgetData::contentDesc.name to getStringVal("content-contentDesc"),
                        WidgetData::checked.name to (if (getBool("checkable")) getBool("checked") else null),
                        WidgetData::clickable.name to getBool("clickable"),
                        WidgetData::enabled.name to getBool("enabled"),
                        WidgetData::focused.name to (if (getBool("focusable")) getBool("focused") else null),
                        WidgetData::scrollable.name to getBool("scrollable"),
                        WidgetData::longClickable.name to getBool("long-clickable"),
                        WidgetData::visible.name to getBool("visible-to-user"),  // TODO check invisible nodes are probably never in the dump anyway
                        WidgetData::isPassword.name to getBool("password"),
                        WidgetData::selected.name to getBool("selected"),
                        WidgetData::boundsX.name to boundsList[0],
                        WidgetData::boundsY.name to boundsList[1],
                        WidgetData::boundsWidth.name to boundsList[2],
                        WidgetData::boundsHeight.name to boundsList[3],
                        WidgetData::isLeaf.name to n.childNodes.toList().none { it.nodeName == "node" }
                ), n.attributes.getNamedItem("index")?.nodeValue?.toInt() ?: -1, parentWidget)

            } catch (e: InvalidWidgetBoundsException) {
                // TODO Check log.error("Catching exception: parsing widget bounds failed. Please see the exceptions log for details.\n" +
                // TODO		"Continuing execution, skipping the widget with empty bounds.")
                // TODO Check log.error(exceptions, "parsing widget bounds failed with exception:\n", e)
                return null
            }
        }

        @JvmStatic
        private fun addWidget(result: MutableList<WidgetData>, parent: WidgetData?, data: Node) {
            val w = createWidget(data, parent)

            if (w != null) {
                if (parent == null)
                    w.xpath = "//"
                else
                    w.xpath = "${parent.xpath}/"

                w.xpath += "${w.className}[${w.index + 1}]"

                result.add(w)
            }

            data.childNodes.toList().filter { it.nodeName == "node" }.forEach { addWidget(result, w, it) }
        }

        @JvmStatic
        private fun NodeList.toList(): List<Node> {
            return (0 until this.length).map { i ->
                this.item(i)
            }
        }

        @JvmStatic
        private fun Node.isSystemUINode(): Boolean {
            val pkg = this.attributes.getNamedItem("package")

            return (pkg != null) && (pkg.nodeValue.contains("com.android.systemui"))
        }

        fun fromUIDump(windowHierarchyDump: String, androidLauncherPackageName: String, displayWidth: Int, displayHeight: Int): GuiStatus {
            val dbf = DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val inputStream = ByteArrayInputStream(windowHierarchyDump.toByteArray())
            val hierarchy = db.parse(inputStream)
                    .apply { documentElement.normalize() }
                    .childNodes.item(0)

            assert(hierarchy.nodeName == "hierarchy")

            val childNodes = hierarchy.childNodes
                    .toList()
                    .filter { it.nodeName == "node" }
                    .filterNot { it.isSystemUINode() }

            var topNodePackage = "ERROR"
            if (childNodes.isNotEmpty()) {
                topNodePackage = childNodes.first().attributes.getNamedItem("package").nodeValue

                // When the application starts with an active keyboard, look for the proper application instead of the keyboard
                // This problem was identified on the app "com.hykwok.CurrencyConverter"
                // https://f-droid.org/repository/browse/?fdfilter=CurrencyConverter&fdid=com.hykwok.CurrencyConverter
                if (topNodePackage.startsWith("com.google.android.inputmethod.") && (childNodes.size > 1))
                    topNodePackage = childNodes.drop(1).first().attributes.getNamedItem("package").nodeValue

                assert(topNodePackage.isNotEmpty())
            }
            val widgets: MutableList<WidgetData> = ArrayList()

            childNodes.forEach {
                addWidget(widgets, null, it)
            }

            return GuiStatus(windowHierarchyDump, topNodePackage, widgets, androidLauncherPackageName, displayWidth, displayHeight)
        }
    }

    private val androidPackageName = "android"
    private val resIdRuntimePermissionDialog = "com.android.packageinstaller:id/dialog_container"

    init {
        assert(!this.topNodePackageName.isEmpty())
    }

    override fun toString(): String {
        return when {
            this.isHomeScreen -> "<GUI state: home screen>"
            this.isAppHasStoppedDialogBox -> "<GUI state of \"App has stopped\" dialog box.>"// OK widget enabled: ${this.okWidget.enabled}>"
            this.isRequestRuntimePermissionDialogBox -> "<GUI state of \"Runtime permission\" dialog box.>"// Allow widget enabled: ${this.allowWidget.enabled}>"
            this.isCompleteActionUsingDialogBox -> "<GUI state of \"Complete action using\" dialog box.>"
            this.isSelectAHomeAppDialogBox -> "<GUI state of \"Select a home app\" dialog box.>"
            this.isUseLauncherAsHomeDialogBox -> "<GUI state of \"Use Launcher as Home\" dialog box.>"
            else -> "<GuiState pkg=$topNodePackageName Widgets count = ${widgets.size}>"
        }
    }

    override val isHomeScreen: Boolean
        get() = topNodePackageName.startsWith(androidLauncherPackageName) && !widgets.any { it.text == "Widgets" }

    override val isAppHasStoppedDialogBox: Boolean
        get() = topNodePackageName == androidPackageName &&
                widgets.any { it.text == "OK" } &&
                !widgets.any { it.text == "Just once" }

    override val isCompleteActionUsingDialogBox: Boolean
        get() = !isSelectAHomeAppDialogBox &&
                !isUseLauncherAsHomeDialogBox &&
                topNodePackageName == androidPackageName &&
                widgets.any { it.text == "Just once" }

    override val isSelectAHomeAppDialogBox: Boolean
        get() = topNodePackageName == androidPackageName &&
                widgets.any { it.text == "Just once" } &&
                widgets.any { it.text == "Select a Home app" }

    override val isUseLauncherAsHomeDialogBox: Boolean
        get() = topNodePackageName == androidPackageName &&
                widgets.any { it.text == "Use Launcher as Home" } &&
                widgets.any { it.text == "Just once" } &&
                widgets.any { it.text == "Always" }

    override val isRequestRuntimePermissionDialogBox: Boolean
        get() = widgets.any { it.resourceId == resIdRuntimePermissionDialog }

    override fun belongsToApp(appPackageName: String): Boolean = this.topNodePackageName == appPackageName

    override fun debugWidgets(): String {

        val sb = StringBuilder()
        sb.appendln("widgets (${widgets.size}):")
        widgets.forEach { sb.appendln(it.toString()) }

        return sb.toString()
    }
}
