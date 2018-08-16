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

package org.droidmate.exploration.statemodel

import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.getValue
import com.natpryce.konfig.parseArgs
import com.natpryce.konfig.stringType
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.stateFileExtension
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.traceFilePrefix
import org.droidmate.debug.debugT
import org.droidmate.deviceInterface.guimodel.P
import org.droidmate.deviceInterface.guimodel.toUUID
import org.droidmate.exploration.statemodel.ModelConfig.Companion.defaultWidgetSuffix
import org.droidmate.exploration.statemodel.features.ModelFeature
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.streams.toList

open class ModelLoader(protected val config: ModelConfig, private val customWidgetIndicies: Map<P,Int> = P.defaultIndicies) {  // TODO integrate logger for the intermediate processing steps
	private val model = Model.emptyModel(config)

	private val jobName = "ModelParsing ${config.appName}(${config.baseDir})"
	private val job = Job()
	private val logger = Logger.getLogger(this::class.java.simpleName)

	private fun context(name:String, parent:Job = job) = newCoroutineContext(context = CoroutineName(name), parent = parent)

	@Suppress("UNUSED_PARAMETER")
	private fun log(msg: String) = if(config[ConfigProperties.Core.debugMode] && msg.contains("5498cd1f-c4c6-3014-a3b3-9e9e795c5631")){ println("[${Thread.currentThread().name}] $msg") } else {}

	/** temporary map of all processed states for trace parsing */
	private val stateQueue: MutableMap<ConcreteId,Deferred<StateData>> = ConcurrentHashMap()

	protected fun execute(watcher: LinkedList<ModelFeature>): Model{
		// the very first state of any trace is always an empty state which is automatically added on Model initialization
		StateData.emptyState.let{ stateQueue[it.stateId] = async(CoroutineName("empty State")) { it } }
		val producer = traceProducer()
		repeat(5){ traceProcessor( producer, watcher ) }  // process up to 5 exploration traces in parallel
		runBlocking {
			log("wait for children completion")
			job.joinChildren() } // wait until all traces were processed (the processor adds the trace to the model)
		job.invokeOnCompletion { exception -> if(exception!=null) {
			throw RuntimeException("\n---------------------------\n ERROR while parsing model $jobName",exception)}
		}
		stateQueue.clear()
		widgetQueue.clear()
		return model
	}

	protected open fun traceProducer() = produce<Path>(context(jobName), capacity = 5){
		log("TRACE PRODUCER CALL")
		Files.list(Paths.get(config.baseDir.toUri())).use { s ->
            s.filter { it.fileName.toString().startsWith(config[traceFilePrefix]) }
                    .also {
                        for (p in it) {
                            send(p)
                        }
                    }
        }
	}

	private fun traceProcessor(channel: ReceiveChannel<Path>, watcher: LinkedList<ModelFeature>) = launch(context(jobName)){
		channel.consumeEach { tracePath ->
			logger.info("process path $tracePath")
			val traceId = tracePath.fileName.toString().removePrefix(config[traceFilePrefix]).toUUID()
			synchronized(model) { model.initNewTrace(watcher, traceId) }.let { trace ->
				P_processLines(tracePath, lineProcessor = _actionParser).let { actionPairs ->  // use maximal parallelism to process the single actions/states
					if (watcher.isEmpty()){
						val resState = actionPairs.last().await().second
						log(" wait for completion of actions")
						trace.updateAll(actionPairs.map { it.await().first }, resState)
					}  // update trace actions
					else {
						log(" wait for completion of EACH action")
						actionPairs.forEach { it.await().let{ (action,resState) -> trace.update(action, resState) }}
					}
				}
			}
			log("CONSUMED trace $tracePath")
		}
	}

	protected open fun getFileContent(path:Path,skip: Long): List<String>? = path.toFile().let { file ->
		log("\n getFileContent skip=$skip, path= ${path.toUri()} \n")

		if (!file.exists()) { return null } // otherwise this state has no widgets

        return BufferedReader(FileReader(file)).use {
            it.lines().skip(skip).toList()
        }
	}



