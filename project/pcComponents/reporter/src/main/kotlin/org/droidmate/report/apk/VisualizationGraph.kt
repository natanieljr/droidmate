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

package org.droidmate.report.apk

import com.google.gson.*
import com.konradjamrozik.Resource
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.device.android_sdk.IApk
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.statemodel.dumpString
import java.io.File
import java.lang.reflect.Type
import java.nio.file.*

/**
 * This reporter creates a report in form of a web page, displaying the model, its states and its
 * actions as an interactive graph with execution details. The report is generated into a folder
 * named after topLevelDirName.
 */
class VisualizationGraph : ApkReport() {

    /**
     * All files are generated into this folder.
     */
    private val topLevelDirName: String = "vis"

    /**
     * The directory which will contain all images for the states.
     */
    private lateinit var targetImgDir: Path

    /**
     * Edge encapsulates an ActionData object, because the frontend cannot have multiple
     * edges for the same transitions. Therefore, the indices are stored and the corresponding
     * targetWidgets are mapped to the indices. E.g., for an Edge e1, the transition was taken
     * for the index 2 (nr. of action) with a button b1 and for the index 5 with a button b2.
     * In this case there are two ActionData objects which are represented as a single Edge
     * object with two entries in the actionIndexWidgetMap map.
     */
    inner class Edge(val actionData: ActionData) {
        val indices = HashSet<Int>()
        val id = "${actionData.prevState.dumpString()} -> ${actionData.resState.dumpString()}"
        val actionIndexWidgetMap = HashMap<Int, Widget?>()
        fun addIndex(i: Int, w: Widget?) {
            indices.add(i)
            actionIndexWidgetMap[i] = w
        }
    }

    /**
     * Wrapper object to encapsulate nodes and edges, so that when this object is serialized
     * to Json, these fields are named "nodes" and "edges". Additionally, the graph contains
     * general information.
     */
    @Suppress("unused")
    inner class Graph(val nodes: Set<StateData>,
                      edges: List<ActionData>,
                      val explorationStartTime: String,
                      val explorationEndTime: String,
                      val explorationTimeInMs: Int,
                      val numberOfActions: Int,
                      val numberOfStates: Int,
                      val apk: IApk) {
        val edges : MutableList<Edge> = ArrayList()

        // The graph in the frontend is not able to display multiple edges for the same transition,
        // therefore add here the indices and check if the same transition was taken before, if yes
        // then just add the index to the already added edge
        init {
            val tmpEdges = edges.map { Edge(it) }
            val edgeMap = HashMap<String, Edge>()
            for (i in tmpEdges.indices) {
                val edge = tmpEdges[i]
                val entry = edgeMap[edge.id]
                if (entry == null) {
                    edge.addIndex(i, edge.actionData.targetWidget)
                    edgeMap[edge.id] = edge
                    this.edges.add(edge)
                } else {
                    entry.addIndex(i, edge.actionData.targetWidget)
                }
            }
        }

        fun toJsonVariable(gson: Gson): String {
            val graphJson = gson.toJson(this)
            return "var data = $graphJson;"
        }
    }

    /**
     * Custom Json serializer to control the serialization for StateData objects.
     */
    inner class StateDataAdapter : JsonSerializer<StateData> {
        override fun serialize(src: StateData, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            val stateId = src.stateId.dumpString()
            // The frontend needs a property 'id', use the stateId for this
            obj.addProperty("id", stateId)
            obj.addProperty("stateId", stateId)
            obj.addProperty("topNodePackageName", src.topNodePackageName)
            obj.addProperty("shape", "image")
            obj.addProperty("image", getImgPath(stateId))
            obj.addProperty("uid", src.uid.toString())
            obj.addProperty("configId", src.configId.toString())
            obj.addProperty("iEditId", src.iEditId.toString())
            obj.addProperty("hasEdit", src.hasEdit)
            obj.addProperty("isHomeScreen", src.isHomeScreen)
            obj.addProperty("title", stateId)
            // Include all important properties to make the states searchable
            val properties = arrayListOf(stateId, src.topNodePackageName, src.uid.toString(), src.configId.toString(), src.iEditId.toString())
            obj.addProperty("content", properties.joinToString("\n"))
            return obj
        }
    }

    /**
     * Custom Json serializer to control the serialization for ActionData objects.
     */
    inner class EdgeAdapter : JsonSerializer<Edge> {
        override fun serialize(src: Edge, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            obj.addProperty("from", src.actionData.prevState.dumpString())
            obj.addProperty("to", src.actionData.resState.dumpString())
            obj.addProperty("actionType", src.actionData.actionType)
            obj.addProperty("id", src.id)
            obj.addProperty("propertyConfigId", src.actionData.targetWidget?.propertyConfigId.toString())
            obj.addProperty("title", src.id)
            obj.addProperty("label", "${src.actionData.actionType} ${src.indices.joinToString(",", prefix = "<", postfix = ">")}")
            obj.add("targetWidgets", context.serialize(src.actionIndexWidgetMap))
            return obj
        }
    }

