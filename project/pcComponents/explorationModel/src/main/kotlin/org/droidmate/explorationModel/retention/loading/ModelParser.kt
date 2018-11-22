package org.droidmate.explorationModel.retention.loading

import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.getValue
import com.natpryce.konfig.parseArgs
import com.natpryce.konfig.stringType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.ConcreteId.Companion.fromString
import org.droidmate.explorationModel.config.*
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.retention.StringCreator.headerFor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.coroutines.coroutineContext

/** public interface, used to parse any model **/
object ModelParser{
	@JvmOverloads suspend fun loadModel(config: ModelConfig, watcher: LinkedList<ModelFeatureI> = LinkedList(),
	                                    autoFix: Boolean = false, sequential: Boolean = false, enablePrint: Boolean = false,
	                                    contentReader: ContentReader = ContentReader(config), enableChecks: Boolean = true,
	                                    customHeaderMap: Map<String,String> = emptyMap())
			: Model{
		if(sequential) return debugT("model loading (sequential)", {
			ModelParserS(config, compatibilityMode = autoFix, enablePrint = enablePrint, reader = contentReader, enableChecks = enableChecks).loadModel(watcher, customHeaderMap)
		}, inMillis = true)
		return debugT("model loading (parallel)", {
			ModelParserP(config, compatibilityMode = autoFix, enablePrint = enablePrint, reader = contentReader, enableChecks = enableChecks).loadModel(watcher, customHeaderMap)
		}, inMillis = true)
	}
}

internal abstract class ModelParserI<T,S,W>: ParserI<T, Pair<Interaction, State>>{
	abstract val config: ModelConfig
	abstract val reader: ContentReader
	abstract val stateParser: StateParserI<S, W>
	abstract val widgetParser: WidgetParserI<W>
	abstract val enablePrint: Boolean
	abstract 	val isSequential: Boolean

	override val logger: Logger = LoggerFactory.getLogger(javaClass)

	override val model by lazy{ Model.emptyModel(config) }
	protected val actionParseJobName: (List<String>)->String = { actionS ->
		"actionParser ${actionS[Interaction.Companion.ActionDataFields.Action.ordinal]}:${actionS[Interaction.srcStateIdx]}->${actionS[Interaction.resStateIdx]}"}

	// watcher state restoration for ModelFeatureI should be automatically handled via trace.updateAll (these are independent from the explorationContext)
	suspend fun loadModel(watcher: LinkedList<ModelFeatureI> = LinkedList(), customHeaderMap: Map<String,String> = emptyMap()): Model = withContext(CoroutineName("ModelParsing ${config.appName}(${config.baseDir})")+Job()){
		coroutineScope {  // this will wait for all coroutines launched in this scope
			stateParser.headerRenaming = customHeaderMap
			// the very first state of any trace is always an empty state which is automatically added on Model initialization
			addEmptyState()
			// start producer who just sends trace paths to the multiple trace processor jobs
			val producer = traceProducer()
			repeat(if(isSequential) 1 else 5)
			{ launch { traceProcessor( producer, watcher )} }  // process up to 5 exploration traces in parallel

		}
		clearQueues()
		return@withContext model
	}
	private fun clearQueues() {
		stateParser.queue.clear()
		widgetParser.queue.clear()
	}
	abstract suspend fun addEmptyState()

	protected open suspend fun traceProducer() = coroutineScope { produce<Path>(context = CoroutineName("trace Producer"), capacity = 5) {
		logger.trace("PRODUCER CALL")
		Files.list(Paths.get(config.baseDir.toUri())).use { s ->
			s.filter { it.fileName.toString().startsWith(config[ConfigProperties.ModelProperties.dump.traceFilePrefix]) }
					.also {
						for (p in it) {
							send(p)
						}
					}
		}
	}}

	private val modelMutex = Mutex()

