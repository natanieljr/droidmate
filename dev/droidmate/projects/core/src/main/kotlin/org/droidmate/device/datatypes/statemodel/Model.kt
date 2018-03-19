package org.droidmate.device.datatypes.statemodel

import kotlinx.coroutines.experimental.*
import org.droidmate.device.datatypes.Widget
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.imageio.ImageIO
import kotlin.collections.HashSet
import kotlin.streams.toList

internal operator fun UUID.plus(uuid: UUID): UUID {
	return UUID(this.mostSignificantBits+uuid.mostSignificantBits,this.leastSignificantBits+uuid.mostSignificantBits)
}
internal inline fun <T> P_processLines(file:File, sep:String, skip:Long=1, crossinline lineProcessor:(List<String>)-> Deferred<T>):List<Deferred<T>>{
	file.bufferedReader().lines().skip(skip).use { it.toList().let{ br ->
		// skip the first line (headline)
		assert(br.count() > 0, { "ERROR on model loading: file ${file.name} does not contain any entries" })
		return br.map { line -> lineProcessor(line.split(sep).map { it.trim() }) }
	}}
}

/** s_* should be only used in sequential context as it currently does not handle parallelism*/
@Suppress("unused", "MemberVisibilityCanBePrivate")
class Model private constructor(val config: ModelDumpConfig){
	private val states = HashSet<StateData>()
	private val widgets = HashSet<Widget>()
	private val paths = HashSet<Trace>()

	fun getStates():Set<StateData> = states // return a view to the data
	fun addState(s: StateData) = synchronized(states){
		states.find { x->x.stateId==s.stateId } ?:states.add(s)}
	fun getState(id:ConcreteId) = states.find { it.stateId == id }

	fun getWidgets():Set<Widget> = widgets
	fun addWidget(w:Widget) = synchronized(widgets){
		widgets.find { it.id==w.id } ?: widgets.add(w) }

	fun getPaths():Set<Trace> = paths
	fun addTrace(t:Trace) = paths.add(t)

	fun S_findWidget(predicate:(Widget)->Boolean) = widgets.find(predicate)
	val findWidget:(uuid:String,c:Collection<Widget>)->Widget?= { id, c ->
		findWidgetOrElse(id,c){ throw RuntimeException("ERROR on state parsing, the target widget $id was not instantiated correctly")}
	}
	inline fun findWidgetOrElse(uuid:String, widgets:Collection<Widget> = getWidgets(), crossinline otherwise:(UUID)->Widget):Widget? {
		return if (uuid == "null") null
		else  UUID.fromString(uuid).let{ synchronized(widgets){ widgets.find { w -> w.uid == it } ?: otherwise(it) }}
	}
	private suspend fun P_findOrAddState(stateId:ConcreteId): StateData = states.find { s->s.stateId==stateId }  // allow parsing in parallel but ensure atomic adding
			?:  P_parseState(stateId).also{ addState(it) }

	fun P_dumpModel(config: ModelDumpConfig) = launch(CoroutineName("Model-dump")){
		paths.map { t -> launch(CoroutineName("trace-dump")){ t.dump(config) } }.let { traceDump ->
			states.map { s -> launch(CoroutineName("state-dump ${s.uid}")) { s.dump(config) } }.forEach { it.join() }
			traceDump.forEach { it.join() }
		}
	}

	/** update the model with any [action] executed as part of an execution [trace] **/
	fun S_updateModel(action:ActionResult,trace:Trace){
		computeNewState(action,trace.interactedEditFields()).also { newState ->
			launch { newState.dump(config) }
			trace.update(action,newState)
			launch { trace.dump(config)}
			trace.last()!!.screenshot.let{ launch { // if there is any screen-shot copy it to the state extraction directory
				java.nio.file.Paths.get(it)?.toFile()?.copyTo(java.io.File(config.statePath(newState.stateId,"_${newState.configId}${timestamp()}","png") )) }}

			widgets.addAll(newState.widgets)
			states.add(newState)
		}
	}

