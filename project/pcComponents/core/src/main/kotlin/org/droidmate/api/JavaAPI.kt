package org.droidmate.api

import com.natpryce.konfig.CommandLineOption
import kotlinx.coroutines.runBlocking
import org.droidmate.command.ExploreCommandBuilder
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.DefaultModelProvider
import org.droidmate.explorationModel.factory.ModelProvider
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
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
    fun buildFromConfig(cfg: ConfigurationWrapper) = ExploreCommandBuilder.fromConfig(cfg)

    @JvmStatic
    @Deprecated("use the new model-provider mechanism",
        replaceWith= ReplaceWith("DefaultModelProvider()","import org.droidmate.explorationModel.factory.DefaultModelProvider"))
    fun defaultModelProvider(cfg: ConfigurationWrapper) = DefaultModelProvider()

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
    fun<M: AbstractModel<S, W>, S: State<W>,W: Widget> explore(
        cfg: ConfigurationWrapper,
        commandBuilder: ExploreCommandBuilder? = null,
        watcher: List<ModelFeatureI>? = null,
        modelProvider: ModelProvider<M>? = null
    ) = runBlocking {
        ExplorationAPI.explore(cfg, commandBuilder, watcher, modelProvider)
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
        commandBuilder: ExploreCommandBuilder? = null,
        watcher: List<ModelFeatureI>? = null): Map<Apk, FailableExploration> = runBlocking {
        ExplorationAPI.inlineAndExplore(args, commandBuilder, watcher)
    }
}