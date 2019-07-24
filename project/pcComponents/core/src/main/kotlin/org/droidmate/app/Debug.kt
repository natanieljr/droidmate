package org.droidmate.app

import com.natpryce.konfig.Configuration
import kotlinx.coroutines.runBlocking
import org.droidmate.api.ExplorationAPI
import org.droidmate.command.ExploreCommandBuilder
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.isFetch
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.actions.terminateApp
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.manual.Logging
import org.droidmate.exploration.strategy.manual.ManualExploration
import org.droidmate.exploration.strategy.manual.getLogger
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.DefaultModelProvider
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.misc.FailableExploration


@Suppress("SameParameterValue")
object Debug : Logging {
	override val log by lazy { getLogger() }

	@JvmStatic
	fun main(args: Array<String>) = runBlocking{

		manualExploration(ExplorationAPI.config(args))

	}

	private suspend fun manualExploration(cfg: ConfigurationWrapper){
		val builder = ExploreCommandBuilder(
			strategies = defaultStrategies(cfg).plus(
				ManualExploration<Int>(resetOnStart = !cfg[org.droidmate.explorationModel.config.ConfigProperties.Output.debugMode])
			).toMutableList(),
			watcher = mutableListOf(),
			selectors = mutableListOf()
		)
		ExplorationAPI.explore(
			cfg = cfg,
			commandBuilder = builder,
			modelProvider = DefaultModelProvider()
		).logResult()
	}

	private val defaultStrategies: (cfg: Configuration) -> MutableCollection<AExplorationStrategy> = { cfg ->
		mutableListOf(
			// timeLimit*60*1000 such that we can specify the limit in minutes instead of milliseconds
			DefaultStrategies.timeBasedTerminate(0, cfg[ConfigProperties.Selectors.timeLimit] * 60 * 1000),
			isStuck(1),
			DefaultStrategies.allowPermission(2),
			leftApp(3)
		)
	}

	private fun isStuck(prio: Int) = object : AExplorationStrategy(){

		override fun getPriority(): Int = prio
		private var lastStuck = -1
		var nextAction: ExplorationAction? = null

		override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
		val reset by lazy{ eContext.resetApp() }
		val lastActions by lazy{ eContext.explorationTrace.getActions().takeLast(20) }
		nextAction = when{
			eContext.isEmpty() -> {
				lastStuck = -1  // reset counter on each new exploration start
				null
			}
			eContext.explorationTrace.size>20
					// we are always within the very same states => probably stuck and reset the app
					&& lastActions.indexOfLast { it.actionType.isPressBack() } < lastActions.size-10 && // no recent goBack
					lastActions.indexOfLast { it.actionType.isLaunchApp() } < lastActions.size-10 && // no recent reset
					lastActions.map { it.resState }.groupBy { it.uid }.size < 4 -> {  // not enough different states seen
				if( eContext.explorationTrace.size - lastStuck < 20){
					log.warn(" We got stuck repeatedly within the last 20 actions! Check the app ${eContext.apk.packageName} for feasibility")
					reset
//					Terminate // TODO this leaded to early terminates even though 'unexplored' elements still existed
				} else {
					lastStuck = eContext.explorationTrace.size
					reset
				}
			}
			else ->null
		}
			return nextAction != null
		}

		override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(
			eContext: ExplorationContext<M, S, W>
		): ExplorationAction =
			nextAction!!
	}

	private fun leftApp(prio: Int) = object : AExplorationStrategy(){
		override fun getPriority(): Int = prio
		var nextAction: ExplorationAction? = null

		override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
			val currentState = eContext.getCurrentState()
			val actions by lazy { eContext.explorationTrace.getActions() }
			val reset by lazy { eContext.resetApp() }
			nextAction = when {
				eContext.isEmpty() || (actions.size == 1 && actions.first().actionType.isFetch()) -> { null /* NoOp */ }
				currentState.isAppHasStoppedDialogBox -> {
					reset
				}
				!eContext.belongsToApp(currentState) ->  // cannot check a single root-node since the first one may be some assistance window e.g. keyboard
					if (actions.takeLast(3).any { it.actionType.isPressBack() }) {
						reset
					} else { // the state does no longer belong to the AUT (happens by clicking browser links or advertisement)
						ExplorationAction.closeAndReturn()
					}
				currentState.isHomeScreen -> {
					val recentRestart = actions.takeLast(10).count { it.actionType.isLaunchApp() } > 2
					if (recentRestart && actions.size > 20) {
						log.error("Cannot start app: we are late in the exploration and already reset at least twice in the last actions.")
						ExplorationAction.terminateApp()
					} // we are late in the exploration and already reset at least twice in the last actions
					else reset
				}
				else -> null
			}
			return nextAction != null
		}

		override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(
			eContext: ExplorationContext<M, S, W>
		): ExplorationAction =
			nextAction!!

	}

	private fun Map<Apk, FailableExploration>.logResult()= forEach { apk, (eContext, errors) ->
		log.info("exploration of {} ended with {} errors; model: {}",	apk.packageName, errors.size, eContext?.model)
		errors.forEach { e ->
			log.error("exception while exploring ${apk.path}", e)
		}
	}
}