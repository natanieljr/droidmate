package org.droidmate.api

import com.natpryce.konfig.CommandLineOption
import kotlinx.coroutines.runBlocking
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.explorationModel.Model
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.misc.FailableExploration

object JavaAPI {
    @JvmStatic
    fun config(args: Array<String>, vararg options: CommandLineOption): ConfigurationWrapper =
        ExplorationAPI.config(args, *options)

    @JvmStatic
    fun customCommandConfig(args: Array<String>, vararg options: CommandLineOption): ConfigurationWrapper =
        ExplorationAPI.customCommandConfig(args, *options)

    @JvmStatic
    fun defaultReporter(cfg: ConfigurationWrapper): List<ModelFeatureI> =
        ExplorationAPI.defaultReporter(cfg)

    @JvmStatic
    fun defaultStrategies(cfg: ConfigurationWrapper) = ExplorationAPI.defaultStrategies(cfg)

    @JvmStatic
    fun defaultSelectors(cfg: ConfigurationWrapper) = ExplorationAPI.defaultSelectors(cfg)

    @JvmStatic
    fun defaultModelProvider(cfg: ConfigurationWrapper): ((String) -> Model)
            = { appName -> Model.emptyModel(ModelConfig(appName, cfg = cfg))}

    @JvmStatic
    @JvmOverloads
    fun instrument(args: Array<String> = emptyArray()) = runBlocking {
        ExplorationAPI.instrument(args)
    }

    @JvmStatic
    fun instrument(cfg: ConfigurationWrapper) = runBlocking {
        ExplorationAPI.instrument(cfg)
    }

    @JvmStatic
    @JvmOverloads
    fun explore(
        cfg: ConfigurationWrapper,
        strategies: List<ISelectableExplorationStrategy>? = null,
        selectors: List<StrategySelector>? = null,
        watcher: List<ModelFeatureI>? = null,
        modelProvider: ((String) -> Model)? = null
    ) = runBlocking {
        ExplorationAPI.explore(cfg, strategies, selectors, watcher, modelProvider)
    }

    @JvmStatic
    @JvmOverloads
    fun inline(args: Array<String> = emptyArray()) {
        runBlocking {
            ExplorationAPI.inline(args)
        }
    }

    @JvmStatic
    fun inline(cfg: ConfigurationWrapper) {
        runBlocking {
            ExplorationAPI.inline(cfg)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun inlineAndExplore(
        args: Array<String> = emptyArray(),
        strategies: List<ISelectableExplorationStrategy>? = null,
        selectors: List<StrategySelector>? = null,
        watcher: List<ModelFeatureI>? = null): Map<Apk, FailableExploration> = runBlocking {
        ExplorationAPI.inlineAndExplore(args, strategies, selectors, watcher)
    }
}