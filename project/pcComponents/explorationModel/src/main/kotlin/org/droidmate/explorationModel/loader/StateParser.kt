package org.droidmate.explorationModel.loader

import kotlinx.coroutines.experimental.*
import org.droidmate.deviceInterface.exploration.isClick
import org.droidmate.deviceInterface.exploration.isLongClick
import org.droidmate.deviceInterface.exploration.isTextInsert
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.ConcreteId
import org.droidmate.explorationModel.config.idFromString
import org.droidmate.explorationModel.loader.WidgetParserI.Companion.computeWidgetIndicies
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal abstract class StateParserI<T,W>: ParserI<T, StateData> {
	abstract val widgetParser: WidgetParserI<W>
	abstract val reader: ContentReader

  override val logger: Logger = LoggerFactory.getLogger(javaClass)

	/** temporary map of all processed widgets for state parsing */
	abstract val queue: MutableMap<ConcreteId, T>
	/**
	 * when compatibility mode is enabled this list will contain the mapping oldId->newlyComputedId
	 * to transform the model to the current (newer) id computation.
	 * This mapping is supposed to be used to adapt the action targets in the trace parser (ActionData entries)
	 */
	private val idMapping: ConcurrentHashMap<ConcreteId, ConcreteId> = ConcurrentHashMap()
//	override fun logcat(msg: String) {	}

	/** parse the state either asynchronous (Deferred) or sequential (blocking) */
	abstract fun P_S_process(id: ConcreteId, scope: CoroutineScope): T

	override val processor: suspend (actionData: List<String>) -> T = {  parseState(it) }

	internal val parseIfAbsent: (CoroutineScope)->(ConcreteId)->T =	{
		scope->{ id ->
		log("parse absent state $id")
		P_S_process(id, scope)
	}	}
	private val rightActionType: (Widget,actionType: String)->Boolean = { w,t ->
		w.enabled && when{
			t.isClick() -> w.clickable || w.checked != null
			t.isLongClick() -> w.longClickable
			t.isTextInsert() -> w.isEdit
			else -> false
		}
	}

	/** parse the result state of the given actionData and verify that the targetWidget (if any) is contained in the src state */
	private suspend fun parseState(actionData: List<String>): T{
		val resId = idFromString(actionData[ActionData.resStateIdx])
		log("parse result State: $resId")
		// parse the result state with the contained widgets and queue them to make them available to other coroutines
		val resState = queue.computeIfAbsent(resId, currentScope(parseIfAbsent))

		val targetWidgetId = widgetParser.fixedWidgetId(actionData[ActionData.widgetIdx])	?: return resState
        log("validate for target widget $targetWidgetId")
		val srcId = idFromString(actionData[ActionData.srcStateIdx])
		verify("ERROR could not find target widget $targetWidgetId in source state $srcId", {

            log("wait for srcState $srcId")
				getElem(queue.computeIfAbsent(srcId, currentScope(parseIfAbsent))).widgets.any { it.id == targetWidgetId }
		}){ // repair function
			val actionType = actionData[ActionData.Companion.ActionDataFields.Action.ordinal]
			var srcS = queue[srcId]
			while(srcS == null) { // due to concurrency the value is not yet written to queue -> wait a bit
				delay(1)
				srcS = queue[srcId]
			}
			val possibleTargets = getElem(srcS).widgets.filter {
				it.uid == targetWidgetId.first && it.canBeActedUpon && rightActionType(it,actionType)}
			when(possibleTargets.size){
				0 -> throw IllegalStateException("cannot re-compute targetWidget $targetWidgetId in state $srcId")
				1 -> widgetParser.addFixedWidgetId(targetWidgetId, possibleTargets.first().id)
				else -> {
					println("WARN there are multiple options for the interacted target widget we just chose the first one")
					widgetParser.addFixedWidgetId(targetWidgetId, possibleTargets.first().id)
				}
			}
		}
		return resState
	}
	protected suspend fun computeState(stateId: ConcreteId): StateData{
        log("\ncompute state $stateId")
		val(contentPath,isHomeScreen,topPackage) = reader.getStateFile(stateId)
		if(!widgetParser.indiciesComputed.get()) {
			widgetParser.setCustomWidgetIndicies( computeWidgetIndicies(reader.getHeader(contentPath)) )
			widgetParser.indiciesComputed.set(true)
		}
		val widgets = reader.processLines(path = contentPath, lineProcessor = widgetParser.processor)
				.map{ widgetParser.getElem(it) }
		computeActableDescendent(widgets) // required to correctly compute if this state is to be used for the StateId or not

		val widgetSet = if(enableChecks) mutableSetOf<Widget>().apply {	// create the set of contained elements (widgets)
			log(" parse file ${contentPath.toUri()} (${widgets.size} widgets)")

			widgets.forEach { w -> // add the parsed widget to temporary set AND initialize the parent property
				add(Widget(w.properties.copy().apply{ // TODO these values should go into constructor as soon as uid computation is adapted to use annotated values only
					xpath = w.xpath
					idHash = w.idHash
					uncoveredCoord = w.uncoveredCoord
					hasActableDescendant = w.hasActableDescendant
				}, w.uidImgId).apply {
					parentId = w.parentId
				}) //!!! Widget.copy does not yield a new reference for WidgetData !
			}
		} else widgets

		var ns: StateData
		return if (widgetSet.isNotEmpty()) {
			StateData.fromFile(widgetSet, homeScreen = isHomeScreen, topPackage = topPackage).also { newState ->
				ns = newState

				verify("ERROR different set of widgets used for UID computation used", {
					val correctId = stateId == newState.stateId
					if (!correctId)
						logger.warn("ERROR on state parsing inconsistent UUID created ${newState.stateId} instead of $stateId")
					val lS = widgets.filter { it.usedForStateId }
					if (lS.isNotEmpty()) {
						val nS = newState.widgets.filter {
							newState.isRelevantForId(it)   // IMPORTANT: use this call instead of accessing usedForState property because the later is only initialized after the pId is accessed
						}
						val uidC = nS.containsAll(lS) && lS.containsAll(nS)
						val nOnly = nS.minus(lS)
						val lOnly = lS.minus(nS)
						if (!uidC){
							logger.warn("ERROR different set of widgets used for UID computation used \n ${nOnly.map { it.id }}\n instead of \n ${lOnly.map { it.id }}")
							ns = StateData.fromFile(widgetSet, newState.isHomeScreen, newState.topNodePackageName)
						}
						uidC && correctId
					} else correctId
				}) {
					idMapping[stateId] = ns.stateId
				}
				model.addState(ns)
			}
		} else StateData.emptyState
	}

	private fun computeActableDescendent(widgets: Collection<Widget>){
		val toProcess: LinkedList<ConcreteId> = LinkedList()
		widgets.filter { it.properties.actable && it.parentId != null }.forEach {
			toProcess.add(it.parentId!!) }
		var i=0
		while (i<toProcess.size){
			val n = toProcess[i++]
			widgets.find { it.id == n }?.run {
				this.properties.hasActableDescendant = true
				if(parentId != null && !toProcess.contains(parentId!!)) toProcess.add(parentId!!)
			}
		}
	}

	fun fixedStateId(idString: String) = idFromString(idString).let{	idMapping[it] ?: it }
}

