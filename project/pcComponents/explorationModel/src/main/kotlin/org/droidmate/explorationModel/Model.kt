// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.explorationModel

import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.config.ConfigProperties
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.*
import org.droidmate.explorationModel.retention.loading.ModelParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis


/**
 * we implement CoroutineScope this allows us to implicitly wait for all child jobs (launch/async) started in this scope
 * any direct call of launch/async is in this scope and will be waited for at the end of this lifecycle (end of exploration)
 * meanwhile we use currentScope or coroutineScope/supervisorScope to directly wait for child jobs before returning from a function call
 */
open class Model private constructor(val config: ModelConfig): CoroutineScope {

	private val paths = LinkedList<ExplorationTrace>()
	/** non-mutable view of all traces contained within this model */
	fun getPaths(): List<ExplorationTrace> = paths

/**---------------------------------- public interface --------------------------------------------------------------**/
	open fun initNewTrace(watcher: LinkedList<ModelFeatureI>,id: UUID = UUID.randomUUID()): ExplorationTrace {
	return ExplorationTrace(watcher, config, id).also { actionTrace ->
			paths.add(actionTrace)
		}
	}

	// we use supervisorScope for the dumping, such that cancellation and exceptions are only propagated downwards
	// meaning if a dump process fails the overall model process is not affected
	open suspend fun dumpModel(config: ModelConfig): Job = this.launch(CoroutineName("Model-dump")+backgroundJob){
		getStates().let { states ->
			debugOut("dump Model with ${states.size}")
			states.forEach { s -> launch(CoroutineName("state-dump ${s.uid}")) { s.dump(config) } }
		}
		paths.forEach { t -> launch(CoroutineName("trace-dump")) { t.dump(config) } }
	}

	private var uTime: Long = 0
	/** update the model with any [action] executed as part of an execution [trace] **/
	suspend fun updateModel(action: ActionResult, trace: ExplorationTrace) {
		measureTimeMillis {
			storeScreenShot(action)
			val widgets = generateWidgets(action, trace).also{ addWidgets(it) }
			val newState = generateState(action, widgets).also{ addState(it) }
			trace.update(action, newState)

			if (config[ConfigProperties.ModelProperties.dump.onEachAction]) {
				this.launch(CoroutineName("state-dump")) { newState.dump(config) }  //TODO the launch may be on state/trace object instead
				this.launch(CoroutineName("trace-dump")) { trace.dump(config) }
			}
		}.let {
			debugOut("model update took $it millis")
			uTime += it
			debugOut("---------- average model update time ${uTime / trace.size} ms overall ${uTime / 1000.0} seconds --------------")
		}
	}

	/**--------------------------------- concurrency utils ------------------------------------------------------------**/
	//we need + Job()  to be able to CancelAndJoin this context, otherwise we can ONLY cancel this scope or its children
	override val coroutineContext: CoroutineContext = CoroutineName("ModelScope")+Job() //we do not define a dispatcher, this means Dispatchers.Default is automatically used (a pool of worker threads)

	/** this job can be used for any coroutine context which is not essential for the main model process.
	 * In particular we use it to invoke background processes for model or img dump
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected val backgroundJob = SupervisorJob()
	/** This will notify all children that this scope is to be canceled (which is an cooperative mechanism, mechanism all non-terminating spawned children have to check this flag).
	 * Moreover, this will propagate the cancellation to our model-actors and join all structural child coroutines of this scope.
	 */
	suspend fun cancelAndJoin() = coroutineContext[Job]!!.cancelAndJoin()

	private val states = CollectionActor(HashSet<State>(), "StateActor").create()
	/** @return a view to the data (suspending function) */
	suspend fun getStates(): Set<State> = states.getAll()

	/** should be used only by model loader/parser */
	internal suspend fun addState(s: State){
		nStates +=1
		states.send(Add(s))
	}

	suspend fun getState(id: ConcreteId): State?{
		val states = getStates()
		return states.find { it.stateId == id }
	}

	private val widgets = CollectionActor(HashSet<Widget>(), "WidgetActor").create()

	suspend fun getWidgets(): Set<Widget>{  //TODO instead we could have the list of seen interactive widgets here (potentially with the count of interactions)
		return CompletableDeferred<Collection<Widget>>().let{ response ->
			widgets.send(GetAll(response))
			response.await() as Set
		}
	}

	/** adding a value to the actor is non blocking and should not take much time */
	internal suspend fun addWidgets(w: Collection<Widget>) {
		nWidgets += w.size
		widgets.send(AddAll(w))
	}

	/** -------------------------------------- protected generator methods --------------------------------------------**/

