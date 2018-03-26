package org.droidmate.device.datatypes.statemodel

import kotlinx.coroutines.experimental.*
import org.droidmate.debug.debugT
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashSet
import kotlin.streams.toList
import kotlin.system.measureTimeMillis

internal operator fun UUID.plus(uuid: UUID): UUID {
	return UUID(this.mostSignificantBits + uuid.mostSignificantBits, this.leastSignificantBits + uuid.mostSignificantBits)
}

internal inline fun <T> P_processLines(file: File, sep: String, skip: Long = 1, crossinline lineProcessor: (List<String>) -> Deferred<T>): List<Deferred<T>> {
	file.bufferedReader().lines().skip(skip).use {
		it.toList().let { br ->
			// skip the first line (headline)
			assert(br.count() > 0, { "ERROR on model loading: file ${file.name} does not contain any entries" })
			return br.map { line -> lineProcessor(line.split(sep).map { it.trim() }) }
		}
	}
}

/** s_* should be only used in sequential context as it currently does not handle parallelism*/
@Suppress("unused", "MemberVisibilityCanBePrivate")
class Model private constructor(val config: ModelDumpConfig) {
	private val states = HashSet<StateData>()
	private val widgets = HashSet<Widget>()
	private val paths = HashSet<Trace>()

	fun getStates(): Set<StateData> = states // return a view to the data
	fun addState(s: StateData) = synchronized(states) {
		states.find { x -> x.stateId == s.stateId } ?: states.add(s)
	}

	fun getState(id: ConcreteId) = states.find { it.stateId == id }

	private val widgetAdder: LinkedList<Deferred<Unit>> = LinkedList()
	fun getWidgets(): Set<Widget> = widgets
	fun addWidget(w: Widget) = synchronized(widgets) {
		widgets.find { it.id == w.id } ?: widgets.add(w)
	}

	fun waitForOverallWidgetsUpdates() = widgetAdder.apply { removeAll { it.isCompleted } }

	fun getPaths(): Set<Trace> = paths
	fun addTrace(t: Trace) = paths.add(t)

	fun S_findWidget(predicate: (Widget) -> Boolean) = widgets.find(predicate)
	val findWidget: (uuid: String, c: Collection<Widget>) -> Widget? = { id, c ->
		findWidgetOrElse(id, c) { throw RuntimeException("ERROR on state parsing, the target widget $id was not instantiated correctly") }
	}

	inline fun findWidgetOrElse(uuid: String, widgets: Collection<Widget> = getWidgets(), crossinline otherwise: (UUID) -> Widget): Widget? {
		return if (uuid == "null") null
		else UUID.fromString(uuid).let { synchronized(widgets) { widgets.find { w -> w.uid == it } ?: otherwise(it) } }
	}

	private suspend fun P_findOrAddState(stateId: ConcreteId): StateData = states.find { s -> s.stateId == stateId }  // allow parsing in parallel but ensure atomic adding
			?: P_parseState(stateId).also { addState(it) }

	fun P_dumpModel(config: ModelDumpConfig) = launch(CoroutineName("Model-dump")) {
		paths.map { t -> launch(CoroutineName("trace-dump")) { t.dump(config) } }.let { traceDump ->
			states.map { s -> launch(CoroutineName("state-dump ${s.uid}")) { s.dump(config) } }.forEach { it.join() }
			traceDump.forEach { it.join() }
		}
	}

	private var uTime: Long = 0
	/** update the model with any [action] executed as part of an execution [trace] **/
	fun S_updateModel(action: ActionResult, trace: Trace) {
		measureTimeMillis {
			var s: StateData? = null
			measureTimeMillis {
				s = computeNewState(action, trace.interactedEditFields)
			}.let { println("state computation takes $it millis for ${s!!.widgets.size}") }
			s?.also { newState ->
				launch { newState.widgets } // initialize the widgets in parallel
//				val traceUpdate = launch{ debugT("trace update",{
				trace.update(action, newState)
//					}) }
				launch { newState.dump(config) }
				launch {
					//traceUpdate.join();
					trace.dump(config)
				}
				if (config.dumpImg) launch {
					//traceUpdate.join()
					trace.last()!!.screenshot.let {
						// if there is any screen-shot copy it to the state extraction directory
						java.io.File(config.statePath(newState.stateId, "_${newState.configId}", "png")).let { file ->
							if (!file.exists()) java.nio.file.Paths.get(it)?.toFile()?.copyTo(file)
						}
					}
				}

//						widgetAdder.add(async{
				launch {
					debugT("${newState.widgets.size} widget adding ", {
						//FIXME do this via actor to ensure synchroniced add/get
//					widgets.addAll(newState.widgets)
						newState.widgets.forEach { addWidget(it) }
					})
//					})
				}
				// FIXME this can occationally take much time, therefore create an actor for it as well
				launch { debugT("state adding", { states.add(newState) }) }
//				debugT("model join trace update",{ runBlocking{traceUpdate.join()} })  // wait until we are sure the model is completely updated
			}
		}.let {
			println("model update took $it millis")
			uTime += it
			println("---------- average model update time ${uTime / trace.size} ms overall ${uTime / 1000.0} seconds --------------")
		}
	}

