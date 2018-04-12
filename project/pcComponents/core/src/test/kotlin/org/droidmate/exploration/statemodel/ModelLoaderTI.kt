package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.channels.produce
import org.droidmate.exploration.statemodel.config.ConcreteId
import org.droidmate.exploration.statemodel.config.ModelConfig
import org.droidmate.exploration.statemodel.config.dump
import org.droidmate.exploration.statemodel.config.idFromString
import org.droidmate.exploration.statemodel.features.ModelFeature
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/** test interface for the model loader, which cannot be done with mockito due to coroutine incompatibility */
interface ModelLoaderTI{
	var testActions: Collection<ActionData>
	var testStates: Collection<StateData>

	fun execute(testActions: Collection<ActionData>, testStates: Collection<StateData>, watcher: LinkedList<ModelFeature> = LinkedList()): Model
	fun parseWidget(widget: Widget):Deferred<Widget?>

	// TODO these are state dependend => use very carefully in Unit-Tests
	val actionParser: (List<String>) -> Deferred<Pair<ActionData, StateData>>
	suspend fun parseState(stateId: ConcreteId):StateData

}

class ModelLoaderT(config: ModelConfig): ModelLoader(config), ModelLoaderTI {
	override lateinit var testActions: Collection<ActionData>
	override lateinit var testStates: Collection<StateData>

	private val traceContents get() =  testActions.map { it.actionString().also{ log(it)} }
	private fun log(msg: String) = println("TestModelLoader[${Thread.currentThread().name}] $msg")

	override fun traceProducer() = produce<Path>(capacity = 1){
		log("Produce trace paths")
		testActions.forEach { log(it.actionString()+"\n") }
		for (i in 0 until testActions.size){ send(Paths.get(config[dump.traceFilePrefix]+i.toString())) }
	}

	override fun getFileContent(path: Path, skip: Long): List<String>? = path.fileName.toString().let{ name ->
		log("getFileContent for ${path.toUri()}")
		if (name.startsWith(config[dump.traceFilePrefix]))
			listOf(traceContents[name.removePrefix(config[dump.traceFilePrefix]).toInt()])
		else
			idFromString( name.removeSuffix(ModelConfig.defaultWidgetSuffix)).let{ stateId ->
				testStates.find { s -> s.stateId == stateId }!!.widgetsDump(config[dump.sep])
			}
	}

	override fun execute(testActions: Collection<ActionData>, testStates: Collection<StateData>, watcher: LinkedList<ModelFeature>): Model {
//		log(testActions.)
		this.testActions = testActions
		this.testStates = testStates
		return execute(watcher)
	}

	override fun parseWidget(widget: Widget):Deferred<Widget?> = _widgetParser(widget.splittedDumpString(config[dump.sep]))

	override val actionParser: (List<String>) -> Deferred<Pair<ActionData, StateData>> = _actionParser
	override suspend fun parseState(stateId: ConcreteId): StateData = P_parseState(stateId)

}

