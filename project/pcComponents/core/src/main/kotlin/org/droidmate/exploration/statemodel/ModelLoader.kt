package org.droidmate.exploration.statemodel

import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.getValue
import com.natpryce.konfig.parseArgs
import com.natpryce.konfig.stringType
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.stateFileExtension
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.traceFilePrefix
import org.droidmate.configuration.ConfigProperties.ModelProperties.path.statesSubDir
import org.droidmate.debug.debugT
import org.droidmate.exploration.statemodel.ModelConfig.Companion.defaultWidgetSuffix
import org.droidmate.exploration.statemodel.features.ModelFeature
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.streams.toList

open class ModelLoader(protected val config: ModelConfig) {  // TODO integrate logger for the intermediate processing steps
	private val model = Model.emptyModel(config)

	private val job = Job()
	private val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ModelParsing"), parent = job)

	@Suppress("UNUSED_PARAMETER")
	private fun log(msg: String) {}//= println("[${Thread.currentThread().name}] $msg")

	/** temporary map of all processed states for trace parsing */
	private val stateQueue: MutableMap<ConcreteId,Deferred<StateData>> = ConcurrentHashMap()

	protected fun execute(watcher: LinkedList<ModelFeature>): Model{
		// the very first state of any trace is always an empty state which is automatically added on Model initialization
		StateData.emptyState.let{ stateQueue[it.stateId] = async { it } }
		val producer = traceProducer()
		repeat(5){ traceProcessor( producer, watcher ) }  // process up to 5 exploration traces in parallel
		runBlocking {
			log("wait for children completion")
			job.joinChildren() } // wait until all traces were processed (the processor adds the trace to the model)
		stateQueue.clear()
		widgetQueue.clear()
		return model
	}

	protected open fun traceProducer() = produce<Path>(context, parent = job, capacity = 5){
		log("TRACE PRODUCER CALL")
		Files.list(Paths.get(config.baseDir.toUri())).filter { it.fileName.toString().startsWith(config[traceFilePrefix]) }
				.also{
			for( p in it){	send(p)	}
		}
	}

