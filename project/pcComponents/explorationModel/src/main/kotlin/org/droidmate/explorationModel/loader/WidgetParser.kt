package org.droidmate.explorationModel.loader

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.NonCancellable.isActive
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import org.droidmate.deviceInterface.guimodel.P
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.ConcreteId
import org.droidmate.explorationModel.config.asConcreteId
import org.droidmate.explorationModel.config.asUUID
import org.droidmate.explorationModel.plus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

internal abstract class WidgetParserI<T>: ParserI<T, Widget> {
	var indiciesComputed: AtomicBoolean = AtomicBoolean(false)
	/** temporary map of all processed widgets for state parsing */
	abstract val queue: MutableMap<Int, T>
	private var customWidgetIndicies: Map<P,Int> = P.defaultIndicies
	private val lock = Mutex()  // to guard the indicy setter

    override val logger: Logger = LoggerFactory.getLogger(javaClass)


    suspend fun setCustomWidgetIndicies(m: Map<P,Int>){
		lock.withLock { customWidgetIndicies = m }
	}

//	override fun logcat(msg: String) {	}
	/**
	 * when compatibility mode is enabled this list will contain the mapping oldId->newlyComputedId
	 * to transform the model to the current (newer) id computation.
	 * This mapping is supposed to be used to adapt the action targets in the trace parser (ActionData entries)
	 */
	private val idMapping: ConcurrentHashMap<ConcreteId, ConcreteId> = ConcurrentHashMap()

	protected suspend fun computeWidget(line: List<String>,id: ConcreteId): Widget {
		log("compute widget $id")
		if(!isActive) return Widget() // if there was already an error the parsing may be canceled -> stop here

		return Widget.fromString(line,customWidgetIndicies).also { widget ->
			verify("ERROR on widget parsing inconsistent ID created ${widget.id} instead of $id",{id == widget.id}){
				idMapping[id] = widget.id //FIXME remove Widget.fromString WidgetData.pId assert
			}
			model.S_addWidget(widget)  // add the widget to the model if it didn't exist yet
		}
	}

	abstract fun P_S_process(s: List<String>, id: ConcreteId, scope: CoroutineScope): T
	private suspend fun parseWidget(line: List<String>): T = currentScope{
		log("parse widget $line")
		val wConfigId = UUID.fromString(line[Widget.idIdx.second]) + line[P.ImgId.idx(customWidgetIndicies)].asUUID()
		val id = Pair((UUID.fromString(line[Widget.idIdx.first])), wConfigId)

		return queue.computeIfAbsent(line.toTypedArray().contentHashCode()){
            log("parse absent widget $id")
			P_S_process(line,id, this)
		}
	}
	override val processor: suspend (List<String>) -> T = { parseWidget(it) }

	fun fixedWidgetId(idString: String) = idString.asConcreteId()?.let{	idMapping[it] ?: it }
	fun addFixedWidgetId(oldId: ConcreteId, newId: ConcreteId) { idMapping[oldId] = newId }

	companion object {

		/**
		 * this function can be used to automatically adapt the property indicies in the persistated file
		 * if header.size contains not all possible entries of [P] the respective entries cannot be set in the created Widget.
		 * Optionally a map of oldName->newName can be given to automatically infere renamed header entries
		 */
		@JvmStatic fun computeWidgetIndicies(header: List<String>, renamed: Map<String,String> = emptyMap()): Map<P,Int>{
			if(header.size!= P.values().size){
				val missing = P.values().filter { !header.contains(it.name) }
				println("WARN the given Widget File does not specify all available properties," +
						"this may lead to different Widget properties and may require to be parsed in compatibility mode\n missing entries: $missing")
			}
			val mapping = HashMap<P,Int>()
			header.forEachIndexed { index, s ->
				val key = renamed[s] ?: s
				P.values().find { it.header == key }?.let{  // if the entry is no longer in P we simply ignore it
					mapping[it] = index
					true  // need to return something such that ?: print is not called
				} ?: println("WARN entry $key is no longer containd in the widget properties")
			}
			return mapping
		}
	}
}

internal class WidgetParserS(override val model: Model, override val parentJob: Job? = null,
                             override val compatibilityMode: Boolean,
                             override val enableChecks: Boolean): WidgetParserI<Widget>(){

	override fun P_S_process(s: List<String>, id: ConcreteId, scope: CoroutineScope): Widget = runBlocking(scope.coroutineContext+CoroutineName("parseWidget $id")) { computeWidget(s,id) }

	override suspend fun getElem(e: Widget): Widget = e

	override val queue: MutableMap<Int, Widget> = HashMap()
}

internal class WidgetParserP(override val model: Model, override val parentJob: Job? = null,
                             override val compatibilityMode: Boolean,
                             override val enableChecks: Boolean): WidgetParserI<Deferred<Widget>>(){

	override fun P_S_process(s: List<String>, id: ConcreteId, scope: CoroutineScope): Deferred<Widget> =
			scope.async(CoroutineName("parseWidget $id")){
		computeWidget(s,id)
	}

	override suspend fun getElem(e: Deferred<Widget>): Widget = e.await()

	override val queue: MutableMap<Int, Deferred<Widget>> = ConcurrentHashMap()
}