internal class StateParserS(override val widgetParser: WidgetParserS,
                            override val reader: ContentReader,
                            override val model: Model,
                            override val parentJob: Job? = null,
                            override val compatibilityMode: Boolean,
                            override val enableChecks: Boolean) : StateParserI<StateData, Widget>(){
	override val queue: MutableMap<ConcreteId, StateData> = HashMap()

	override fun P_S_process(id: ConcreteId, scope: CoroutineScope): StateData = runBlocking(scope.coroutineContext +CoroutineName("blocking compute State $id")) { computeState(id) }

	override suspend fun getElem(e: StateData): StateData = e
}

internal class StateParserP(override val widgetParser: WidgetParserP,
                            override val reader: ContentReader,
                            override val model: Model,
                            override val parentJob: Job? = null,
                            override val compatibilityMode: Boolean,
                            override val enableChecks: Boolean) : StateParserI<Deferred<StateData>, Deferred<Widget>>(){
	override val queue: MutableMap<ConcreteId, Deferred<StateData>> = ConcurrentHashMap()

	override fun P_S_process(id: ConcreteId, scope: CoroutineScope): Deferred<StateData> =	scope.async(CoroutineName("parseWidget $id")){
        log("parallel compute state $id")
		computeState(id)
	}

	override suspend fun getElem(e: Deferred<StateData>): StateData =
			e.await()
}