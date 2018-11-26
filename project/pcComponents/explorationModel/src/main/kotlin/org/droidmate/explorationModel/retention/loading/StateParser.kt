package org.droidmate.explorationModel.retention.loading

import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.deviceInterface.exploration.isClick
import org.droidmate.deviceInterface.exploration.isLongClick
import org.droidmate.deviceInterface.exploration.isTextInsert
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.retention.loading.WidgetParserI.Companion.computeWidgetIndices
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal abstract class StateParserI<T,W>: ParserI<T, State>{
	var headerRenaming: Map<String,String> = emptyMap()
	abstract val widgetParser: WidgetParserI<W>
	abstract val reader: ContentReader

  override val logger: Logger = LoggerFactory.getLogger(javaClass)

	/** temporary map of all processed widgets for state parsing */
	abstract val queue: MutableMap<ConcreteId, T>
	/**
	 * when compatibility mode is enabled this list will contain the mapping oldId->newlyComputedId
	 * to transform the model to the current (newer) id computation.
	 * This mapping is supposed to be used to adapt the action targets in the trace parser (Interaction entries)
	 */
	private val idMapping: ConcurrentHashMap<ConcreteId, ConcreteId> = ConcurrentHashMap()
//	override fun logcat(msg: String) {	}

	/** parse the state either asynchronous (Deferred) or sequential (blocking) */
	@Suppress("FunctionName")
	abstract fun P_S_process(id: ConcreteId, coroutineContext: CoroutineContext): T

	override val processor: suspend (s: List<String>, scope: CoroutineScope) -> T = { _,_ -> TODO("not necessary anymore") }

	internal val parseIfAbsent: (CoroutineContext) -> (ConcreteId)->T =	{ context ->{ id ->
		log("parse absent state $id")
		P_S_process(id,context)
	}}

	protected suspend fun computeState(stateId: ConcreteId): State {
		log("\ncompute state $stateId")
		val(contentPath,isHomeScreen) = reader.getStateFile(stateId)
		if(!widgetParser.indicesComputed.get()) {
			widgetParser.setCustomWidgetIndices( computeWidgetIndices(reader.getHeader(contentPath), headerRenaming) )
			widgetParser.indicesComputed.set(true)
		}
		val uiProperties = reader.processLines(path = contentPath, lineProcessor = widgetParser.processor)
				.map { (id,e) -> id to widgetParser.getElem(id to e)	}
		uiProperties.groupBy { it.second.idHash }.forEach {
			if(it.value.size>1){
				//FIXME that may happen for old models and will destroy the parent/child mapping, so for 'old' models we would have to parse the parentId instead
				logger.error("ambiguous idHash elements found, this will result in model inconsistencies (${it.value})")
			}
		}
		debugOut("${uiProperties.map { it.first.toString()+": HashId = ${it.second.idHash}" }}",false)
		val widgets = model.generateWidgets(uiProperties.associate { (_,e) ->  e.idHash to e }).also {
			it.forEach { w -> uiProperties.find { p -> p.second.idHash == w.idHash }!!.let{ (id,_) ->
				verify("ERROR on widget parsing inconsistent ID created ${w.id} instead of $id",{id == w.id}) {
					idMapping[id] = w.id
				}
			}}
		}
		model.addWidgets(widgets)

		return if (widgets.isNotEmpty()) {
			model.parseState(widgets, isHomeScreen).also { newState ->

				verify("ERROR different set of widgets used for UID computation used", {
					val correctId = stateId == newState.stateId
					if (!correctId)
						logger.warn("ERROR on state parsing inconsistent UUID created ${newState.stateId} instead of $stateId")
					val lS = emptyList<Widget>()//widgets.filter { it.usedForStateId }
					if (lS.isNotEmpty()) {
						val nS = newState.widgets.filter {
							newState.isRelevantForId(it)   // IMPORTANT: use this call instead of accessing usedForState property because the later is only initialized after the pId is accessed
						}
						val uidC = nS.containsAll(lS) && lS.containsAll(nS)
						val nOnly = nS.minus(lS)
						val lOnly = lS.minus(nS)
						if (!uidC){
							logger.warn("ERROR different set of widgets used for UID computation used \n ${nOnly.map { it.id }}\n instead of \n ${lOnly.map { it.id }}")
						}
						uidC && correctId
					} else correctId
				}) {
					idMapping[stateId] = newState.stateId
				}
				model.addState(newState)
			}
		} else State.emptyState
	}

	fun fixedStateId(idString: String) = ConcreteId.fromString(idString).let{	idMapping[it] ?: it }

}

internal class StateParserS(override val widgetParser: WidgetParserS,
                            override val reader: ContentReader,
                            override val model: Model,
                            override val compatibilityMode: Boolean,
                            override val enableChecks: Boolean) : StateParserI<State, UiElementPropertiesI>(){
	override val queue: MutableMap<ConcreteId, State> = HashMap()

	override fun P_S_process(id: ConcreteId, coroutineContext: CoroutineContext): State = runBlocking { computeState(id) }

	override suspend fun getElem(e: State): State = e
}

internal class StateParserP(override val widgetParser: WidgetParserP,
                            override val reader: ContentReader,
                            override val model: Model,
                            override val compatibilityMode: Boolean,
                            override val enableChecks: Boolean)
	: StateParserI<Deferred<State>, Deferred<UiElementPropertiesI>>(){
	override val queue: MutableMap<ConcreteId, Deferred<State>> = ConcurrentHashMap()

	override fun P_S_process(id: ConcreteId, coroutineContext: CoroutineContext): Deferred<State> =	CoroutineScope(coroutineContext+Job()).async(CoroutineName("parseWidget $id")){
		log("parallel compute state $id")
		computeState(id)
	}

	override suspend fun getElem(e: Deferred<State>): State =
			e.await()
}