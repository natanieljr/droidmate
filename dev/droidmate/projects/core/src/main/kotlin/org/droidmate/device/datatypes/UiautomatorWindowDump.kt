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

import org.droidmate.logging.LogbackConstants
import org.droidmate.logging.Markers.Companion.exceptions
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.awt.Dimension
import java.awt.Rectangle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory

/**
 * <p>
 * <i> --- This doc was last reviewed on 04 Jan 2014.</i>
 * </p><p>
 *
 * Represents the GUI snapshot returned by uiautomator.
 *
 * </p><p>
 *
 * About uiautomator dump:<br/>
 * http://developer.android.com/tools/help/uiautomator/index.html#table1
 *
 * </p><p>
 *
 * Example bash scripts on how to get the dump on Windows:<br/>
 *
 * <pre>
 * function dump_gui {*   adb shell uiautomator dump # should dump to: /storage/emulated/legacy/window_dump.xml
 *   adb pull storage/emulated/legacy/window_dump.xml ./window_dump.xml
 *}*
 * # pre-condition: a device is running and connected through adb.
 * function vis_dump_gui {*   echo "After the GUI starts, please select the device to the left and click on the appropriate
 * button above it"
 *   echo "to dump the GUI."
 *   $COMSPEC /c monitor.bat
 *}</pre>
 * The monitor.bat from examples above should live in path like:<br/>
 * c:\Program Files (x86)\Android\android-sdk\tools\monitor.bat
 */
