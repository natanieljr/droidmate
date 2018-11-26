package org.droidmate.explorationModel.retention.loading

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.retention.StringCreator
import org.droidmate.explorationModel.retention.StringCreator.annotatedProperties
import org.droidmate.explorationModel.retention.WidgetProperty
import org.droidmate.explorationModel.retention.getValue
import kotlin.collections.HashMap

internal abstract class WidgetParserI<T>: ParserI<Pair<ConcreteId,T>, UiElementPropertiesI> {
	var indicesComputed: AtomicBoolean = AtomicBoolean(false)
	/** temporary map of all processed widgets for state parsing */
	abstract val queue: MutableMap<ConcreteId, T>
	private var customWidgetIndices: Map<WidgetProperty, Int> = StringCreator.defaultMap
	private val lock = Mutex()  // to guard the indices setter

    override val logger: Logger = LoggerFactory.getLogger(javaClass)


    suspend fun setCustomWidgetIndices(m: Map<WidgetProperty, Int>){
		lock.withLock { customWidgetIndices = m }
	}

	/**
	 * when compatibility mode is enabled this list will contain the mapping oldId->newlyComputedId
	 * to transform the model to the current (newer) id computation.
	 * This mapping is supposed to be used to adapt the action targets in the trace parser (Interaction entries)
	 */
	private val idMapping: ConcurrentHashMap<ConcreteId, ConcreteId> = ConcurrentHashMap()

	protected fun computeWidget(line: List<String>, id: ConcreteId): Pair<ConcreteId,UiElementPropertiesI> {
		log("compute widget $id")
		return id to StringCreator.parseWidgetPropertyString(line, customWidgetIndices)
	}

	@Suppress("FunctionName")
	abstract fun P_S_process(s: List<String>, id: ConcreteId, scope: CoroutineScope): Pair<ConcreteId,T>

	private fun parseWidget(line: List<String>, scope: CoroutineScope): Pair<ConcreteId,T> {
		log("parse widget $line")
		val idProperty = StringCreator.widgetProperties.find { it.property == Widget::id }
		check(idProperty != null)
		val id = idProperty.parseValue(line, customWidgetIndices).getValue() as ConcreteId

		return id to queue.computeIfAbsent(id){
			log("parse absent widget properties for $id")
			P_S_process(line,id, scope).second
		}
	}
	override val processor: suspend (s: List<String>, scope: CoroutineScope) -> Pair<ConcreteId, T> = { s,cs -> parseWidget(s,cs) }

	fun fixedWidgetId(idString: String) = ConcreteId.fromString(idString)?.let{	idMapping[it] ?: it }
	fun fixedWidgetId(idString:ConcreteId?) = idString?.let{	idMapping[it] ?: it }
	fun addFixedWidgetId(oldId: ConcreteId, newId: ConcreteId) { idMapping[oldId] = newId }

	companion object {

		/**
		 * this function can be used to automatically adapt the property indicies in the persistated file
		 * if header.size contains not all persistent entries the respective entries cannot be set in the created Widget.
		 * Optionally a map of oldName->newName can be given to automatically infere renamed header entries
		 */
		@JvmStatic fun computeWidgetIndices(header: List<String>, renamed: Map<String,String> = emptyMap()): Map<WidgetProperty, Int>{
			if(header.size!= StringCreator.annotatedProperties.count()){
				val missing = StringCreator.annotatedProperties.filter { !header.contains(it.annotation.header) && !renamed.containsValue(it.annotation.header) }
				println("WARN the given Widget File does not specify all available properties," +
						"this may lead to different Widget properties and may require to be parsed in compatibility mode\n missing entries: ${missing.toList()}")
			}
			val mapping = HashMap<WidgetProperty, Int>()
			header.forEachIndexed { index, s ->
				val key = renamed[s] ?: s
				annotatedProperties.find { it.annotation.header == key }?.let{  // if the entry is no longer in P we simply ignore it
					mapping[it] = index
					true  // need to return something such that ?: print is not called
				} ?: println("WARN entry '$key' is no longer contained in the widget properties")
			}
			return mapping
		}
	}
}

internal class WidgetParserS(override val model: Model,
                             override val compatibilityMode: Boolean = false,
                             override val enableChecks: Boolean = true): WidgetParserI<UiElementPropertiesI>(){

	override fun P_S_process(s: List<String>, id: ConcreteId, scope: CoroutineScope): Pair<ConcreteId,UiElementPropertiesI> = runBlocking{ computeWidget(s,id) }

	override suspend fun getElem(e: Pair<ConcreteId, UiElementPropertiesI>): UiElementPropertiesI = e.second

	override val queue: MutableMap<ConcreteId, UiElementPropertiesI> = HashMap()
}

internal class WidgetParserP(override val model: Model,
                             override val compatibilityMode: Boolean = false,
                             override val enableChecks: Boolean = true): WidgetParserI<Deferred<UiElementPropertiesI>>(){


	override fun P_S_process(s: List<String>, id: ConcreteId, scope: CoroutineScope): Pair<ConcreteId,Deferred<UiElementPropertiesI>>
			= id to scope.async(CoroutineName("parseWidget $id")){
		computeWidget(s,id).second
	}

	override suspend fun getElem(e: Pair<ConcreteId,Deferred<UiElementPropertiesI>>): UiElementPropertiesI = e.second.await()

	override val queue: MutableMap<ConcreteId, Deferred<UiElementPropertiesI>> = ConcurrentHashMap()
}
