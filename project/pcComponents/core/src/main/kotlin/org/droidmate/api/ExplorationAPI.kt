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

package org.droidmate.api

import com.natpryce.konfig.CommandLineOption
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.droidmate.command.CoverageCommand
import org.droidmate.command.ExploreCommand
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.modelFeatures.reporter.VisualizationGraphMF
import org.droidmate.explorationModel.Model
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.misc.FailableExploration
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
object ExplorationAPI {
	private val log by lazy { LoggerFactory.getLogger(ExplorationAPI::class.java) }

	/**
	 * Entry-point to explore an application with a (subset) of default exploration strategies as specified in the property `explorationStrategies`
	 */
	@JvmStatic  // -config ../customConfig.properties
	// use JVM arg -Dlogback.configurationFile=default-logback.xml
	fun main(args: Array<String>) = runBlocking(CoroutineName("main")) { // e.g.`-config filePath` or `--configPath=filePath`
		val cfg = setup(args)

		if (cfg[ConfigProperties.ExecutionMode.coverage])
            ExplorationAPI.instrument(cfg)

		if (cfg[ConfigProperties.ExecutionMode.inline])
            ExplorationAPI.inline(cfg)

		if (cfg[ConfigProperties.ExecutionMode.explore])
            ExplorationAPI.explore(cfg)

		if ( !cfg[ConfigProperties.ExecutionMode.explore] &&
				!cfg[ConfigProperties.ExecutionMode.inline] &&
				!cfg[ConfigProperties.ExecutionMode.coverage] ){
			log.info("DroidMate was not configured to run in any known exploration mode. Finishing.")
		}
	}

	@JvmStatic
	fun config(args: Array<String>, vararg options: CommandLineOption): ConfigurationWrapper =
		ConfigurationBuilder().build(args, FileSystems.getDefault(), *options)

	@JvmStatic
	fun customCommandConfig(args: Array<String>, vararg options: CommandLineOption): ConfigurationWrapper =
		ConfigurationBuilder().buildRestrictedOptions(args, FileSystems.getDefault(), *options)

	@JvmStatic
	fun defaultReporter(cfg: ConfigurationWrapper): List<ModelFeatureI> =
		listOf(VisualizationGraphMF(cfg.droidmateOutputReportDirPath, cfg.resourceDir))

	@JvmStatic
	fun defaultStrategies(cfg: ConfigurationWrapper) = ExploreCommand.getDefaultStrategies(cfg)

	@JvmStatic
	fun defaultSelectors(cfg: ConfigurationWrapper) = ExploreCommand.getDefaultSelectors(cfg)

	@JvmStatic
	fun defaultModelProvider(cfg: ConfigurationWrapper): ((String) -> Model)
			= { appName -> Model.emptyModel(ModelConfig(appName, cfg = cfg))}


	/****************************** Apk-Instrument (Coverage) API methods *****************************/
	@JvmStatic
	@JvmOverloads
	suspend fun instrument(args: Array<String> = emptyArray()) = coroutineScope{
        ExplorationAPI.instrument(setup(args))
	}

	@JvmStatic
	suspend fun instrument(cfg: ConfigurationWrapper) = coroutineScope{
		log.info("instrument the apks for coverage if necessary")
		CoverageCommand(cfg).execute()
	}

	/****************************** Exploration API methods *****************************/
	@JvmStatic
	@JvmOverloads
	suspend fun explore(args: Array<String> = emptyArray(),
						strategies: List<ISelectableExplorationStrategy>? = null,
                        selectors: List<StrategySelector>? = null,
						watcher: List<ModelFeatureI>? = null,
                        modelProvider: ((String) -> Model)? = null): Map<Apk, FailableExploration> {
		return ExplorationAPI.explore(setup(args), strategies, selectors, watcher, modelProvider)
	}

	@JvmStatic
	@JvmOverloads
	suspend fun explore(cfg: ConfigurationWrapper,
						strategies: List<ISelectableExplorationStrategy>? = null,
                        selectors: List<StrategySelector>? = null,
						watcher: List<ModelFeatureI>? = null,
                        modelProvider: ((String) -> Model)? = null): Map<Apk, FailableExploration> = coroutineScope {
		val runStart = Date()
		val exploration = ExploreCommand.build(cfg,
			watcher = watcher ?: defaultReporter(cfg),
			strategies = strategies ?: defaultStrategies(cfg),
			selectors = selectors ?: defaultSelectors(cfg),
			modelProvider = modelProvider ?: defaultModelProvider(cfg) )
		log.info("EXPLORATION start timestamp: $runStart")
		log.info("Running in Android $cfg.androidApi compatibility mode (api23+ = version 6.0 or newer).")

		exploration.execute(cfg)
	}


	@JvmStatic
	@JvmOverloads
	suspend fun inline(args: Array<String> = emptyArray()) {
		val cfg = setup(args)
        ExplorationAPI.inline(cfg)
	}

	@JvmStatic
	suspend fun inline(cfg: ConfigurationWrapper) {
		Instrumentation.inline(cfg)
	}

	/**
	 * 1. Inline the apks in the directory if they do not end on `-inlined.apk`
	 * 2. Run the exploration with the strategies listed in the property `explorationStrategies`
	 */
	@JvmStatic
	@JvmOverloads
	suspend fun inlineAndExplore(args: Array<String> = emptyArray(),
								 strategies: List<ISelectableExplorationStrategy>? = null,
								 selectors: List<StrategySelector>? = null,
								 watcher: List<ModelFeatureI>? = null
	): Map<Apk, FailableExploration> = coroutineScope{
		val cfg = setup(args)
		Instrumentation.inline(cfg)

		ExplorationAPI.explore(cfg, strategies, selectors, watcher)
	}
}