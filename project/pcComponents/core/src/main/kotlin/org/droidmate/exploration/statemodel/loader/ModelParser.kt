package org.droidmate.exploration.statemodel.loader

import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.getValue
import com.natpryce.konfig.parseArgs
import com.natpryce.konfig.stringType
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import org.droidmate.configuration.ConfigProperties
import org.droidmate.debug.debugT
import org.droidmate.exploration.statemodel.*
import org.droidmate.exploration.statemodel.features.ModelFeature
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/** public interface, used to parse any model **/
object ModelParser{
	@JvmOverloads @JvmStatic fun loadModel(config: ModelConfig, watcher: LinkedList<ModelFeature> = LinkedList(),
	                            autoFix: Boolean = false, sequential: Boolean = false, enablePrint: Boolean = false,
	                            contentReader: ContentReader = ContentReader(config), enableChecks: Boolean = true)
			: Model{
		if(sequential) return debugT("model loading (sequential)", {
			ModelParserS(config, compatibilityMode = autoFix, enablePrint = enablePrint, reader = contentReader, enableChecks = enableChecks).loadModel(watcher)
		}, inMillis = true)
		return debugT("model loading (parallel)", {
			ModelParserP(config, compatibilityMode = autoFix, enablePrint = enablePrint, reader = contentReader, enableChecks = enableChecks).loadModel(watcher)
		}, inMillis = true)
	}
}

// FIXME watcher state restoration requires eContext.onUpdate function & model.onUpdate currently only onActionUpdate is supported
internal abstract class ModelParserI<T,S,W>: ParserI<T,Pair<ActionData, StateData>>{
	abstract val config: ModelConfig
	abstract val reader: ContentReader
	abstract val stateParser: StateParserI<S,W>
	abstract val widgetParser: WidgetParserI<W>
	abstract val enablePrint: Boolean
	abstract 	val isSequential: Boolean

	override val logger: Logger = LoggerFactory.getLogger(javaClass)

	override val parentJob: Job = Job()
	override val model by lazy{ Model.emptyModel(config) }
	private val jobName by lazy{ "ModelParsing ${config.appName}(${config.baseDir})" }
	protected val actionParseJobName: (List<String>)->String = { actionS ->
		"actionParser ${actionS[ActionData.Companion.ActionDataFields.Action.ordinal]}:${actionS[ActionData.srcStateIdx]}->${actionS[ActionData.resStateIdx]}"}

	fun loadModel(watcher: LinkedList<ModelFeature> = LinkedList()): Model {
		// the very first state of any trace is always an empty state which is automatically added on Model initialization
		addEmptyState()
		// start producer who just sends trace paths to the multiple trace processor jobs
		val producer = traceProducer()
		repeat(if(isSequential) 1 else 5)
		{ traceProcessor( producer, watcher ) }  // process up to 5 exploration traces in parallel
		runBlocking(CoroutineName(jobName)) {
			log("wait for children completion")
//			parentJob.joinChildren() } // wait until all traces were processed (the processor adds the trace to the model)
			parentJob.children.forEach {
				it.join()
				it.invokeOnCompletion { exception ->
					if (exception != null) {
						parentJob.cancel(RuntimeException("Error in $it"))
						logger.error("\n---------------------------\n ERROR while parsing model $jobName : $it : ${it.children}")
						exception.printStackTrace()
					}
				}
			}
			parentJob.cancel()
			parentJob.cancel()
		}
		clearQueues()
		return model
	}
	private fun clearQueues() {
		stateParser.queue.clear()
		widgetParser.queue.clear()
	}
	abstract fun addEmptyState()

	protected open fun traceProducer() = CoroutineScope(newContext(jobName)).produce<Path>(capacity = 5) {
		logger.trace("PRODUCER CALL")
		Files.list(Paths.get(config.baseDir.toUri())).use { s ->
			s.filter { it.fileName.toString().startsWith(config[ConfigProperties.ModelProperties.dump.traceFilePrefix]) }
					.also {
						for (p in it) {
							send(p)
						}
					}
		}
	}

	private val modelMutex = Mutex()
	private fun traceProcessor(channel: ReceiveChannel<Path>, watcher: LinkedList<ModelFeature>) = CoroutineScope(newContext(jobName)).launch{
		logger.trace("trace processor launched")
		if(enablePrint) logger.info("trace processor launched")
		channel.consumeEach { tracePath ->
			if(enablePrint) logger.info("\nprocess TracePath $tracePath")
			val traceId =
					try {
						UUID.fromString(tracePath.fileName.toString().removePrefix(config[ConfigProperties.ModelProperties.dump.traceFilePrefix]).removeSuffix(config[ConfigProperties.ModelProperties.dump.traceFileExtension]))
					}catch(e:IllegalArgumentException){ // tests do not use valid UUIDs but rather int indicies
						emptyUUID
					}
			modelMutex.withLock { model.initNewTrace(watcher, traceId) }
					.let { trace ->
						reader.processLines(tracePath, lineProcessor = processor).let { actionPairs ->  // use maximal parallelism to process the single actions/states
							if (watcher.isEmpty()){
								val resState = getElem(actionPairs.last()).second
								logger.debug(" wait for completion of actions")
								trace.updateAll(actionPairs.map { getElem(it).first }, resState)
							}  // update trace actions
							else {
								logger.debug(" wait for completion of EACH action")
								actionPairs.forEach { getElem(it).let{ (action,resState) -> trace.update(action, resState) }}
							}
						}
					}
			logger.debug("CONSUMED trace $tracePath")
		}
	}