	private fun computeNewState(action:ActionResult, interactedEF: Map<UUID, List<Pair<StateData, Widget>>>):StateData{
		action.getWidgets(config).let { // compute all widgets existing in the current state
			action.resultState(it).let { state -> // revise state if it contains previously interacted edit fields
				if( state.hasEdit) interactedEF[state.iEditId]?.let{ return action.resultState(handleEditFields(state,it)) }
				return state
			}
		}
	}

	/** check for all edit fields of the state if we already interacted with them and thus potentially changed their text property, if so overwrite the uid to the original one (before we interacted with it) */
	private fun handleEditFields(state: StateData, interactedEF: List<Pair<StateData, Widget>>):List<Widget> {
		// different states may coincidentally have the same iEditId => grouping and check which (if any) is the same conceptional state as [state]
		return interactedEF.groupBy { it.first }.map { (s,pairs) ->
			pairs.map { it.second }.let { widgets -> s.idWhenIgnoring(widgets) to widgets	}
		}.fold(state.widgets, { res, (iUid,widgets) ->  // determine which candidate matches the current [state] and replace the respective widget.uid`s
			if(state.idWhenIgnoring(widgets)==iUid && widgets.all { candidate -> state.widgets.any { it.xpath == candidate.xpath } })
				return state.widgets.map { w -> w.apply { uid =	widgets.find { it.uid ==w.uid }?.uid ?: w.uid } } // replace with initial uid
			else res // contain different elements => wrong state candidate
		})
	}

	private val _actionParser:(List<String>)->Deferred<ActionData> = { entries -> async(CoroutineName("ActionParsing-$entries")){ // we createFromString the source state and target widget if there is any
		stateIdFromString(entries[0]).let { srcId ->  // pars the src state with the contained widgets and add the respective objects to our model
			ActionData.createFromString(entries, findWidget(entries[ActionData.widgetIdx], P_findOrAddState(srcId).widgets))
		}
	}}
	private suspend fun P_parseTrace(file:Path){
		Trace().apply {
			P_processLines(file.toFile(), sep, lineProcessor = _actionParser).forEach{ it.await().let{ addAction(it) } }
		}.also{ synchronized(paths){ paths.add(it)} }
	}

	private val _widgetParser:(List<String>)->Deferred<Widget?> = { line -> line[Widget.idIdx].let{ id ->
		async(CoroutineName("WidgetParsing-$id")) {
			findWidgetOrElse(id) { id -> Widget.fromString(line).also {
				addWidget(it) }.also { assert(id==it.uid, {
				"ERROR on widget parsing inconsistent UUID created ${it.uid} instead of $id"}) } }
		}
	}}
	private suspend fun P_parseState(stateId: ConcreteId): StateData {
		return mutableSetOf<Widget>().apply {  // create the set of contained elements (widgets)
			val contentFile = File(config.widgetFile(stateId))
			if(contentFile.exists())  // otherwise this state has no widgets
				P_processLines(file = contentFile, sep = sep, lineProcessor = _widgetParser).forEach {
					it.await()?.also {  // add the parsed widget to temporary set AND initialize the parent property
						add(it)
						if(it.parentXpath != "") it.parentId = widgets.find{ w-> w.xpath == it.parentXpath }?.id
					}
				}
		}.let {
			if(it.isNotEmpty())
				StateData.fromFile(it)
			else StateData.emptyState()
		}
				.also {assert(stateId ==it.stateId, {"ERROR on state parsing inconsistent UUID created ${it.uid} instead of $stateId"}) }
	}

	companion object {
		@JvmStatic fun emptyModel(config: ModelDumpConfig): Model = Model(config)

		// parallel parsing of multiple traces => need to lock states/widgets and paths when modifying them
		internal fun loadAppModel(appName:String): Model {
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