    /**
     * Custom Json serializer to control the serialization for <HashMap<Int, Widget?> objects.
     */
    inner class WidgetAdapter : JsonSerializer<HashMap<Int, Widget?>> {
        override fun serialize(src: HashMap<Int, Widget?>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val widgets = JsonArray()
            for ((idx, w) in src) {
                val obj = JsonObject()
                // TODO dataString might be interesting?
                obj.addProperty("idxOfAction", idx)
                obj.addProperty("id", w?.id?.dumpString())
                obj.addProperty("uid", w?.uid.toString())
                obj.addProperty("propertyConfigId", w?.propertyConfigId.toString())
                obj.addProperty("text", w?.text)
                obj.addProperty("contentDesc", w?.contentDesc)
                obj.addProperty("resourceId", w?.resourceId)
                obj.addProperty("className", w?.text)
                obj.addProperty("packageName", w?.text)
                obj.addProperty("isPassword", w?.text)
                obj.addProperty("enabled", w?.enabled)
                obj.addProperty("visible", w?.visible)
                obj.addProperty("clickable", w?.clickable)
                obj.addProperty("longClickable", w?.longClickable)
                obj.addProperty("scrollable", w?.scrollable)
                obj.addProperty("checked", w?.checked)
                obj.addProperty("focused", w?.focused)
                obj.addProperty("selected", w?.selected)
                obj.addProperty("xpath", w?.xpath)
                obj.addProperty("isLeaf", w?.isLeaf)

                widgets.add(obj)
            }

            return widgets
        }
    }

    /**
     * Custom Json serializer to control the serialization for IApk objects.
     */
    inner class IApkAdapter : JsonSerializer<IApk> {
        override fun serialize(src: IApk, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            obj.addProperty("path", src.path.toString())
            obj.addProperty("packageName", src.packageName)
            obj.addProperty("launchableActivityName", src.launchableActivityName)
            obj.addProperty("launchableActivityComponentName", src.launchableActivityComponentName)
            obj.addProperty("applicationLabel", src.applicationLabel)
            obj.addProperty("fileName", src.fileName)
            obj.addProperty("fileNameWithoutExtension", src.fileNameWithoutExtension)
            obj.addProperty("absolutePath", src.absolutePath)
            obj.addProperty("inlined", src.inlined)
            obj.addProperty("instrumented", src.instrumented)
            obj.addProperty("isDummy", src.isDummy)
            return obj
        }
    }

    /**
     * Returns the path of the image, which should be used for the according state. Use
     * the Default.png if no such file with the according stateId exists. This is the case
     * e.g. for the initial state or for states for which DroidMate could not acquire an
     * image.
     */
    private fun getImgPath(stateId: String): String {
        return if (Files.list(targetImgDir).anyMatch { it.fileName.toString().startsWith(stateId) }) {
            Paths.get(".").resolve("img").resolve("$stateId.png").toString()
        } else
            Paths.get(".").resolve("img").resolve("Default.png").toString()
    }

    /**
     * Returns the custom Json builder, which controls what properties are serialized
     * and how they are named.
     */
    private fun getCustomGsonBuilder(): Gson {
        val gsonBuilder = GsonBuilder().setPrettyPrinting()

        val stateDataSerializer = StateDataAdapter()
        val edgeSerializer = EdgeAdapter()
        val apkSerializer = IApkAdapter()
        val widgetSerializer = WidgetAdapter()
        gsonBuilder.registerTypeAdapter(StateData::class.java, stateDataSerializer)
        gsonBuilder.registerTypeAdapter(Edge::class.java, edgeSerializer)
        gsonBuilder.registerTypeAdapter(IApk::class.java, apkSerializer)
        gsonBuilder.registerTypeAdapter(HashMap<Int, Widget?>()::class.java, widgetSerializer)

        return gsonBuilder.create()
    }

    override fun safeWriteApkReport(data: ExplorationContext, apkReportDir: Path, resourceDir: Path) {

        val targetVisFolder = apkReportDir.resolve(topLevelDirName)
        val model = data.getModel()
        val source = Resource("vis").file

        // Copy the folder with the required resources
        Files.copy(source, targetVisFolder, StandardCopyOption.REPLACE_EXISTING)
        // Copy the state images
        targetImgDir = targetVisFolder.resolve("img")

        Files.list(model.config.stateDst)
                .filter { filename -> filename.toString().endsWith(".png") }
                .forEach {
                    println("Source: $it")
                    println("Destination: $targetImgDir")
                    Files.copy(it, targetImgDir.resolve(it.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
                }

        val jsonFile = targetVisFolder.resolve("data.js")
        val gson = getCustomGsonBuilder()

        runBlocking {

            val actions = data.actionTrace.getActions()
            val states = model.getStates()
            val graph = Graph(states,
                                actions,
                                data.explorationStartTime.toString(),
                                data.explorationEndTime.toString(),
                                data.getExplorationTimeInMs(),
                                data.getSize(),
                                states.size,
                                data.apk)
            val jsGraph = graph.toJsonVariable(gson)

            Files.write(jsonFile, jsGraph.toByteArray())
        }

    }
}