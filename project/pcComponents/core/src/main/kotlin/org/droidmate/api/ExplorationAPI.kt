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
import org.droidmate.command.ExploreCommandBuilder
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.exploration.modelFeatures.reporter.VisualizationGraphMF
import org.droidmate.exploration.strategy.AExplorationStrategy
import org.droidmate.exploration.strategy.AStrategySelector
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.DefaultModelProvider
import org.droidmate.explorationModel.factory.ModelProvider
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.misc.FailableExploration
import org.droidmate.tools.ApksProvider
import org.droidmate.tools.DeviceTools
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
			instrument(cfg)

		if (cfg[ConfigProperties.ExecutionMode.inline])
			inline(cfg)

		if (cfg[ConfigProperties.ExecutionMode.explore])
			explore(cfg, modelProvider = DefaultModelProvider())

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
	fun defaultReporter(cfg: ConfigurationWrapper): MutableList<ModelFeatureI> =
		mutableListOf(VisualizationGraphMF(cfg.droidmateOutputReportDirPath, cfg.resourceDir))

	@JvmStatic
	fun buildFromConfig(cfg: ConfigurationWrapper) = ExploreCommandBuilder.fromConfig(cfg)


	/****************************** Apk-Instrument (Coverage) API methods *****************************/

	@JvmStatic
	@JvmOverloads
	suspend fun instrument(args: Array<String> = emptyArray()) = coroutineScope{
		instrument(setup(args))
	}

	@JvmStatic
	suspend fun instrument(cfg: ConfigurationWrapper) = coroutineScope{
		log.info("instrument the apks for coverage if necessary")
		CoverageCommand(cfg).execute()
	}

	/****************************** Exploration API methods *****************************/

	@JvmStatic
	@JvmOverloads
	suspend fun<M:AbstractModel<S,W>,S: State<W>,W: Widget> explore(args: Array<String> = emptyArray(),
	                                                                commandBuilder: ExploreCommandBuilder? = null,
	                                                                watcher: List<ModelFeatureI>? = null,
	                                                                modelProvider: ModelProvider<M>? = null
	): Map<Apk, FailableExploration> =
		explore(setup(args), commandBuilder, watcher, modelProvider)

	/**
	 * Convenience function which allows you to define the strategies and selectors to be used directly,
	 * such that it is not mandatory to invoke any ExploreCommandBuilder.
	 * Consequently you have to pass a list of strategies with at least one element and
	 * the list of selectors is empty by default and if no watcher is specified the features listed in
	 * [defaultReporter] are going to be used.
	 */
	@JvmOverloads
	suspend fun<M:AbstractModel<S,W>,S: State<W>,W: Widget> explore(args: Array<String> = emptyArray(),
	                                                                strategies: List<AExplorationStrategy>,
	                                                                selectors: List<AStrategySelector> = emptyList(),
	                                                                watcher: List<ModelFeatureI>? = null,
	                                                                modelProvider: ModelProvider<M>? = null
	): Map<Apk, FailableExploration> =
		explore(setup(args), strategies, selectors, watcher?.toMutableList(), modelProvider)

	@JvmStatic
	@JvmOverloads
	suspend fun<M:AbstractModel<S,W>, S: State<W>,W: Widget> explore(cfg: ConfigurationWrapper,
	                                                                 commandBuilder: ExploreCommandBuilder? = null,
	                                                                 watcher: List<ModelFeatureI>? = null,
	                                                                 modelProvider: ModelProvider<M>? = null
	): Map<Apk, FailableExploration> = coroutineScope {
		val builder = commandBuilder ?: ExploreCommandBuilder.fromConfig(cfg)
		explore( cfg, builder.strategies, builder.selectors, watcher?.toMutableList(), modelProvider )
	}

	/**
	 * Convenience function which allows you to define a custom model provider, the strategies and selectors to be used directly,
	 * such that it is not mandatory to invoke any ExploreCommandBuilder.
	 * Consequently the list of selectors is empty by default and if no watcher is specified the features listed in
	 * [defaultReporter] are going to be used.
	 */
	private suspend fun<M:AbstractModel<S,W>, S: State<W>,W: Widget> explore(
		cfg: ConfigurationWrapper,
		modelProvider: ModelProvider<M>,
		strategies: List<AExplorationStrategy>,
		selectors: List<AStrategySelector>,
		watcher: MutableList<ModelFeatureI>
	): Map<Apk, FailableExploration> =
		if(strategies.isEmpty()){
			throw IllegalStateException("you have to specify at least one strategy, to be used by droidmate")
		} else coroutineScope {
			val runStart = Date()

			val strategyProvider = ExplorationStrategyPool( strategies, selectors	)
			val deviceTools = DeviceTools(cfg)
			val apksProvider = ApksProvider(deviceTools.aapt)
			val exploration =
				ExploreCommand(
					cfg, apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer,
					strategyProvider, modelProvider, watcher
				)

			log.info("EXPLORATION start timestamp: $runStart")
			log.info("Running in Android $cfg.androidApi compatibility mode (api23+ = version 6.0 or newer).")

			exploration.execute(cfg)
		}

	/**
	 * Convenience function which allows you to define the strategies and selectors to be used directly,
	 * such that it is not mandatory to invoke any ExploreCommandBuilder.
	 * Consequently the list of selectors is empty by default and if no watcher is specified the features listed in
	 * [defaultReporter] are going to be used.
	 */
	@JvmOverloads suspend fun<M:AbstractModel<S,W>, S: State<W>,W: Widget> explore(
		cfg: ConfigurationWrapper,
		strategies: List<AExplorationStrategy>,
		selectors: List<AStrategySelector> = emptyList(),
		watcher: MutableList<ModelFeatureI>? = null,
		modelProvider: ModelProvider<M>? = null
	): Map<Apk, FailableExploration> = modelProvider?.let{
		explore( cfg, modelProvider, strategies, selectors, watcher ?: defaultReporter(cfg) )
	} ?:	explore( cfg, DefaultModelProvider(), strategies, selectors, watcher ?: defaultReporter(cfg))


	@JvmStatic
	@JvmOverloads
	suspend fun inline(args: Array<String> = emptyArray()) {
		val cfg = setup(args)
		inline(cfg)
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
	                             commandBuilder: ExploreCommandBuilder? = null,
	                             watcher: List<ModelFeatureI>? = null
	): Map<Apk, FailableExploration> = coroutineScope{
		val cfg = setup(args)
		Instrumentation.inline(cfg)

		explore(cfg, commandBuilder, watcher, modelProvider = DefaultModelProvider())
	}
}