	/** used on model update to instantiate a new state for the current UI screen */
	protected open fun generateState(action: ActionResult, widgets: Collection<Widget>): State =
			with(action.guiSnapshot) { State(widgets, isHomeScreen) }

	/** used by ModelParser to create [State] object from persisted data */
	internal open fun parseState(widgets: Collection<Widget>, isHomeScreen: Boolean): State =
			State(widgets, isHomeScreen)

	private fun generateWidgets(action: ActionResult, @Suppress("UNUSED_PARAMETER") trace: ExplorationTrace): Collection<Widget>{
		val elements: Map<Int, UiElementPropertiesI> = action.guiSnapshot.widgets.associateBy { it.idHash }
		return generateWidgets(elements)
	}

	/** used on model update to compute the list of UI elements contained in the current UI screen ([State]).
	 *  used by ModelParser to create [Widget] object from persisted data
	 */
	internal open fun generateWidgets(elements: Map<Int, UiElementPropertiesI>): Collection<Widget>{
		val widgets = HashMap<Int,Widget>()
		val workQueue = LinkedList<UiElementPropertiesI>().apply {
			addAll(elements.values.filter { it.parentHash == 0 })  // add all roots to the work queue
		}
		check(elements.isEmpty() || workQueue.isNotEmpty()){"ERROR we don't have any roots something went wrong on UiExtraction"}
		while (workQueue.isNotEmpty()){
			with(workQueue.pollFirst()){
				val parent = if(parentHash != 0) widgets[parentHash]!!.id else null
				widgets[idHash] = Widget(this, parent)
				childHashes.forEach {
//					check(elements[it]!=null){"ERROR no element with hashId $it in working queue"}
					if(elements[it] == null)
						logger.warn("could not find child with id $it of widget $this ")
					else workQueue.add(elements[it]!!) } //FIXME if null we can try to find element.parentId = this.idHash !IN workQueue as repair function, but why does it happen at all
			}
		}
		check(widgets.size==elements.size){"ERROR not all UiElements were generated correctly in the model ${elements.filter { !widgets.containsKey(it.key) }.values}"}
		assert(elements.all { e -> widgets.values.any { it.idHash == e.value.idHash } }){ "ERROR not all UiElements were generated correctly in the model ${elements.filter { !widgets.containsKey(it.key) }}" }
		return widgets.values
	}

	/**---------------------------------------- private methods -------------------------------------------------------**/
	private fun storeScreenShot(action: ActionResult) = this.launch(CoroutineName("screenShot-dump")+backgroundJob){
		if(action.screenshot.isNotEmpty())
			Files.write(config.imgDst.resolve("${action.action.id}.jpg"), action.screenshot
					, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
	}

	companion object {
        val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }
		@JvmStatic
		fun emptyModel(config: ModelConfig): Model = Model(config).apply { runBlocking { addState(State.emptyState) }}

		/**
		 * use this method to load a specific app model from its dumped data
		 *
		 * example:
		 * val test = loadAppModel("ch.bailu.aat")
		 * runBlocking { println("$test #widgets=${test.getWidgets().size} #states=${test.getStates().size} #paths=${test.getPaths().size}") }
		 */
		@Suppress("unused")
		@JvmStatic fun loadAppModel(appName: String, watcher: LinkedList<ModelFeatureI> = LinkedList())
				= runBlocking { ModelParser.loadModel(ModelConfig(appName = appName, isLoadC = true), watcher) }

		/** debug method **/
		@JvmStatic
		fun main(args: Array<String>) {

			println("runBlocking: ${Thread.currentThread()}")

			val t = Model.emptyModel(ModelConfig("someApp"))
			t.launch {
				println("ModelScope.launch: ${Thread.currentThread()}")
			}
			t.coroutineContext.cancel()
			val active = t.isActive
			println(active)

//			val test = ModelParser.loadModel(ModelConfig(path = Paths.get("src/main", "out", "playback"), appName = "testModel", isLoadC = true))//loadAppModel("loadTest")
//			runBlocking { println("$test #widgets=${test.getWidgets().size} #states=${test.getStates().size} #paths=${test.getPaths().size}") }
//			test.getPaths().first().getActions().forEach { a ->
//				println("ACTION: " + a.actionString())
//			}
		}

	} /** end COMPANION **/

	/*********** debugging parameters *********************************/
	/** debugging counter do not use it in productive code, instead access the respective element set */
	private var nWidgets = 0
	/** debugging counter do not use it in productive code, instead access the respective element set */
	private var nStates = 0

	/**
	 * this only shows how often the addState or addWidget function was called, but if identical id's were added multiple
	 * times the real set will contain less elements then these counter indicate
	 */
	override fun toString(): String {
		return "Model[#addState=$nStates, #addWidget=$nWidgets, paths=${paths.size}]"
	}
}