data class UiautomatorWindowDump @JvmOverloads constructor(override val windowHierarchyDump: String,
                                                           private val displayDimensions: Dimension,
                                                           override val androidLauncherPackageName: String,
                                                           override val id: String = "") : IDeviceGuiSnapshot, Serializable {
    private enum class WellFormedness {

        OK,
        is_null,
        is_empty,
        missing_root_xml_node_prefix;

        fun toValidationResult(): ValidationResult {
            when (this) {
                OK -> assert(false, { "Called .toValidatonResult() on 'OK' well-formedness status. This is forbidden, as 'OK' well-formedness is not enough by itself to determine validation result." })
                is_null -> return ValidationResult.is_null
                is_empty -> ValidationResult.is_empty
                missing_root_xml_node_prefix -> return ValidationResult.missing_root_xml_node_prefix
            }

            assert(false, { "This statement should be unreachable code!" })
            return ValidationResult.error// To make compiler happy
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1
        private val log = LoggerFactory.getLogger(UiautomatorWindowDump::class.java)

        val rootXmlNodePrefix = "<hierarchy"

        /**
         * THIS FUNCTION IS BROKEN, DO NOT USE IT. See below.
         *
         * DroidMate obtains Android device's window hierarchy dump via
         * android.support.test.uiautomator.UiDevice#dumpWindowHierarchy(java.io.File)
         * called in org.droidmate.uiautomator2daemon.UiAutomatorDaemonDriver
         *
         * This dump contains as first children of the <hierarchy> node some nodes with com.android.systemui package.
         * They are deleted by this function. Interestingly, they are not present if the window hierarchy is obtained with monitor tool
         * from Android SDK.
         *
         * Implemented with help of:
         * http://stackoverflow.com/a/3717875/986533
         * https://docs.oracle.com/javase/tutorial/jaxp/xslt/xpath.html
         * http://www.xpathtester.com/xpath
         * https://docs.oracle.com/javase/7/docs/api/javax/xml/parsers/package-summary.html
         * https://docs.oracle.com/javase/7/docs/api/javax/xml/xpath/package-summary.html
         *
         */
        @JvmStatic
        fun removeSystemuiNodes(windowHierarchyDump: String): String {
            val doc: Document = getDumpDocument(windowHierarchyDump)
            val nodesToRemove: NodeList = getNodesToRemove(doc)

            for (i in 0 until nodesToRemove.length) {
                removeNode(nodesToRemove.item(i))
            }

            return writeDocToString(doc)
        }

        private fun getDumpDocument(windowHierarchyDump: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(windowHierarchyDump.byteInputStream())

        private fun getNodesToRemove(windowHierarchyDumpDocument: Document): NodeList {
            val nodesToRemoveXpath: XPathExpression = XPathFactory.newInstance().newXPath().compile("/hierarchy/node[@package=\"com.android.systemui\"]")
            val nodesToRemove: NodeList = nodesToRemoveXpath.evaluate(windowHierarchyDumpDocument, XPathConstants.NODESET) as NodeList
            return nodesToRemove
        }

        private fun removeNode(currNodeToBeRemoved: Node) {
            // Current node is removed by informing its parent that its child, being current node, is o be removed.
            currNodeToBeRemoved.parentNode.removeChild(currNodeToBeRemoved)
        }

        private fun writeDocToString(windowHierarchyDumpDocument: Document): String {
            val baos = ByteArrayOutputStream()
            TransformerFactory.newInstance().newTransformer().transform(DOMSource(windowHierarchyDumpDocument), StreamResult(baos))
            val outputString = baos.toString(Charsets.UTF_8.name())
            return outputString
        }
    }

    val deviceDisplayBounds: Rectangle
    private val wellFormedness: WellFormedness
    override val guiState: IGuiState
    override val validationResult: ValidationResult


    init {
        this.deviceDisplayBounds = Rectangle(displayDimensions)

        val wellFormedness = checkWellFormedness(windowHierarchyDump)
        if (wellFormedness == WellFormedness.OK) {
            this.wellFormedness = checkWellFormedness(this.windowHierarchyDump)
            this.guiState = computeGuiState(this.windowHierarchyDump)
        } else {
            this.wellFormedness = wellFormedness
            this.guiState = GuiState("INVALID", "", ArrayList(), "INVALID")
        }

        this.validationResult = validate()
    }


    override fun getPackageName(): String {
        if (this.wellFormedness != WellFormedness.OK)
            return "Package unknown: the snapshot is not well-formed"

        val startIndex = windowHierarchyDump.indexOf("package=\"")
        val endIndex = windowHierarchyDump.indexOf('"', startIndex + "package=\"".length)
        return windowHierarchyDump.substring(startIndex + "package=\"".length, endIndex)
    }

    //region Getting GUI state

    private fun createWidget(it: Node, parentWidget: IWidget?): IWidget? {
        try {
            /*
      Example "it": <node index="0" text="LOG IN" resource-id="com.snapchat.android:id/landing_page_login_button" class="android.widget.Button" package="com.snapchat.android" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,949][800,1077]"/>
      */
            val w = Widget()
            with(w) {
                // @formatter:off
                id = it.attributes.getNamedItem("id")?.nodeValue ?: ""
                // Appears only in test code simulating the device, never on actual devices or their emulators.
                index = it.attributes.getNamedItem("index").nodeValue.toInt()
                text = it.attributes.getNamedItem("text")?.nodeValue ?: ""
                resourceId = it.attributes.getNamedItem("resource-id")?.nodeValue ?: ""
                className = it.attributes.getNamedItem("class")?.nodeValue ?: ""
                packageName = it.attributes.getNamedItem("package")?.nodeValue ?: ""
                contentDesc = it.attributes.getNamedItem("content-desc")?.nodeValue ?: ""
                checkable = it.attributes.getNamedItem("checkable").nodeValue == "true"
                checked = it.attributes.getNamedItem("checked").nodeValue == "true"
                clickable = it.attributes.getNamedItem("clickable").nodeValue == "true"
                enabled = it.attributes.getNamedItem("enabled").nodeValue == "true"
                focusable = it.attributes.getNamedItem("focusable").nodeValue == "true"
                focused = it.attributes.getNamedItem("focused").nodeValue == "true"
                scrollable = it.attributes.getNamedItem("scrollable").nodeValue == "true"
                longClickable = it.attributes.getNamedItem("long-clickable").nodeValue == "true"
                password = it.attributes.getNamedItem("password").nodeValue == "true"
                selected = it.attributes.getNamedItem("selected").nodeValue == "true"

                bounds = Widget.parseBounds(it.attributes.getNamedItem("bounds").nodeValue)
                deviceDisplayBounds = this.deviceDisplayBounds

                parent = parentWidget
                // @formatter:on
            }
            return w
        } catch (e: InvalidWidgetBoundsException) {
            log.error("Catching exception: parsing widget bounds failed. ${LogbackConstants.err_log_msg}\n" +
                    "Continuing execution, skipping the widget with invalid bounds.")
            log.error(exceptions, "parsing widget bounds failed with exception:\n", e)
            return null
        }
    }

    private fun addWidget(result: MutableList<IWidget>, parent: IWidget?, data: Node) {
        val w = createWidget(data, parent)

        if (w != null) {
            if (parent == null)
                w.xpath = "//"
            else
                w.xpath = "$parent.xpath/"

            w.xpath += "${w.className}[${w.index + 1}]"

            result.add(w)
        }

        data.childNodes.toList()
                .filter { it.nodeName == "node" }
                .forEach { addWidget(result, w, it) }
    }

    private fun NodeList.toList(): List<Node> {
        return (0 until this.length).map { i ->
            this.item(i)
        }
    }

    private fun Node.isSystemUINode(): Boolean {
        val pkg = this.attributes.getNamedItem("package")

        return (pkg != null) && (pkg.nodeValue.contains("com.android.systemui"))
    }

    private fun computeGuiState(windowHierarchyDump: String): GuiState {
        assert(wellFormedness == WellFormedness.OK)

        val dbf = DocumentBuilderFactory.newInstance()
        val db = dbf.newDocumentBuilder()
        val inputStream = ByteArrayInputStream(windowHierarchyDump.toByteArray())
        val hierarchy = db.parse(inputStream)
                .apply { documentElement.normalize() }
                .childNodes.item(0)

        ////////val hierarchy = XmlSlurper().parseText(windowHierarchyDump)
        assert(hierarchy.nodeName == "hierarchy")

        val childNodes = hierarchy.childNodes
                .toList()
                .filter { it.nodeName == "node" }
                .filterNot { it.isSystemUINode() }
        var topNodePackage = childNodes.first().attributes.getNamedItem("package").nodeValue

        // When the application starts with an active keyboard, look for the proper application instead of the keyboard
        // This problem was identified on the app "com.hykwok.CurrencyConverter"
        // https://f-droid.org/repository/browse/?fdfilter=CurrencyConverter&fdid=com.hykwok.CurrencyConverter
        if (topNodePackage.startsWith("com.google.android.inputmethod.") && (childNodes.size > 1))
            topNodePackage = childNodes.drop(1).first().attributes.getNamedItem("package").nodeValue

        assert(topNodePackage.isNotEmpty())

        val widgets: MutableList<IWidget> = ArrayList()

        childNodes.forEach {
            addWidget(widgets, null, it)
        }

        val gs = GuiState(topNodePackage, id, widgets, this.androidLauncherPackageName)
        return when {
            gs.isRequestRuntimePermissionDialogBox -> RuntimePermissionDialogBoxGuiState(topNodePackage, widgets, this.androidLauncherPackageName)
            gs.isAppHasStoppedDialogBox -> AppHasStoppedDialogBoxGuiState(topNodePackage, widgets, this.androidLauncherPackageName)
            else -> gs
        }
    }

    //endregion Getting GUI state

    //region Validation

    private fun validate(): ValidationResult {
        if (wellFormedness == WellFormedness.OK) {
            val gs = this.guiState

            return if (gs is AppHasStoppedDialogBoxGuiState) {
                if (gs.okWidget.enabled)
                    ValidationResult.app_has_stopped_dialog_box_with_OK_button_enabled
                else
                    ValidationResult.app_has_stopped_dialog_box_with_OK_button_disabled

            } else if (gs is RuntimePermissionDialogBoxGuiState) {
                if (gs.allowWidget.enabled)
                    ValidationResult.request_runtime_permission_dialog_box_with_Allow_button_enabled
                else
                    ValidationResult.request_runtime_permission_dialog_box_with_Allow_button_disabled

            } else {
                ValidationResult.OK
            }
        } else
            return wellFormedness.toValidationResult()
    }


    private fun checkWellFormedness(windowHierarchyDump: String): WellFormedness {
        if (windowHierarchyDump.isEmpty())
            return WellFormedness.is_null
        else if (isEmptyStub(windowHierarchyDump))
            return WellFormedness.is_empty
        else if (!windowHierarchyDump.contains(rootXmlNodePrefix))
            return WellFormedness.missing_root_xml_node_prefix
        else
            return WellFormedness.OK
    }

    /**
     * This covers a case when the dump looks as follows:
     *
     * <?xml version="1.0" encoding="UTF-8"?><hierarchy rotation="0">
     *
     *
     * </hierarchy>
     */
    private fun isEmptyStub(windowHierarchyDump: String): Boolean
            = windowHierarchyDump.count { it == '<' } <= 3 && windowHierarchyDump.count { it == '\n' } <= 5
    //endregion


    override fun toString(): String {
        val clazz = UiautomatorWindowDump::class.java.simpleName

        if (this.wellFormedness != WellFormedness.OK)
            return "$clazz{!not well-formed!: $windowHierarchyDump}"

        if (this.guiState.isHomeScreen)
            return "$clazz{home screen}"

        if (this.guiState.isRequestRuntimePermissionDialogBox)
            return "$clazz{\"Runtime permission\" dialog box. Allow widget enabled: ${(this.guiState as RuntimePermissionDialogBoxGuiState).allowWidget.enabled}}"

        if (this.guiState.isAppHasStoppedDialogBox)
            return "$clazz{\"App has stopped\" dialog box. OK widget enabled: ${(this.guiState as AppHasStoppedDialogBoxGuiState).okWidget.enabled}}"

        if (this.guiState.isCompleteActionUsingDialogBox)
            return "$clazz{\"Complete action using\" dialog box.}"

        if (this.guiState.isSelectAHomeAppDialogBox)
            return "$clazz{\"Select a home app\" dialog box.}"

        if (this.guiState.isUseLauncherAsHomeDialogBox)
            return "$clazz{\"Use Launcher as Home\" dialog box.}"


        val returnString = "$clazz{${getPackageName()}. Widgets# ${this.guiState.widgets.size}}"

        // Uncomment when necessary for debugging.
//    List<Widget> widgets = this.guiState.widgets
//    final int displayedWidgetsLimit = 50
//    returnString += widgets.take(displayedWidgetsLimit).collect {it.toShortString() }.join("\n") + "\n"
//    if (widgets.size() > displayedWidgetsLimit)
//      returnString += "...\n...skipped displaying remaining ${widgets.size()-displayedWidgetsLimit} widgets...\n"
//    returnString += "----- end of widgets ----\n"

        return returnString
    }
}

