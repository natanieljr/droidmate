package org.droidmate.app

import com.natpryce.konfig.Configuration
import kotlinx.coroutines.runBlocking
import org.droidmate.api.ExplorationAPI
import org.droidmate.command.ExploreCommandBuilder
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.deviceInterface.exploration.isFetch
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.SelectorFunction
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.exploration.strategy.Back
import org.droidmate.exploration.strategy.Reset
import org.droidmate.exploration.strategy.Terminate
import org.droidmate.exploration.strategy.manual.Logging
import org.droidmate.exploration.strategy.manual.ManualExploration
import org.droidmate.exploration.strategy.manual.getLogger
import org.droidmate.exploration.strategy.widget.AllowRuntimePermission
import org.droidmate.explorationModel.factory.DefaultModelProvider
import org.droidmate.misc.FailableExploration
import java.util.*
import kotlin.collections.HashMap


object Debug : Logging {
	override val log by lazy { getLogger() }

	@JvmStatic
	fun main(args: Array<String>) = runBlocking{

		manualExploration(ExplorationAPI.config(args))

	}

	private suspend fun manualExploration(cfg: ConfigurationWrapper){
		val builder = ExploreCommandBuilder(
			strategies = defaultStrategies.plus(
				ManualExploration<Int>(resetOnStart = !cfg[org.droidmate.explorationModel.config.ConfigProperties.Output.debugMode])
			).toMutableList(),
			watcher = mutableListOf(),
			selectors = mutableListOf(
				StrategySelector(2, "allowRuntimePermission", StrategySelector.allowPermission),
				StrategySelector(3, "leftApp", leftApp),
				ManualExploration.selector(	42	)
			)
		)
		ExplorationAPI.explore(
			cfg = cfg,
			commandBuilder = builder,
			modelProvider = DefaultModelProvider()
		).logResult()
	}

	private val defaultStrategies: Collection<AbstractStrategy> = listOf(Terminate, AllowRuntimePermission() )
	@Suppress("unused")
	private val defaultSelectors: (cfg: Configuration) -> Collection<StrategySelector> = { cfg ->
		listOf(
			// timeLimit*60*1000 such that we can specify the limit in minutes instead of milliseconds
			StrategySelector(
				0,
				"timeBasedTerminate",
				StrategySelector.timeBasedTerminate,
				bundle = arrayOf(cfg[ConfigProperties.Selectors.timeLimit]*60*1000)
			),
			StrategySelector(1, "stuckInLoop", isStuck),
			StrategySelector(2, "allowRuntimePermission", allowPermission), // HAS To Be BEFORE 'leftApp' check since permission requests would be interpreted as out-of-app otherwise
			StrategySelector(3, "leftApp", leftApp)
		)
	}

	private var numPermissions = HashMap<UUID,Int>()  // avoid some options to be misinterpreted as permission request to be infinitely triggered
	private val allowPermission: SelectorFunction = { eContext, pool, _ ->
		if (numPermissions.compute(eContext.getCurrentState().uid){ _,v -> v?.inc()?: 0 } ?: 0 < 5 && eContext.getCurrentState().isRequestRuntimePermissionDialogBox) {
			pool.getFirstInstanceOf(AllowRuntimePermission::class.java)
		}
		else{
			null
		}
	}

	private var lastStuck = -1
	private val isStuck: SelectorFunction = { eContext, strategyPool, _ ->
		val reset by lazy{ strategyPool.getOrCreate("Reset") { Reset() } }
		val lastActions by lazy{ eContext.explorationTrace.getActions().takeLast(20) }
		when{
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
//					Terminate // TODO maybe we want instead Reset() and ignore this case?
				} else {
					lastStuck = eContext.explorationTrace.size
					reset
				}
			}
			else ->null
		}
	}

	private val leftApp: SelectorFunction = { eContext, strategyPool, _ ->
		val currentState = eContext.getCurrentState()
		val actions by lazy{ eContext.explorationTrace.getActions() }
		val reset by lazy{ strategyPool.getOrCreate("Reset") { Reset() } }
		when{
			eContext.isEmpty() || (actions.size == 1 && actions.first().actionType.isFetch()) ->{
				null
			}
			currentState.isAppHasStoppedDialogBox -> reset
			!eContext.belongsToApp(currentState) ->  // cannot check a single root-node since the first one may be some assistance window e.g. keyboard
				if(actions.takeLast(3).any{ it.actionType.isPressBack() }) reset
				else Back // the state does no longer belong to the AUT (happens by clicking browser links or advertisement)
			currentState.isHomeScreen -> {
				val recentRestart = actions.takeLast(10).count { it.actionType.isLaunchApp() }>2
				if( recentRestart && actions.size>20){
					log.error("Cannot start app: we are late in the exploration and already reset at least twice in the last actions.")
					Terminate
				} // we are late in the exploration and already reset at least twice in the last actions
				else reset
			}
			else -> null
		}
	}

	private fun Map<Apk, FailableExploration>.logResult()= forEach { apk, (eContext, errors) ->
		log.info("exploration of {} ended with {} errors; model: {}",	apk.packageName, errors.size, eContext?.model)
		errors.forEach { e ->
			log.error("exception while exploring ${apk.path}", e)
		}
	}
}