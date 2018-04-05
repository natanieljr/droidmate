package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import org.droidmate.debug.debugT
import org.droidmate.exploration.statemodel.config.*
import org.droidmate.exploration.statemodel.features.ModelFeature
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.streams.toList

class ModelLoader(val config: ModelConfig) {  // TODO integrate logger for the intermediate processing steps
	private val model = Model.emptyModel(config)

	private val job = Job()
	private val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ModelParsing"), parent = job)

	@Suppress("UNUSED_PARAMETER")
	fun log(msg: String){}// = println("[${Thread.currentThread().name}] $msg")

	/** temporary map of all processed states for trace parsing */
	private val stateQueue: MutableMap<ConcreteId,Deferred<StateData>> = ConcurrentHashMap()

	private fun execute(watcher: LinkedList<ModelFeature>): Model{
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

	private fun traceProducer() = produce<Path>(context, parent = job, capacity = 5){
		Files.list(Paths.get(config.baseDir)).filter { it.fileName.toString().startsWith(config[dump.traceFilePrefix]) }.also{
			for( p in it){	send(p)	}
		}
	}

	private fun traceProcessor(channel: ReceiveChannel<Path>, watcher: LinkedList<ModelFeature>) = launch(context, parent = job){
		channel.consumeEach { tracePath ->
			log("process path $tracePath")
			synchronized(model) { model.initNewTrace(watcher) }.let { trace ->
				P_processLines(tracePath.toFile(), lineProcessor = _actionParser).let { actionPairs ->  // use maximal parallelism to process the single actions/states
					if (watcher.isEmpty()){
						log(" wait for completion of actions")
						trace.updateAll(actionPairs.map { it.await().first }, actionPairs.last().await().second)
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

	private inline fun <T> P_processLines(file: File, skip: Long = 1, crossinline lineProcessor: (List<String>) -> Deferred<T>):Collection<Deferred<T>> {
		file.bufferedReader().lines().skip(skip).use {
			return it.toList().let { br ->
				// skip the first line (headline)
				assert(br.count() > 0, { "ERROR on model loading: file ${file.name} does not contain any entries" })
				br.map { line -> lineProcessor(
						line.split(config[dump.sep])
								.map { it.trim() }
				) }
			}
		}
	}

	private val stateTask: (ConcreteId)->Deferred<StateData> = { key -> async(CoroutineName("parseState $key"),parent = job){ P_parseState(key)} }

	/** compute for each line in the trace file the ActionData object and the resulting StateData object */
	private val _actionParser: (List<String>) -> Deferred<Pair<ActionData, StateData>> = { entries -> async(CoroutineName("actionParser"), parent = job) {
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
								assert(srcState.stateId == srcId, {" ERROR source state $srcState should have id $srcId"})
								srcState.widgets.find { it.id == targetWidgetId }
										.also { assert(it != null, {" ERROR could not find target widget $targetWidgetId in source state $srcState" }) }
							}
				}
			}
		}
		Pair(ActionData.createFromString(entries, targetWidget, config[dump.sep]), resState.await()).also { log("\n computed TRACE ${entries[ActionData.resStateIdx]}") }
	}}

	private suspend fun P_parseState(stateId: ConcreteId):StateData {
		log("parse state $stateId")
		return mutableSetOf<Widget>().apply {	// create the set of contained elements (widgets)
			val contentFile = File(config.widgetFile(stateId))

			log(" parse file ${contentFile.absolutePath}")
			if (contentFile.exists()){ // otherwise this state has no widgets
				P_processLines(file = contentFile, sep = config[dump.sep], lineProcessor = _widgetParser).forEach {
					log("await for each")
					it.await()?.also {
						// add the parsed widget to temporary set AND initialize the parent property
						log(" add widget $it")
						add(it)
					}
				}}
		}.let {
			if (it.isNotEmpty())
				StateData.fromFile(it).also { newState -> model.addState(newState) }
			else StateData.emptyState
		}.also {
					log("computed state $stateId with ${it.widgets.size} widgets")
					assert(stateId == it.stateId, { "ERROR on state parsing inconsistent UUID created ${it.stateId} instead of $stateId" }) }
	}

	/** temporary map of all processed widgets for state parsing */
	private val widgetQueue: MutableMap<ConcreteId,Deferred<Widget>> = ConcurrentHashMap()
	private val _widgetParser: (List<String>) -> Deferred<Widget?> = { line ->
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

	companion object {
		@JvmStatic fun loadModel(config: ModelConfig, watcher: LinkedList<ModelFeature> = LinkedList()): Model{
			return debugT("model loading", { ModelLoader(config).execute(watcher) }, inMillis = true)
		}

	} /** end COMPANION **/

}