	private fun traceProcessor(channel: ReceiveChannel<Path>, watcher: LinkedList<ModelFeature>) = launch(context, parent = job){
		channel.consumeEach { tracePath ->
			log("process path $tracePath")
			synchronized(model) { model.initNewTrace(watcher) }.let { trace ->
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
		file.bufferedReader().use { return it.lines().skip(skip).toList().also {
			if(path.toUri().toString().contains("trace")) {
				log(" FILE-Content ")
				it.forEach { log(it) }
				log(" END FileContent")
			}
		} }
	}
	private inline fun <reified T> P_processLines(path: Path, skip: Long = 1, crossinline lineProcessor: (List<String>) -> Deferred<T>): List<Deferred<T>> {
		log("call P_processLines for ${path.toUri()}")
		getFileContent(path,skip)?.let { br ->	// skip the first line (headline)
			assert(br.count() > 0, { "ERROR on model loading: file ${path.fileName} does not contain any entries" })
			return br.map { line -> lineProcessor(line.split(config[sep]).map { it.trim() }) }
		} ?: return emptyList()
	}
	private val stateTask: (ConcreteId)->Deferred<StateData> = { key -> async(CoroutineName("parseState $key"),parent = job){ P_parseState(key)} }

	/** compute for each line in the trace file the ActionData object and the resulting StateData object */
	protected val _actionParser: (List<String>) -> Deferred<Pair<ActionData, StateData>> = { entries ->
		log("parse action $entries")
		async(CoroutineName("actionParser"), parent = job) {
		// we createFromString the source state and target widget if there is any
		val resState = idFromString(entries[ActionData.resStateIdx]).let { resId ->
			log("parse result: $resId")
			// parse the result state with the contained widgets and queue them to make them available to other coroutines
			stateQueue.computeIfAbsent(resId, stateTask).also { launch{ assert(it.await().stateId == resId, {"ERROR result State $it should have id $resId"})} }
		}
		val targetWidget = entries[ActionData.widgetIdx].let { widgetIdString ->
			if (widgetIdString == "null") null
			else idFromString(widgetIdString).let{ targetWidgetId->
				log("wait for srcState ${entries[ActionData.srcStateIdx]}")
				idFromString(entries[ActionData.srcStateIdx]).let { srcId ->
					stateQueue.computeIfAbsent(srcId, stateTask)
							.await().let { srcState ->
								log("SRC-State $srcId computed")
								assert(srcState.stateId == srcId, {" ERROR source state $srcState should have id $srcId"})
								srcState.widgets.find { it.id == targetWidgetId }
										.also { assert(it != null, {" ERROR could not find target widget $targetWidgetId in source state $srcState" }) }
							}
				}
			}
		}
		Pair(ActionData.createFromString(entries, targetWidget, config[sep]), resState.await()).also { log("\n computed TRACE ${entries[ActionData.resStateIdx]}: ${it.first.actionString()}") }
	}}
	protected open fun getStateFile(stateId: ConcreteId): Triple<Path,Boolean,String>{
		val contentPath = Files.list(Paths.get(config.stateDst.toUri())).toList().first {
			it.fileName.toString().startsWith( stateId.dumpString()+ defaultWidgetSuffix ) }
		return contentPath.fileName.toString().let {
			Triple(contentPath, it.contains("HS"), it.substring(it.indexOf("_PN-")+4,it.indexOf(config[stateFileExtension])))
		}
	}

	protected suspend fun P_parseState(stateId: ConcreteId):StateData {
		log("parse state $stateId")
		val(contentPath,isHomeScreen,topPackage) = getStateFile(stateId)
		return mutableSetOf<Widget>().apply {	// create the set of contained elements (widgets)
			log(" parse file ${contentPath.toUri()}")
				val widgets = P_processLines(path = contentPath, lineProcessor = _widgetParser).map{it.await()}

					widgets.forEach {
					log("await for each")
					it.also {
						// add the parsed widget to temporary set AND initialize the parent property
						log(" add widget $it")
						add(it)
					}
				}
		}.let {
			if (it.isNotEmpty())
				StateData.fromFile(it,isHomeScreen,topPackage).also { newState -> model.addState(newState) }
			else StateData.emptyState
		}.also {
					log("computed state $stateId with ${it.widgets.size} widgets")
					assert(stateId == it.stateId, {
						"ERROR on state parsing inconsistent UUID created ${it.stateId} instead of $stateId" }) }
	}

	/** temporary map of all processed widgets for state parsing */
	private val widgetQueue: MutableMap<ConcreteId,Deferred<Widget>> = ConcurrentHashMap()
	protected val _widgetParser: (List<String>) -> Deferred<Widget> = { line ->
		log("parse widget $line")
		Pair((UUID.fromString(line[Widget.idIdx.first])),UUID.fromString(line[Widget.idIdx.second])).let { widgetId ->
			widgetQueue.computeIfAbsent(widgetId) { id ->
				log("parse widget absent $id")
				async(CoroutineName("parseWidget $id"), parent = job) {
					Widget.fromString(line).also { widget ->
						model.S_addWidget(widget)  // add the widget to the model if it didn't exist yet
						assert(id == widget.id, { "ERROR on widget parsing inconsistent ID created ${widget.id} instead of $id" })
					}
				}
			}
		}
	}

	companion object {  // FIXME watcher state restoration requires eContext.onUpdate function & model.onUpdate currently only onActionUpdate is supported
		@JvmStatic fun loadModel(config: ModelConfig, watcher: LinkedList<ModelFeature> = LinkedList()): Model{
			return debugT("model loading", { ModelLoader(config).execute(watcher) }, inMillis = true)
		}

		@JvmStatic fun main(args: Array<String>) {  // helping function to identify differences of two state files  // --statesSubDir=.
			val id1 by stringType
			val id2 by stringType

			val config = ModelConfig("debug_diffs", true, cfg = parseArgs(args,	CommandLineOption(statesSubDir), CommandLineOption(id1),CommandLineOption(id2)).first)
			val loader = ModelLoader(config)

			runBlocking {
				val s1 = loader.P_parseState(idFromString(config[id1]))
				val s2 = loader.P_parseState(idFromString(config[id2]))
				val onlyInS1 = s1.widgets.filterNot { s2.widgets.contains(it) }.filter{s1.isRelevantForId(it)}
				println("widgets which are only in s1")
				onlyInS1.forEach{
					if(s1.isRelevantForId(it)) println(it.dataString("\t"))
				}
				println("\n widgets which are only in s2")
				val onlyInS2 = s2.widgets.filterNot { s1.widgets.contains(it) }.filter{s2.isRelevantForId(it)}
				onlyInS2.forEach{
					if(s2.isRelevantForId(it)) println(it.dataString("\t"))
				}

			}
		}

	} /** end COMPANION **/

}