    private inline fun <reified T> P_processLines(path: Path, skip: Long = 1, crossinline lineProcessor: (List<String>) -> Deferred<T>): List<Deferred<T>> {
		log("call P_processLines for ${path.toUri()}")
		getFileContent(path,skip)?.let { br ->	// skip the first line (headline)
			assert(br.count() > 0) { "ERROR on model loading: file ${path.fileName} does not contain any entries" }
			return br.map { line -> lineProcessor(line.split(config[sep]).map { it.trim() }) }
		} ?: return emptyList()
	}
	private val stateTask: (ConcreteId)->Deferred<StateData> = { key -> async(context("parseState $key")){ P_parseState(key)} }

	/** compute for each line in the trace file the ActionData object and the resulting StateData object */
	protected val _actionParser: (List<String>) -> Deferred<Pair<ActionData, StateData>> = { entries ->
		log("parse action $entries")
		async(context("actionParser ${entries[ActionData.srcStateIdx]}->${entries[ActionData.resStateIdx]}")) {
		// we createFromString the source state and target widget if there is any
		val resState = idFromString(entries[ActionData.resStateIdx]).let { resId ->
			log("parse result: $resId")
			// parse the result state with the contained widgets and queue them to make them available to other coroutines
			stateQueue.computeIfAbsent(resId, stateTask).also { launch(CoroutineName("assert stateId $resId")){ assert(it.await().stateId == resId) {"ERROR result State $it should have id $resId"} } }
		}
		val targetWidget = entries[ActionData.widgetIdx].let { widgetIdString ->
			if (widgetIdString == "null") null
			else idFromString(widgetIdString).let{ targetWidgetId->
				log("wait for srcState ${entries[ActionData.srcStateIdx]}")
				idFromString(entries[ActionData.srcStateIdx]).let { srcId ->
					stateQueue.computeIfAbsent(srcId, stateTask)
							.await().let { srcState ->
								log("SRC-State $srcId computed")
								assert(srcState.stateId == srcId) {" ERROR source state $srcState should have id $srcId"}
								srcState.widgets.find { it.id == targetWidgetId }
										.also {
											assert(it != null) {" ERROR could not find target widget $targetWidgetId in source state $srcState" } }
							}
				}
			}
		}
		Pair(ActionData.createFromString(entries, targetWidget, config[sep]), resState.await()).also { log("\n computed TRACE ${entries[ActionData.resStateIdx]}: ${it.first.actionString()}") }
	}}
	protected open fun getStateFile(stateId: ConcreteId): Triple<Path,Boolean,String>{
		val contentPath = Files.list(Paths.get(config.stateDst.toUri())).use { it.toList() }.first {
			it.fileName.toString().startsWith( stateId.dumpString()+ defaultWidgetSuffix ) }
		return contentPath.fileName.toString().let {
			Triple(contentPath, it.contains("HS"), it.substring(it.indexOf("_PN-")+4,it.indexOf(config[stateFileExtension])))
		}
	}

	private fun computeActableDescendent(widgets: Collection<Widget>){
		val toProcess: LinkedList<ConcreteId> = LinkedList()
		widgets.filter { it.properties.actable && it.parentId != null }.forEach {
			toProcess.add(it.parentId!!) }
		var i=0
		while (i<toProcess.size){
			val n = toProcess[i++]
			widgets.find { it.id == n }?.run {
				this.properties.hasActableDescendant = true
				if(parentId != null && !toProcess.contains(parentId!!)) toProcess.add(parentId!!)
			}
		}
	}

	protected suspend fun P_parseState(stateId: ConcreteId):StateData {
		logger.info("parse state $stateId")
		val(contentPath,isHomeScreen,topPackage) = getStateFile(stateId)
		val widgets = P_processLines(path = contentPath, lineProcessor = _widgetParser).map{it.await()}
		computeActableDescendent(widgets)
		return mutableSetOf<Widget>().apply {	// create the set of contained elements (widgets)
			log(" parse file ${contentPath.toUri()}")

			widgets.forEach { w ->
				log("await for each")
				// add the parsed widget to temporary set AND initialize the parent property
				log(" add widget $w")
				add(w.copy().apply { parentId = w.parentId })
			}
		}.let { widgetSet ->
			if (widgetSet.isNotEmpty())
				StateData.fromFile(widgetSet,isHomeScreen,topPackage).also { newState ->
					val lS = widgets.filter{ it.usedForStateId }
					val nS = newState.widgets.filter {
						newState.isRelevantForId(it)   // IMPORTANT: use this call instead of accessing usedForState property because the later is only initialized after the uid is accessed
					}
					if(lS.isNotEmpty()) {
						val uidC = nS.containsAll(lS) && lS.containsAll(nS)
						val nOnly = nS.minus(lS)
						val lOnly = lS.minus(nS)
						assert(uidC) {
							"ERROR different set of widgets used for UID computation used \n ${nOnly.map { it.id }}\n instead of \n ${lOnly.map { it.id }}"
						}
					}
					model.addState(newState)
				}
			else StateData.emptyState
		}.also {
			log("computed state $stateId with ${it.widgets.size} widgets")
			assert(stateId == it.stateId)
			{ "ERROR on state parsing inconsistent UUID created ${it.stateId} instead of $stateId" }
		}
	}

