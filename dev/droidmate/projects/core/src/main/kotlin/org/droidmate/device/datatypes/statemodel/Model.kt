package org.droidmate.device.datatypes.statemodel

import kotlinx.coroutines.experimental.*
import org.droidmate.device.datatypes.Widget
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
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

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Model private constructor(val config: ModelDumpConfig){
	private val states = HashSet<StateData>()
	private val widgets = HashSet<Widget>()
	private val paths = HashSet<Trace>()

	fun getStates():Set<StateData> = states // return a view to the data
	fun addState(s: StateData) = synchronized(states){
		states.find { x->x.uid==s.uid } ?:states.add(s)}
	fun getState(id:StateId) = states.find { it.stateId == id }

	fun getWidgets():Set<Widget> = widgets
	fun addWidget(w:Widget) = synchronized(widgets){
		widgets.find { it.uid==w.uid } ?: widgets.add(w) }

	fun getPaths():Set<Trace> = paths
	fun addTrace(t:Trace) = paths.add(t)

	/** s_ should be only used in sequential context */
	fun S_findWidget(predicate:(Widget)->Boolean) = widgets.find(predicate)
	val findWidget:(uuid:String,c:Collection<Widget>)->Widget?= { id, c ->
		findWidgetOrElse(id,c){ throw RuntimeException("ERROR on state parsing, the target widget $id was not instantiated correctly")}
	}
	inline fun findWidgetOrElse(uuid:String, widgets:Collection<Widget> = getWidgets(), crossinline otherwise:(UUID)->Widget):Widget? {
		return if (uuid == "null") null
		else  UUID.fromString(uuid).let{ synchronized(widgets){ widgets.find { w -> w.uid == it } ?: otherwise(it) }}
	}
	private suspend fun P_findOrAddState(uuid:UUID): StateData = states.find { s->s.uid==uuid }  // allow parsing in parallel but ensure atomic adding
			?:  P_parseState(uuid).also{ addState(it) }

	fun P_dumpModel(config: ModelDumpConfig) = launch(CoroutineName("Model-dump")){
		paths.map { t -> launch(CoroutineName("trace-dump")){ t.dump(config) } }.let { traceDump ->
			states.map { s -> launch(CoroutineName("state-dump ${s.uid}")) { s.dump(config) } }.forEach { it.join() }
			traceDump.forEach { it.join() }
		}
	}

	private val _actionParser:(List<String>)->Deferred<ActionData> = { entries -> async(CoroutineName("ActionParsing-$entries")){ // we createFromString the source state and target widget if there is any
		UUID.fromString(entries[0]).let { srcId ->  // pars the src state with the contained widgets and add the respective objects to our model
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
	private suspend fun P_parseState(uuid: UUID): StateData {
		return mutableSetOf<Widget>().apply {  // create the set of contained elements (widgets)
			val contentFile = File(config.widgetFile(uuid))
			if(contentFile.exists())  // otherwise this state has no widgets
				P_processLines(file = contentFile, sep = sep, lineProcessor = _widgetParser).forEach {
					it.await()?.also {  // add the parsed widget to temporary set AND initialize the parent property
						add(it)
						it.properties.parent?.let{ parent -> it.parentId = widgets.find{ w-> w.xpath == parent.xpath }?.id }
					}
				}
		}.let {
			if(it.isNotEmpty())
				StateData.fromFile(it, File(config.statePath(uuid)))
			else StateData.emptyState()
		}
				.also {assert(uuid ==it.uid, {"ERROR on state parsing inconsistent UUID created ${it.uid} instead of $uuid"}) }
	}

	companion object {
		fun emptyModel(config: ModelDumpConfig): Model = Model(config)

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