	/** parse the action this function is called in the processor either asynchronous (Deferred) or sequential (blocking) */
	suspend fun parseAction(actionS: List<String>): Pair<ActionData, StateData> {
		if(enablePrint) println("\n\t ---> parse action $actionS")
		val resState = stateParser.processor(actionS).getState()
		val targetWidgetId = widgetParser.fixedWidgetId(actionS[ActionData.widgetIdx])

		val srcId = idFromString(actionS[ActionData.srcStateIdx])
		val srcState = stateParser.queue.getOrDefault(srcId,currentScope(stateParser.parseIfAbsent)(srcId)).getState()
		val targetWidget = targetWidgetId?.let { tId ->
			srcState.widgets.find { it.id == tId } ?: run{
				logger.warn("ERROR target widget $tId cannot be found in src state")
				null
			}
		}
		val fixedActionS = mutableListOf<String>().apply { addAll(actionS) }
		fixedActionS[ActionData.resStateIdx] = resState.stateId.dumpString()
		fixedActionS[ActionData.srcStateIdx] = srcState.stateId.dumpString()  //do NOT use local val srcId as that may be the old id

		if(actionS!=fixedActionS)
			println("id's changed due to automatic repair new action is \n $fixedActionS\n instead of \n $actionS")

		return Pair(ActionData.createFromString(fixedActionS, targetWidget, config[ConfigProperties.ModelProperties.dump.sep]), resState)
				.also { log("\n computed TRACE ${actionS[ActionData.resStateIdx]}: ${it.first.actionString()}") }
	}

	@Suppress("ReplaceSingleLineLet")
	suspend fun S.getState() = this.let{ e ->  stateParser.getElem(e) }

	companion object {

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
			val m =
//				loadModel(config, autoFix = false, sequential = true)
					ModelParser.loadModel(config, autoFix = true, sequential = false, enablePrint = false)
//				debugT("load time parallel", { ModelParserP(config).loadModel() }, inMillis = true)
//				debugT("load time sequentiel", { ModelParserS(config).loadModel() }, inMillis = true)

			/** dump the (repaired) model */ /*
			runBlocking {
				m.P_dumpModel(ModelConfig("repaired-${config.appName}", cfg = cfg))
				m.modelDumpJob.joinChildren()
			}
			// */
			println("model load finished: ${config.appName} $m")
		}
	} /** end COMPANION **/

}

private class ModelParserP(override val config: ModelConfig, override val reader: ContentReader = ContentReader(config),
                           override val compatibilityMode: Boolean = false, override val enablePrint: Boolean = true,
                           override val enableChecks: Boolean)
	: ModelParserI<Deferred<Pair<ActionData, StateData>>, Deferred<StateData>, Deferred<Widget>>() {
	override val isSequential: Boolean = true

	override val widgetParser by lazy { WidgetParserP(model,parentJob, compatibilityMode, enableChecks) }
	override val stateParser  by lazy { StateParserP(widgetParser, reader, model, parentJob, compatibilityMode, enableChecks)}

	override val processor: suspend(s: List<String>) -> Deferred<Pair<ActionData, StateData>> = { actionS ->
		currentScope { async(CoroutineName(actionParseJobName(actionS))) { parseAction(actionS) } }
	}

	override fun addEmptyState() {
		StateData.emptyState.let{ stateParser.queue[it.stateId] = runBlocking{ async(CoroutineName("empty State")) { it } } }
	}

	override suspend fun getElem(e: Deferred<Pair<ActionData, StateData>>): Pair<ActionData, StateData> = e.await()

}

private class ModelParserS(override val config: ModelConfig, override val reader: ContentReader = ContentReader(config),
                           override val compatibilityMode: Boolean = false, override val enablePrint: Boolean = true,
                           override val enableChecks: Boolean)
	: ModelParserI<Pair<ActionData, StateData>, StateData, Widget >() {
	override val isSequential: Boolean = true

	override val widgetParser by lazy { WidgetParserS(model,parentJob, compatibilityMode, enableChecks) }
	override val stateParser  by lazy { StateParserS(widgetParser, reader, model, parentJob, compatibilityMode, enableChecks)}

	override val processor: suspend (List<String>) -> Pair<ActionData, StateData> = { actionS:List<String> ->
		parseAction(actionS)
	}

	override fun addEmptyState() {
		StateData.emptyState.let{ stateParser.queue[it.stateId] = it }
	}

	override suspend fun getElem(e: Pair<ActionData, StateData>): Pair<ActionData, StateData> = e

}