	// FIXME different states may contain widgets with the same ID (uid,imgId+propertyId) but different ParentIds -> use string.hashcode as key value instead
	/** temporary map of all processed widgets for state parsing */
	private val widgetQueue: MutableMap<ConcreteId,Deferred<Widget>> = ConcurrentHashMap()
	protected val _widgetParser: (List<String>) -> Deferred<Widget> = { line ->
		log("parse widget $line")
		val wConfigId = UUID.fromString(line[Widget.idIdx.second]) + line[P.ImgId.idx(customWidgetIndicies)].asUUID()
//		val parentId = line[P.ParentID.idx(customWidgetIndicies)].let { if (it == "null") emptyId else idFromString(it) }
//		Pair((UUID.fromString(line[Widget.idIdx.first])), wConfigId+parentId.first+parentId.second).let { widgetIdPlusParent ->
//			widgetQueue.computeIfAbsent(widgetIdPlusParent) {
				val id = ConcreteId(UUID.fromString(line[Widget.idIdx.first]),wConfigId)
				log("parse widget absent $id")
				async(context("parseWidget $id")) {
					Widget.fromString(line,customWidgetIndicies).also { widget ->
						model.S_addWidget(widget)  // add the widget to the model if it didn't exist yet
						assert(id == widget.id)	{ "ERROR on widget parsing inconsistent ID created ${widget.id} instead of $id" }
					}
				}
//			}
//		}
	}

	companion object {  // FIXME watcher state restoration requires eContext.onUpdate function & model.onUpdate currently only onActionUpdate is supported
		@JvmStatic fun loadModel(config: ModelConfig, watcher: LinkedList<ModelFeature> = LinkedList()): Model{
			return debugT("model loading", { ModelLoader(config).execute(watcher) }, inMillis = true)
		}

//			val config = ModelConfig("debug_diffs", true, cfg = parseArgs(args,	CommandLineOption(statesSubDir), CommandLineOption(id1),CommandLineOption(id2)).first)
//			val loader = ModelLoader(config)
//
//			runBlocking {
//				val s1 = loader.P_parseState(idFromString(config[id1]))
//				val s2 = loader.P_parseState(idFromString(config[id2]))
//				val onlyInS1 = s1.widgets.filterNot { s2.widgets.contains(it) }.filter{s1.isRelevantForId(it)}
//				println("widgets which are only in s1")
//				onlyInS1.forEach{
//					if(s1.isRelevantForId(it)) println(it.dataString("\t"))
//				}
//				println("\n widgets which are only in s2")
//				val onlyInS2 = s2.widgets.filterNot { s1.widgets.contains(it) }.filter{s2.isRelevantForId(it)}
//				onlyInS2.forEach{
//					if(s2.isRelevantForId(it)) println(it.dataString("\t"))
//				}
//
//			}

		/**
		 * helping/debug function to manually load a model.
		 * The directory containing the 'model' folder and the app name have to be specified, e.g.
		 * '--Output-outputDir=pathToModelDir --appName=sampleApp'
		 * --Core-debugMode=true (optional for enabling print-outs)
		 */
		@JvmStatic fun main(args: Array<String>) {
			// stateDiff(args)
			val appName by stringType
			val cfg = parseArgs(args,
					CommandLineOption(ConfigProperties.Output.outputDir), CommandLineOption(ConfigProperties.Core.debugMode),
					CommandLineOption(appName)
			).first
			val config = ModelConfig(cfg[appName], true, cfg = cfg)
			val m = ModelLoader.loadModel(config)
			println(m)
		}

	} /** end COMPANION **/

}