	private fun computeNewState(action: ActionResult, interactedEF: Map<UUID, List<Pair<StateData, Widget>>>): StateData {
		debugT("compute Widget set ", { action.getWidgets(config) })
				.let { widgets ->
					// compute all widgets existing in the current state

					debugT("compute result State for ${widgets.size}\n", { action.resultState(widgets) }).let { state ->
						// revise state if it contains previously interacted edit fields

						return debugT("special widget handling", {
							if (state.hasEdit) interactedEF[state.iEditId]?.let {
								action.resultState(lazy { handleEditFields(state, it) })
							} ?: state
							else state
						})
//					return s!! //}
//				return state
					}
				}
	}

//	val editTask = {state:StateData,(iUid, widgets):Pair<UUID,List<Widget>> ->
//		if (state.idWhenIgnoring(widgets) == iUid &&
//				widgets.all { candidate -> state.widgets.any { it.xpath == candidate.xpath } })
//			state.widgets.map { w -> w.apply { uid = widgets.find { it.uid == w.uid }?.uid ?: w.uid } } // replace with initial uid
//		else null
//	}
	/** check for all edit fields of the state if we already interacted with them and thus potentially changed their text property, if so overwrite the uid to the original one (before we interacted with it) */
	//TODO this takes unusual much time
	private val handleEditFields: (StateData, List<Pair<StateData, Widget>>) -> List<Widget> = { state, interactedEF ->
		//		async {
		// different states may coincidentally have the same iEditId => grouping and check which (if any) is the same conceptional state as [state]
		debugT("candidate computation", {
			interactedEF.groupBy { it.first }.map { (s, pairs) ->
				pairs.map { it.second }.let { widgets -> s.idWhenIgnoring(widgets) to widgets }
			}
		})
				.let { candidates ->

					//			debugT("parallel edit field",{
//				runBlocking {
//					it.map { //(iUid, widgets) ->
//						async {	editTask(state,it) }
//					}.mapNotNull { it.await() }
//				}
//			})
//			debugT("parallel edit unconfined",{
//				runBlocking {
//					it.map { //(iUid, widgets) ->
//						async(Unconfined) {	editTask(state,it) }
//					}.mapNotNull { it.await() }
//				}
//			})
//FIXME same issue for Password fields?
					debugT("sequential edit field", {
						// faster then parallel alternatives
						candidates.fold(state.widgets, { res, (iUid, widgets) ->
							// determine which candidate matches the current [state] and replace the respective widget.uid`s
							if (state.idWhenIgnoring(widgets) == iUid &&
									widgets.all { candidate -> state.widgets.any { it.xpath == candidate.xpath } })
								state.widgets.map { w -> w.apply { uid = widgets.find { it.uid == w.uid }?.uid ?: w.uid } } // replace with initial uid
							else res // contain different elements => wrong state candidate
						})
					})
				}
//		}
	}


	private val _actionParser: (List<String>) -> Deferred<ActionData> = { entries ->
		async(CoroutineName("ActionParsing-$entries")) {
			// we createFromString the source state and target widget if there is any
			stateIdFromString(entries[0]).let { srcId ->
				// pars the src state with the contained widgets and add the respective objects to our model
				ActionData.createFromString(entries, findWidget(entries[ActionData.widgetIdx], P_findOrAddState(srcId).widgets))
			}
		}
	}

	private suspend fun P_parseTrace(file: Path) {
		Trace().apply {
			P_processLines(file.toFile(), sep, lineProcessor = _actionParser).forEach { it.await().let { S_addAction(it) } }
		}.also { synchronized(paths) { paths.add(it) } }
	}

	private val _widgetParser: (List<String>) -> Deferred<Widget?> = { line ->
		line[Widget.idIdx].let { id ->
			async(CoroutineName("WidgetParsing-$id")) {
				findWidgetOrElse(id) { id ->
					Widget.fromString(line).also {
						addWidget(it)
					}.also {
						assert(id == it.uid, {
							"ERROR on widget parsing inconsistent UUID created ${it.uid} instead of $id"
						})
					}
				}
			}
		}
	}

	private suspend fun P_parseState(stateId: ConcreteId): StateData {
		return mutableSetOf<Widget>().apply {
			// create the set of contained elements (widgets)
			val contentFile = File(config.widgetFile(stateId))
			if (contentFile.exists())  // otherwise this state has no widgets
				P_processLines(file = contentFile, sep = sep, lineProcessor = _widgetParser).forEach {
					it.await()?.also {
						// add the parsed widget to temporary set AND initialize the parent property
						add(it)
						if (it.parentXpath != "") it.parentId = widgets.find { w -> w.xpath == it.parentXpath }?.id
					}
				}
		}.let {
			if (it.isNotEmpty())
				StateData.fromFile(it)
			else StateData.emptyState
		}
				.also { assert(stateId == it.stateId, { "ERROR on state parsing inconsistent UUID created ${it.uid} instead of $stateId" }) }
	}

	companion object {
		@JvmStatic
		fun emptyModel(config: ModelDumpConfig): Model = Model(config)

		// parallel parsing of multiple traces => need to lock states/widgets and paths when modifying them
		internal fun loadAppModel(appName: String): Model {
			return loadAppModel(ModelDumpConfig(appName))
		}

		fun loadAppModel(config: ModelDumpConfig): Model {
			return emptyModel(config).apply {
				Files.list(Paths.get(config.modelBaseDir)).filter { it.fileName.toString().startsWith(traceFilePrefix) }.map {
					launch(CoroutineName("TraceParsing")) { P_parseTrace(it) }
				}.forEach { runBlocking { it.join() } }
			}
		}
	}

	override fun toString(): String {
		return "Model[states=${states.size},widgets=${widgets.size},paths=${paths.size}]"
	}
}