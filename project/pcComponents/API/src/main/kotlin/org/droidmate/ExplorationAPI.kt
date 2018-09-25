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
@file:Suppress("unused")

package org.droidmate

import org.droidmate.command.CoverageCommand
import org.droidmate.command.ExploreCommand
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.statemodel.Model
import org.droidmate.exploration.statemodel.ModelConfig
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.report.Reporter
import org.droidmate.report.apk.VisualizationGraph
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
object ExplorationAPI {
	private val log by lazy { LoggerFactory.getLogger(ExplorationAPI::class.java) }
	val defaultReporter = listOf(VisualizationGraph())

	/**
	 * entry-point to explore an application with a (subset) of default exploration strategies as specified in the property `explorationStrategies`
	 */
	@JvmStatic  // -config ../customConfig.properties
	fun main(args: Array<String>) { // e.g.`-config filePath` or `--configPath=filePath`
		val cfg = setup(args)

		if (cfg[ConfigProperties.ExecutionMode.coverage])
			instrument(cfg)

		if (cfg[ConfigProperties.ExecutionMode.inline])
			inline(cfg)

		if (cfg[ConfigProperties.ExecutionMode.explore])
			explore(cfg)

		if ( !cfg[ConfigProperties.ExecutionMode.explore] &&
				!cfg[ConfigProperties.ExecutionMode.inline] &&
				!cfg[ConfigProperties.ExecutionMode.coverage] ){
			log.info("DroidMate was not configured to run in any known exploration mode. Finishing.")
		}
	}

	@JvmStatic
	val config: (args: Array<String>) -> ConfigurationWrapper = { args -> ConfigurationBuilder().build(args, FileSystems.getDefault()) }


	/****************************** Apk-Instrument (Coverage) API methods *****************************/
	@JvmStatic
	@JvmOverloads
	fun instrument(args: Array<String> = emptyArray()) {
		instrument(setup(args))
	}

	@JvmStatic
	fun instrument(cfg: ConfigurationWrapper) {
		log.info("instrument the apks for coverage if necessary")
		tryExecute(CoverageCommand(cfg), cfg)
	}

	/****************************** Exploration API methods *****************************/
	@JvmStatic
	@JvmOverloads
	fun explore(args: Array<String> = emptyArray(), strategies: List<ISelectableExplorationStrategy>? = null,
	            selectors: List<StrategySelector>? = null, reportCreators: List<Reporter> = defaultReporter,
	            modelProvider: ((String) -> Model)? = null): List<ExplorationContext> {
		return explore(setup(args), strategies, selectors, reportCreators, modelProvider)
	}

	@JvmStatic
	@JvmOverloads
	fun explore(cfg: ConfigurationWrapper, strategies: List<ISelectableExplorationStrategy>? = null,
	            selectors: List<StrategySelector>? = null, reportCreators: List<Reporter> = defaultReporter,
	            modelProvider: ((String) -> Model)? = null): List<ExplorationContext> {
		val runStart = Date()
		val exploration = ExploreCommand.build(cfg, reportCreators = reportCreators, strategies = strategies
				?: ExploreCommand.getDefaultStrategies(cfg), selectors = selectors ?: ExploreCommand.getDefaultSelectors(cfg)
				, modelProvider = modelProvider ?: { appName -> Model.emptyModel(ModelConfig(appName, cfg = cfg))} )
		log.info("EXPLORATION start timestamp: $runStart")
		log.info("Running in Android $cfg.androidApi compatibility mode (api23+ = version 6.0 or newer).")

		return tryExecute(exploration, cfg)
	}

	@JvmStatic
	@JvmOverloads
	fun inline(args: Array<String> = emptyArray()) {
		val cfg = setup(args)
		inline(cfg)
	}

	@JvmStatic
	fun inline(cfg: ConfigurationWrapper) {
		Instrumentation.inline(cfg)
	}

	/**
	 * 1. inline the apks in the directory if they do not end on `-inlined.apk`
	 * 2. run the exploration with the strategies listed in the property `explorationStrategies`
	 */
	@JvmStatic
	@JvmOverloads
	fun inlineAndExplore(args: Array<String> = emptyArray(), strategies: List<ISelectableExplorationStrategy>? = null,
	                     selectors: List<StrategySelector>? = null, reportCreators: List<Reporter> = defaultReporter): List<ExplorationContext> {
		val cfg = setup(args)
		Instrumentation.inline(cfg)

		return explore(cfg, strategies, selectors, reportCreators)
	}

}