	private suspend fun traceProcessor(channel: ReceiveChannel<Path>, watcher: LinkedList<ModelFeatureI>): Unit = coroutineScope {
		logger.trace("trace processor launched")
		if(enablePrint) logger.info("trace processor launched")
		channel.consumeEach { tracePath ->
			if(enablePrint) logger.info("\nprocess TracePath $tracePath")
			val traceId =
					try {
						UUID.fromString(tracePath.fileName.toString().removePrefix(config[ConfigProperties.ModelProperties.dump.traceFilePrefix]).removeSuffix(config[ConfigProperties.ModelProperties.dump.traceFileExtension]))
					}catch(e:IllegalArgumentException){ // tests do not use valid UUIDs but rather int indices
						emptyUUID
					}
			modelMutex.withLock { model.initNewTrace(watcher, traceId) }
					.let { trace ->
						val actionPairs = reader.processLines(tracePath, lineProcessor = processor)
						// use maximal parallelism to process the single actions/states
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
			logger.debug("CONSUMED trace $tracePath")
		}
	}

	/** parse the action this function is called in the processor either asynchronous (Deferred) or sequential (blocking) */
	suspend fun parseAction(actionS: List<String>, scope: CoroutineScope): Pair<Interaction, State> {
		if(enablePrint) println("\n\t ---> parse action $actionS")
		val resState = stateParser.processor(actionS, scope).getState()
		val targetWidgetId = widgetParser.fixedWidgetId(actionS[Interaction.widgetIdx])

		val srcId = fromString(actionS[Interaction.srcStateIdx])!!
		val srcState = stateParser.queue.getOrDefault(srcId,stateParser.parseIfAbsent(coroutineContext)(srcId)).getState()
		val targetWidget = targetWidgetId?.let { tId ->
			srcState.widgets.find { it.id == tId } ?: run{
				logger.warn("ERROR target widget $tId cannot be found in src state")
				null
			}
		}
		val fixedActionS = mutableListOf<String>().apply { addAll(actionS) }
		fixedActionS[Interaction.resStateIdx] = resState.stateId.toString()
		fixedActionS[Interaction.srcStateIdx] = srcState.stateId.toString()  //do NOT use local val srcId as that may be the old id

		if(actionS!=fixedActionS)
			println("id's changed due to automatic repair new action is \n $fixedActionS\n instead of \n $actionS")

		return Pair(Interaction.createFromString(fixedActionS, targetWidget, config[ConfigProperties.ModelProperties.dump.sep]), resState)
				.also { log("\n computed TRACE ${actionS[Interaction.resStateIdx]}: ${it.first.actionString()}") }
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
//			runBlocking {
			val appName by stringType
			val cfg = parseArgs(args,
					CommandLineOption(ConfigProperties.Output.outputDir),
					CommandLineOption(appName)
			).first
			val config = ModelConfig(cfg[appName], true, cfg = cfg)
			//REMARK old models are most likely not compatible, since the idHash may not be unique (probably some bug on UI extraction)
			val headerMap = mapOf(
					"HashId" to headerFor(UiElementPropertiesI::idHash)!!,
					"widget class" to headerFor(UiElementPropertiesI::className)!!,
					"Text" to headerFor(UiElementPropertiesI::text)!!,
					"Description" to headerFor(UiElementPropertiesI::contentDesc)!!,
					"Enabled" to headerFor(UiElementPropertiesI::enabled)!!,
					"Visible" to headerFor(UiElementPropertiesI::definedAsVisible)!!,
					"Clickable" to headerFor(UiElementPropertiesI::clickable)!!,
					"LongClickable" to headerFor(UiElementPropertiesI::longClickable)!!,
					"Scrollable" to headerFor(UiElementPropertiesI::scrollable)!!,
					"Checked" to headerFor(UiElementPropertiesI::checked)!!,
					"Editable" to headerFor(UiElementPropertiesI::isInputField)!!,
					"Focused" to headerFor(UiElementPropertiesI::focused)!!,
					"IsPassword" to headerFor(UiElementPropertiesI::isPassword)!!,
					"XPath" to headerFor(UiElementPropertiesI::xpath)!!,
					"PackageName" to headerFor(UiElementPropertiesI::packageName)!!
					//MISSING translation of BoundsX,..Y,..Width,..Height to visibleBoundaries
					//MISSING instead of parentHash we had parentID persisted
			)

			val m =
			//				loadModel(config, autoFix = false, sequential = true)
					runBlocking { ModelParser.loadModel(config, autoFix = false, sequential = false, enablePrint = false//, customHeaderMap = headerMap
					)}
			println("performance test")
			var ts =0L
			var tp =0L
			runBlocking {
				repeat(10) {
					debugT("load time sequential", { ModelParserS(config).loadModel() },
							timer = { ts += it / 1000000 },
							inMillis = true)
					debugT("load time parallel", { ModelParserP(config).loadModel() },
							timer = { tp += it / 1000000 },
							inMillis = true)
				}
			}
			println(" overall time \nsequential = $ts avg=${ts/10000.0} \nparallel = $tp avg=${tp/10000.0}")
			/** dump the (repaired) model */ /*
			runBlocking {
				m.dumpModel(ModelConfig("repaired-${config.appName}", cfg = cfg))
				m.modelDumpJob.joinChildren()
			}
			// */
			println("model load finished: ${config.appName} $m")
		}
//		}
	} /** end COMPANION **/

}

internal open class ModelParserP(override val config: ModelConfig, override val reader: ContentReader = ContentReader(config),
                                 override val compatibilityMode: Boolean = false, override val enablePrint: Boolean = false,
                                 override val enableChecks: Boolean = true)
	: ModelParserI<Deferred<Pair<Interaction, State>>, Deferred<State>, Deferred<UiElementPropertiesI>>() {
	override val isSequential: Boolean = false

	override val widgetParser by lazy { WidgetParserP(model, compatibilityMode, enableChecks) }
	override val stateParser  by lazy { StateParserP(widgetParser, reader, model, compatibilityMode, enableChecks) }

	override val processor: suspend (s: List<String>, CoroutineScope) -> Deferred<Pair<Interaction, State>> = { actionS, scope ->
		CoroutineScope(coroutineContext+Job()).async(CoroutineName(actionParseJobName(actionS))) { parseAction(actionS, scope) }
	}

	override suspend fun addEmptyState() {
		State.emptyState.let{ stateParser.queue[it.stateId] =  CoroutineScope(coroutineContext).async(CoroutineName("empty State")) { it } }
	}

	override suspend fun getElem(e: Deferred<Pair<Interaction, State>>): Pair<Interaction, State> = e.await()

}

private class ModelParserS(override val config: ModelConfig, override val reader: ContentReader = ContentReader(config),
                           override val compatibilityMode: Boolean = false, override val enablePrint: Boolean = false,
                           override val enableChecks: Boolean = true)
	: ModelParserI<Pair<Interaction, State>, State, UiElementPropertiesI>() {
	override val isSequential: Boolean = true

	override val widgetParser by lazy { WidgetParserS(model, compatibilityMode, enableChecks) }
	override val stateParser  by lazy { StateParserS(widgetParser, reader, model, compatibilityMode, enableChecks) }

	override val processor: suspend (s: List<String>, CoroutineScope) -> Pair<Interaction, State> = { actionS:List<String>, scope ->
		parseAction(actionS, scope)
	}

	override suspend fun addEmptyState() {
		State.emptyState.let{ stateParser.queue[it.stateId] = it }
	}

	override suspend fun getElem(e: Pair<Interaction, State>): Pair<Interaction, State> = e

}