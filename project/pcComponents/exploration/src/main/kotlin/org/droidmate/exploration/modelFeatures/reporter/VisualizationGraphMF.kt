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

package org.droidmate.exploration.modelFeatures.reporter

import com.google.gson.*
import com.konradjamrozik.Resource
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.isQueueEnd
import org.droidmate.deviceInterface.exploration.isQueueStart
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.IApk
import org.droidmate.exploration.modelFeatures.misc.unzip
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.ConcreteId.Companion.fromString
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.lang.reflect.Type
import java.nio.file.*
import java.util.*
import javax.imageio.ImageIO
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext

/**
 * This reporter creates a report in form of a web page, displaying the model, its states and its
 * actions as an interactive graph with execution details. The report is generated into a folder
 * named after topLevelDirName.
 *
 * For better usage set the "ModelProperties.imgDump.widget.nonInteractable" property to true.
 */
class VisualizationGraphMF(reportDir: Path, resourceDir: Path) : ApkReporterMF(reportDir, resourceDir) {

    override val coroutineContext: CoroutineContext = CoroutineName("VisualizationGraphMF")

    /**
     * All files are generated into this folder.
     */
    private val topLevelDirName: String = "vis"

    /**
     * The directory which will contain all images for the states.
     */
    private lateinit var targetStatesImgDir: Path

