// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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

package org.droidmate.test_tools.device.datatypes

class WidgetTestHelper {
    // TODO Fix tests
    companion object {
        /*@JvmStatic
        private fun newGenWidget(args: Map<String, Any>, widgetGenIndex: Int): Widget {
            assert(widgetGenIndex >= 1)
            val genArgs = args.toMutableMap()

            // @formatter:off
            genArgs["uid"] = args["uid"] ?: getIdsList(widgetGenIndex).last()
            genArgs["text"] = args["text"] ?: getTextsList(widgetGenIndex, (0 until widgetGenIndex).map { _ -> genArgs["uid"] as String? ?: "" }).last()
            genArgs["bounds"] = args["bounds"] ?: getBoundsList(widgetGenIndex).last()
            genArgs["className"] = args["className"] ?: getClassesList(widgetGenIndex).last()
            genArgs["enabled"] = args["enabled"] ?: true
            // @formatter:on

            return newWidget(genArgs)
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun newWidgets(widgetCount: Int, packageName: String, props: Map<String, Any>, widgetIdPrefix: String = ""): List<Widget> {
            assert(widgetCount >= 1)

            val propIdsList = props["idsList"]
            val idsList = if ( (propIdsList == null) || (propIdsList as List<String>).isEmpty() )
                getIdsList(widgetCount, widgetIdPrefix)
            else
                propIdsList

            val textsList = props["textsList"] as List<String>? ?: getTextsList(widgetCount, idsList)
            val boundsList = props["boundsList"] as List<List<Int>>? ?: getBoundsList(widgetCount)
            val classesList = props["classList"] as List<String>? ?: getClassesList(widgetCount)
            val enabledList = props["enabledList"] as List<Boolean>? ?: (0..widgetCount).map { _ -> true }

            assert(arrayListOf(idsList, textsList, boundsList, classesList, enabledList).all { it.size == widgetCount })

            val widgets = (0 until widgetCount).map { i ->
                newWidget(mutableMapOf(
                        "uid" to idsList[i],
                        "text" to textsList[i],
                        "bounds" to boundsList[i],
                        "className" to classesList[i],
                        "packageName" to packageName,
                        "clickable" to true,
                        "check" to false,
                        "enabled" to enabledList[i]
                ))
            }
            assert(widgets.size == widgetCount)
            return widgets
        }

        @JvmStatic
        private fun getIdsList(idsCount: Int, widgetIdPrefix: String = ""): List<String> =
                (0 until idsCount).map { i -> getNextWidgetId(i, widgetIdPrefix) }

        @JvmStatic
        private fun getTextsList(textsCount: Int, widgetIds: List<String>): List<String> {
            assert(widgetIds.size == textsCount)
            return (0 until textsCount).map { i -> "txt:uid/${widgetIds[i]}" }
        }

        @JvmStatic
        private fun getClassesList(classesCount: Int): List<String> {
            val classesList = androidWidgetClassesForTesting

            return (0 until classesCount).map { index ->
                val classNameIndex = index % classesList.size
                classesList[classNameIndex]
            }
        }

        @JvmStatic
        private fun getBoundsList(boundsCount: Int): List<List<Int>> {
            var lowX = 5 + getBoundsListCallGen
            var lowY = 6 + getBoundsListCallGen
            getBoundsListCallGen++
            var highX = lowX + 20
            var highY = lowY + 30

            val bounds: MutableList<List<Int>> = ArrayList()
            (0 until boundsCount).forEach { _ ->
                bounds.add(arrayListOf(lowX, lowY, highX, highY))
                lowX += 25
                lowY += 35
                highX = lowX + 20
                highY = lowY + 30
            }
            return bounds
        }

        @JvmStatic
        private var dummyNameGen = 0
        @JvmStatic
        private var getBoundsListCallGen = 0

        @JvmStatic
        private fun getNextWidgetId(index: Int, widgetIdPrefix: String = ""): String {
            return if (widgetIdPrefix.isEmpty())
                "${index}_uniq${dummyNameGen++}"
            else
                "${widgetIdPrefix}_W$index"
        }

        @JvmStatic
        private val androidWidgetClassesForTesting = arrayListOf(
                "android.view.View",
                "android.widget.Button",
                "android.widget.CheckBox",
                "android.widget.CheckedTextView",
                "android.widget.CompoundButton",
                "android.widget.EditText",
                "android.widget.GridView",
                "android.widget.ImageButton",
                "android.widget.ImageView",
                "android.widget.LinearLayout",
                "android.widget.ListView",
                "android.widget.RadioButton",
                "android.widget.RadioGroup",
                "android.widget.Spinner",
                "android.widget.Switch",
                "android.widget.TableLayout",
                "android.widget.TextView",
                "android.widget.ToggleButton"
        )

        @JvmStatic
        fun newClickableButton(args: MutableMap<String, Any> = HashMap()): Widget {
            val newArgs: MutableMap<String, Any> = args.toMutableMap()
                    .apply { putAll(hashMapOf("clickable" to true, "check" to true, "enabled" to true)) }

            return newButton(newArgs)
        }

        @JvmStatic
        private fun newButton(args: Map<String, Any>): Widget {
            val newArgs: MutableMap<String, Any> = args.toMutableMap()
                    .apply { putAll(hashMapOf("className" to "android.widget.Button")) }

            return newWidget(newArgs)
        }


        @JvmStatic
        @Suppress("unused")
        fun newTopLevelWidget(packageName: String): Widget {
            return newWidget(mapOf("uid" to "topLevelFrameLayout",
                    "packageName" to packageName,
                    "class" to "android.widget.FrameLayout",
                    "bounds" to arrayListOf(0, 0, 800, 1205))
                    .toMutableMap())
        }

        @JvmStatic
        fun newClickableWidget(args: MutableMap<String, Any> = HashMap(), widgetGenIndex: Int = 0): Widget {
            val newArgs: MutableMap<String, Any> = args.toMutableMap()
                    .apply { putAll(hashMapOf("clickable" to true, "enabled" to true)) }

            return if (widgetGenIndex > 0)
                newWidget(newArgs)
            else
                newGenWidget(newArgs, widgetGenIndex + 1)
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private fun newWidget(args: MutableMap<String, Any>): Widget {
            val bounds = if (args["bounds"] != null) args["bounds"] as List<Int> else arrayListOf(10, 20, 101, 202)
            if (args["className"] == null)
                args["className"] = androidWidgetClassesForTesting[1]

            assert(bounds.size == 4)
            assert(bounds[0] < bounds[2])
            assert(bounds[1] < bounds[3])

            val lowX = bounds[0]
            val lowY = bounds[1]
            val highX = bounds[2]
            val highY = bounds[3]
            val xpath = "//${args["className"] as String? ?: "fix_cls"}[${(args["index"] as Int? ?: 0) + 1}]"

            return OldWidget(args["uid"] as String? ?: "",
                    args["index"] as Int? ?: 0,
                    args["text"] as String? ?: "fix_text",
                    args["resourceId"] as String? ?: "fix_resId",
                    args["className"] as String? ?: "fix_cls",
                    args["packageName"] as String? ?: "fix_pkg",
                    args["contentDesc"] as String? ?: "fix_contDesc",
                    args["xpath"] as String? ?: xpath,
                    args["check"] as Boolean? ?: false,
                    args["check"] as Boolean? ?: false,
                    args["clickable"] as Boolean? ?: false,
                    args["enabled"] as Boolean? ?: false,
                    args["focusable"] as Boolean? ?: false,
                    args["focus"] as Boolean? ?: false,
                    args["scrollable"] as Boolean? ?: false,
                    args["longClickable"] as Boolean? ?: false,
                    args["password"] as Boolean? ?: false,
                    args["selected"] as Boolean? ?: false,
                    Rectangle(lowX, lowY, highX - lowX, highY - lowY),
                    null)
                    // @formatter:on
        }*/
    }
}