    /**
     * Edge encapsulates an Interaction object, because the frontend cannot have multiple
     * edges for the same transitions. Therefore, the indices are stored and the corresponding
     * targetWidgets are mapped to the indices. E.g., for an Edge e1, the transition was taken
     * for the index 2 (nr. of action) with a button b1 and for the index 5 with a button b2.
     * In this case there are two Interaction objects which are represented as a single Edge
     * object with two entries in the actionIndexWidgetMap map.
     */
    inner class Edge(val interaction: Interaction) {
        val indices = HashSet<Int>()
        val id = "${interaction.prevState} -> ${interaction.resState}"
        val actionIndexWidgetMap = HashMap<Int, Widget?>()
        fun addIndex(i: Int, w: Widget?) {  //FIXME this does not allow for all targets of WidgetQueue
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
    inner class Graph(val nodes: Set<State>,
                      edges: List<Pair<Int, Interaction>>,
                      val explorationStartTime: String,
                      val explorationEndTime: String,
                      val numberOfActions: Int,
                      val numberOfStates: Int,
                      val apk: IApk) {
        private val edges: MutableList<Edge> = ArrayList()

        // The graph in the frontend is not able to display multiple edges for the same transition,
        // therefore update here the indices and check if the same transition was taken before, if yes
        // then just update the index to the already added edge
        init {
//			val tmpEdges = edges.map { Edge(it) }
            val edgeMap = HashMap<String, Edge>()
            edges.forEach { (idx, a) ->
                if (!(a.actionType.isQueueStart() || a.actionType.isQueueEnd())) { // ignore queue actions
                    val edge = Edge(a)
                    val entry = edgeMap[edge.id]
                    if (entry == null) {
                        edge.addIndex(idx, edge.interaction.targetWidget)
                        edgeMap[edge.id] = edge
                        this.edges.add(edge)
                    } else {
                        entry.addIndex(idx, edge.interaction.targetWidget)
                    }
                }
            }
        }

        fun toJsonVariable(gson: Gson): String {
            val graphJson = gson.toJson(this)
            return "var data = $graphJson;"
        }
    }

    /**
     * This is a dummy apk implementation. It is needed if [createVisualizationGraph] is called
     * with only the model, because the model has not the apk as reference.
     */
    inner class DummyApk : IApk {
        override val path: Path
            get() = Paths.get("./")
        override val packageName: String
            get() = "DummyPackageName"
        override var launchableMainActivityName = "DummyLaunchableMainActivityName"
        override val applicationLabel: String
            get() = "DummyApplicationLabel"
        override val fileName: String
            get() = "DummyFileName"
        override val fileNameWithoutExtension: String
            get() = "DummyFileNameWithoutExtension"
        override val absolutePath: String
            get() = "DummyAbsolutePath"
        override val inlined: Boolean
            get() = false
        override val instrumented: Boolean
            get() = false
        override val isDummy: Boolean
            get() = true
    }

    /**
     * Custom Json serializer to control the serialization for State objects.
     */
    inner class StateDataAdapter : JsonSerializer<State> {
        override fun serialize(src: State, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            val stateId = src.stateId.toString()
            // The frontend needs a property 'id', use the stateId for this
            obj.addProperty("id", stateId)
            obj.addProperty("stateId", stateId)
//			obj.addProperty("topNodePackageName", src.topNodePackageName)
            obj.addProperty("shape", "image")
            obj.addProperty("image", getImgPath(stateId))
            obj.addProperty("uid", src.uid.toString())
            obj.addProperty("configId", src.configId.toString())
            obj.addProperty("hasEdit", src.hasEdit)
            obj.addProperty("isHomeScreen", src.isHomeScreen)
            obj.addProperty("title", stateId)
            // Include all important properties to make the states searchable
            val properties = arrayListOf(stateId, //src.topNodePackageName,
                src.uid.toString(), src.configId.toString())
            obj.addProperty("nlpText", properties.joinToString("\n"))

            // Widgets
            val widgets = JsonArray()
            for (w in src.widgets) {
                widgets.add(context.serialize(w))
            }

            obj.add("widgets", widgets)
            return obj
        }
    }

    /**
     * Custom Json serializer to control the serialization for Interaction objects.
     */
    inner class EdgeAdapter : JsonSerializer<Edge> {
        override fun serialize(src: Edge, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            obj.addProperty("from", src.interaction.prevState.toString())
            obj.addProperty("to", src.interaction.resState.toString())
            obj.addProperty("actionType", src.interaction.actionType)
            obj.addProperty("id", src.id)
            obj.addProperty("configId", src.interaction.targetWidget?.configId.toString())
            obj.addProperty("title", src.id)
            obj.addProperty("label", "${src.interaction.actionType} ${src.indices.joinToString(",", prefix = "<", postfix = ">")}")
            obj.add("targetWidgets", context.serialize(src.actionIndexWidgetMap))
            return obj
        }
    }

    /**
     * Custom Json serializer to control the serialization for Widget objects.
     */
    inner class WidgetAdapter : JsonSerializer<Widget> {
        override fun serialize(src: Widget, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return convertWidget(src, context)
        }
    }

    /**
     * Custom Json serializer to control the serialization for <HashMap<Int, Widget?> objects.
     */
    inner class IdxWidgetHashMapAdapter : JsonSerializer<HashMap<Int, Widget?>> {
        override fun serialize(src: HashMap<Int, Widget?>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val widgets = JsonArray()
            for ((idx, w) in src) {
                val obj = convertWidget(w, context)
                // TODO dataString might be interesting?
                obj.addProperty("idxOfAction", idx)
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
            obj.addProperty("launchedMainActivityName", src.launchableMainActivityName)
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
     * Converts a given Widget as JsonObject with all the necessary information.
     */
    private fun convertWidget(src: Widget?, context: JsonSerializationContext): JsonObject {
        val obj = JsonObject()

        val id = src?.id?.toString()
        obj.addProperty("id", id)
        obj.addProperty("uid", src?.uid.toString())
        obj.addProperty("configId", src?.configId.toString())
        obj.addProperty("image", getImgPath(id))
        obj.addProperty("text", src?.text)
        obj.addProperty("contentDesc", src?.contentDesc)
        obj.addProperty("resourceId", src?.resourceId)
        obj.addProperty("className", src?.className)
        obj.addProperty("packageName", src?.packageName)
        obj.addProperty("isPassword", src?.isPassword)
        obj.addProperty("enabled", src?.enabled)
        obj.addProperty("definedAsVisible", src?.definedAsVisible)
        obj.addProperty("clickable", src?.clickable)
        obj.addProperty("longClickable", src?.longClickable)
        obj.addProperty("scrollable", src?.scrollable)
        obj.addProperty("checked", src?.checked)
        obj.addProperty("focused", src?.focused)
        obj.add("visibleAreas", context.serialize(src?.visibleAreas))
        obj.addProperty("selected", src?.selected)
        obj.addProperty("xpath", src?.xpath)
        obj.addProperty("isLeaf", src?.isLeaf())

        return obj
    }

    /**
     * Returns the path of the image, which should be used for the according id. Use
     * the Default.png if no such file with the according id exists. This is the case
     * e.g. for the initial state or for states for which DroidMate could not acquire an
     * image.
     */
    private fun getImgPath(id: String?): String { // FIXME the image files are stored by their action number, lookup the exploration trace to determine which image belongs to which state (or if necessary add an model watcher for that)
        return if (id != null
            // Image is available
            && Files.list(targetStatesImgDir).use { list -> list.anyMatch { it.fileName.toString().startsWith(id) } }) {

            Paths.get(".")
                .resolve("img")
                .resolve("states")
                .resolve("$id.jpg")
                .toString()
        } else
            Paths.get(".")
                .resolve("img")
                .resolve("Default.png")
                .toString()
    }

    /**
     * Returns the custom Json builder, which controls what properties are serialized
     * and how they are named.
     */
    private fun getCustomGsonBuilder(): Gson {
        val gsonBuilder = GsonBuilder().setPrettyPrinting()

        gsonBuilder.registerTypeAdapter(State::class.java, StateDataAdapter())
        gsonBuilder.registerTypeAdapter(Edge::class.java, EdgeAdapter())
        gsonBuilder.registerTypeAdapter(IApk::class.java, IApkAdapter())
        gsonBuilder.registerTypeAdapter(Widget::class.java, WidgetAdapter())
        gsonBuilder.registerTypeAdapter(HashMap<Int, Widget?>()::class.java, IdxWidgetHashMapAdapter())

        return gsonBuilder.create()
    }

    private fun copyFilteredFiles(from: Path, to: Path, suffix: String) {
        Files.list(from)
            .use { list ->
                list.filter { filename -> filename.toString().endsWith(suffix) }.forEach {
                    Files.copy(it, to.resolve(it.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
                }
            }
    }

    override fun safeWriteApkReport(context: ExplorationContext, apkReportDir: Path, resourceDir: Path) {
        createVisualizationGraph(context.getModel(), context.apk, apkReportDir, resourceDir)
    }

    override suspend fun onNewInteracted(traceId: UUID, actionIdx: Int, action: ExplorationAction, targetWidgets: List<Widget>, prevState: State, newState: State) {
        // TODO keep track of actionnumber, map state
        // Fix here Ticket #73
    }

    override fun reset() {
        // Do nothing
    }

    fun createVisualizationGraph(model: Model, apkReportDir: Path, resourceDir: Path, ignoreConfig: Boolean = false) {
        createVisualizationGraph(model, DummyApk(), apkReportDir, resourceDir, ignoreConfig)
    }

    /**
     * The zipped archive 'vis.zip' contains all resources for the graph such as index.html etc.
     * It is zipped because, keeping it as directory in the resources folder and copying a folder
     * from a jar (e.g. when DroidMate is imported as an external application) was troublesome.
     */
    fun createVisualizationGraph(model: Model, apk: IApk, apkReportDir: Path, resourceDir: Path, ignoreConfig: Boolean = false) {
        val targetVisFolder = apkReportDir.resolve(topLevelDirName)
        // Copy the folder with the required resources
        val zippedVisDir = Resource("vis.zip").extractTo(apkReportDir)
        try {
            zippedVisDir.unzip(targetVisFolder)
            Files.delete(zippedVisDir)
        } catch (e: FileSystemException) { // FIXME temporary work-around for windows file still used issue
            log.warn("resource zip could not be unzipped/removed ${e.localizedMessage}")
        }

        // Copy the state and widget images
        val targetImgDir = targetVisFolder.resolve("img")
        Files.createDirectories(targetImgDir)
        targetStatesImgDir = targetImgDir.resolve("states")
        Files.createDirectories(targetStatesImgDir)

        copyFilteredFiles(model.config.imgDst, targetStatesImgDir, ".jpg")

        val jsonFile = targetVisFolder.resolve("data.js")
        val gson = getCustomGsonBuilder()

        runBlocking {

            // TODO Jenny proposed to visualize multiple traces in different colors in the future, as we only
            // use the first trace right now
            val actions = if (ignoreConfig)
                markTargets(model, targetStatesImgDir)
            else
                model.getPaths().first().getActions().mapIndexed { i, a -> Pair(i, a) }
            val states = model.getStates().filter { s ->
                // avoid unconnected states
                actions.any { (_, a) -> a.prevState == s.stateId || a.resState == s.stateId }
            }.toSet()
            val graph = Graph(states,
                actions,
                actions.first().second.startTimestamp.toString(),
                actions.last().second.endTimestamp.toString(),
                actions.size,
                states.size,
                apk)
            val jsGraph = graph.toJsonVariable(gson)

            Files.write(jsonFile, jsGraph.toByteArray())
        }
    }

    // copy highlighed images into the visualization directory and adjust interaction to be unified to same config Id
    // FIXME it would be better to keep the information of original state configId's for the different actions to display this information
    // for this we have to update an option for the Edge class to include/ignore configId's and change the visualization script
    // to display such information properly (if possible with small images of the alternative config states in the selection view of a state)
    private fun markTargets(model: Model, imgDir: Path): List<Pair<Int, Interaction>> {
        val uidMap: MutableMap<UUID, ConcreteId> = HashMap()
        var idx = 0
        var isAQ = false
        return model.getPaths().first().getActions()
            .map { a ->
                Pair(idx, a).also {
                    // use same index for all actions within same actionQueue
                    when {
                        a.actionType.isQueueStart() -> isAQ = true
                        a.actionType.isQueueEnd() -> {
                            isAQ = false; idx += 1
                        }
                        else -> if (!isAQ) idx += 1
                    }
                }
            }
            .groupBy { (_, it) -> it.prevState.uid }.flatMap { (uid, indexedActions) ->
                var imgFile = imgDir.resolve("${indexedActions.first().second.prevState.toString()}.jpg").toFile()
                if (!imgFile.exists()) {
                    imgFile = Files.list(imgDir).filter { it.fileName.startsWith(uid.toString()) && it.fileName.endsWith(".jpg") }.findFirst().orElseGet { Paths.get("Error") }.toFile()
                }
                val img = if (imgFile.exists()) ImageIO.read(imgFile) else null
                if (!imgFile.exists() || img == null) indexedActions// if we cannot find a source img we are not touching the actions
                else {
                    uidMap[uid] = fromString(imgFile.name.replace(".jpg", ""))!!
                    val configId = uidMap[uid]!!.configId
                    val targets = indexedActions.filter { (_, action) -> action.targetWidget != null }
                    highlightWidget(img, targets.map { it.second.targetWidget!! }, targets.map { it.first }) // highlight each action in img
                    ImageIO.write(img, "jpg", imgDir.resolve("${ConcreteId(uid, configId)}.jpg").toFile())
                    // manipulate the action datas to replace config-id's
                    indexedActions.map { (i, action) ->
                        Pair(i, action.copy(prevState = ConcreteId(uid, configId), resState = uidMap.getOrDefault(action.resState.uid, action.resState)))
                    }
                }
            }
    }

}
