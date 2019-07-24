package org.droidmate.command

import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors.pressBackProbability
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.configuration.ConfigProperties.Selectors.stopOnExhaustion
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.exploration.SelectorFunction
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.modelFeatures.reporter.*
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.others.MinimizeMaximize
import org.droidmate.exploration.strategy.others.RotateUI
import org.droidmate.exploration.strategy.playback.Playback
import org.droidmate.exploration.strategy.widget.DFS
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.ModelProvider
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.tools.ApksProvider
import org.droidmate.tools.DeviceTools
import org.droidmate.tools.IDeviceTools
import java.nio.file.Path
import java.util.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class ExploreCommandBuilder(
    val strategies: MutableList<AExplorationStrategy> = mutableListOf(),
    val selectors: MutableList<AStrategySelector> = mutableListOf(),
    val watcher: MutableList<ModelFeatureI> = mutableListOf()
) {
    companion object {
        fun fromConfig(cfg: ConfigurationWrapper): ExploreCommandBuilder {
            return ExploreCommandBuilder().fromConfig(cfg)
        }

        @JvmStatic
        fun defaultReportWatcher(cfg: ConfigurationWrapper): LinkedList<ModelFeatureI> {
            val reportDir = cfg.droidmateOutputReportDirPath.toAbsolutePath()
            val resourceDir = cfg.resourceDir.toAbsolutePath()

            return LinkedList<ModelFeatureI>()
                .also {
                    it.addAll(
                        listOf(
                            AggregateStats(reportDir, resourceDir),
                            Summary(reportDir, resourceDir),
                            ApkViewsFileMF(reportDir, resourceDir),
                            ApiCountMF(
                                reportDir,
                                resourceDir,
                                includePlots = cfg[ConfigProperties.Report.includePlots]
                            ),
                            ClickFrequencyMF(
                                reportDir,
                                resourceDir,
                                includePlots = cfg[ConfigProperties.Report.includePlots]
                            ),
                            ApiActionTraceMF(reportDir, resourceDir),
                            ActivitySeenSummaryMF(reportDir, resourceDir),
                            ActionTraceMF(reportDir, resourceDir),
                            WidgetApiTraceMF(reportDir, resourceDir),
                            VisualizationGraphMF(reportDir, resourceDir)
                        )
                    )
                }
        }
    }

    private fun fromConfig(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        conditionalEnable(cfg[ConfigProperties.Strategies.playback], cfg) { withPlayback(cfg) }

        conditionalEnable(cfg[actionLimit] > 0, cfg) { terminateAfterActions(cfg) }
        conditionalEnable(cfg[actionLimit] > 0, cfg) { terminateAfterTime(cfg) }

        resetOnCrash()

        conditionalEnable(cfg[ConfigProperties.Strategies.allowRuntimeDialog]) { allowRuntimePermissions() }
        conditionalEnable(cfg[ConfigProperties.Strategies.denyRuntimeDialog]) { denyRuntimePermissions() }

        pressBackOnAds()
        resetOnInvalidState()

        conditionalEnable(cfg[resetEvery] > 0, cfg) { resetOnIntervals(cfg) }
        conditionalEnable(cfg[pressBackProbability] > 0, cfg) { randomBack(cfg) }

        conditionalEnable(cfg[stopOnExhaustion]) { terminateIfAllExplored() }

        conditionalEnable(cfg[ConfigProperties.Strategies.dfs]) { usingDFS() }

        conditionalEnable(cfg[ConfigProperties.Strategies.explore], cfg) { exploreRandomly(cfg) }

        conditionalEnable(
            cfg[StatementCoverageMF.Companion.StatementCoverage.enableCoverage],
            cfg
        ) { collectStatementCoverage() }

        return this
    }

    fun getNextSelectorPriority(): Int {
        return selectors.size * 10
    }

    private fun conditionalEnable(
        condition: Boolean,
        builderFunction: () -> Any
    ) {

        if (condition) {
            builderFunction()
        }
    }

    private fun conditionalEnable(
        condition: Boolean,
        cfg: ConfigurationWrapper,
        builderFunction: (ConfigurationWrapper) -> Any
    ) {

        if (condition) {
            builderFunction(cfg)
        }
    }

    @Deprecated("no longer necessary, to be deleted")
    fun addRequiredStrategies(): ExploreCommandBuilder {
        return addTerminateStrategy()
            .addBackStrategy()
            .addResetStrategy()
    }

    @Deprecated("no longer necessary, to be deleted")
    fun addTerminateStrategy(): ExploreCommandBuilder {
        strategies.add(Terminate)
        return this
    }

    fun terminateAfterTime(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return terminateAfterTime(cfg[ConfigProperties.Selectors.timeLimit])
    }

    fun terminateAfterTime(seconds: Int): ExploreCommandBuilder = apply{
        strategies.add( DefaultStrategies.timeBasedTerminate(getNextSelectorPriority(),seconds) )
    }

    fun terminateAfterActions(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return terminateAfterActions(cfg[actionLimit])
    }

    fun terminateAfterActions(actionLimit: Int): ExploreCommandBuilder = apply{
        strategies.add( DefaultStrategies.actionBasedTerminate(getNextSelectorPriority(), actionLimit) )
    }

    fun terminateIfAllExplored(): ExploreCommandBuilder {
        strategies.add( DefaultStrategies.explorationExhausted(getNextSelectorPriority()) )
        return this
    }

		@Deprecated("no longer necessary, should be removed")
    fun addResetStrategy(): ExploreCommandBuilder {
        strategies.add(Reset())
        return this
    }

    fun resetOnInvalidState(): ExploreCommandBuilder {
        strategies.add( DefaultStrategies.handleTargetAbsence(getNextSelectorPriority()) )
        return this
    }

    fun resetOnIntervals(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return resetOnIntervals(cfg[resetEvery])
    }

    fun resetOnIntervals(actionInterval: Int): ExploreCommandBuilder {
        strategies.add( DefaultStrategies.intervalReset(getNextSelectorPriority(), actionInterval) )
        return this
    }

		@Deprecated("no longer necessary, to be deleted")
    fun startWithReset(): ExploreCommandBuilder {
        TODO("no longer supported")
    }

    fun resetOnCrash(): ExploreCommandBuilder {
        strategies.add( DefaultStrategies.resetOnAppCrash(getNextSelectorPriority()) )
        return this
    }

    @Deprecated("no longer necessary, to be deleted")
    fun addBackStrategy(): ExploreCommandBuilder {
        strategies.add(Back)
        return this
    }

    fun pressBackOnAds(): ExploreCommandBuilder {
        strategies.add( DefaultStrategies.handleAdvertisment(getNextSelectorPriority()) )
        return this
    }

    fun randomBack(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return randomBack(cfg[pressBackProbability], cfg.randomSeed)
    }

    fun randomBack(probability: Double, randomSeed: Long): ExploreCommandBuilder {
        strategies.add( DefaultStrategies.randomBack(getNextSelectorPriority(), probability, Random(randomSeed)) )
        return this
    }

    fun exploreRandomly(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return exploreRandomly(
            cfg.randomSeed,
            cfg[ConfigProperties.Exploration.widgetActionDelay],
            cfg[ConfigProperties.Strategies.Parameters.biasedRandom],
            cfg[ConfigProperties.Strategies.Parameters.randomScroll]
        )
    }

    @Deprecated("no longer necessary, you can directly add the random strategy instead", ReplaceWith("addRandomStrategy()"))
    fun exploreRandomly(
        randomSeed: Long = 0,
        delay: Long = 0,
        enableScroll: Boolean = false,
        biasedRandom: Boolean = false
    ): ExploreCommandBuilder {
        return addRandomStrategy()
    }

    @Deprecated("no argument required",ReplaceWith("addRandomStrategy()"))
    fun addRandomStrategy(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return addRandomStrategy()
    }

    @JvmOverloads
    fun addRandomStrategy(): ExploreCommandBuilder {
        strategies.add(RandomWidget(getNextSelectorPriority()))
        return this
    }

    @Deprecated("no longer necessary, you can directly add the random strategy instead", ReplaceWith("addRandomStrategy()"))
    fun addRandomExploreSelector(): ExploreCommandBuilder {
        addRandomStrategy()
        return this
    }

    fun allowRuntimePermissions(): ExploreCommandBuilder {
        addAllowPermissionStrategy()
        return this
    }

    fun addAllowPermissionStrategy(): ExploreCommandBuilder {
        strategies.add(DefaultStrategies.allowPermission(getNextSelectorPriority()))
        return this
    }

    @Deprecated("redundant")
    fun addAllowPermissionSelector(): ExploreCommandBuilder {
        TODO("deprecated")
    }

    fun denyRuntimePermissions(): ExploreCommandBuilder {
        addDenyPermissionStrategy()
        return this
    }

    fun addDenyPermissionStrategy(): ExploreCommandBuilder {
        strategies.add(DefaultStrategies.denyPermission(getNextSelectorPriority()))
        return this
    }

    @Deprecated("redundant")
    fun addDenyPermissionSelector(): ExploreCommandBuilder {
        TODO("deprecated")
    }

    @Deprecated("no longer necessary, to be deleted")
    fun addRotateUIStrategy(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return addRotateUIStrategy(cfg[ConfigProperties.Strategies.Parameters.uiRotation])
    }

    @Deprecated("no longer necessary, to be deleted")
    fun addRotateUIStrategy(uiRotation: Int): ExploreCommandBuilder {
        strategies.add(RotateUI(uiRotation))
        return this
    }

    @Deprecated("no longer necessary, to be deleted")
    fun addMinimizeMaximizeStrategy(): ExploreCommandBuilder {
        strategies.add(MinimizeMaximize())

        return this
    }

    fun withPlayback(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return withPlayback(cfg.getPath(cfg[ConfigProperties.Selectors.playbackModelDir]))
    }

    fun withPlayback(playbackModelDir: Path): ExploreCommandBuilder {
        return addPlaybackStrategy(playbackModelDir)
    }

    fun addPlaybackStrategy(playbackModelDir: Path): ExploreCommandBuilder {
        strategies.add(Playback(playbackModelDir.toAbsolutePath()))
        return this
    }

    @Deprecated("no longer used, invoke addStrategy directly instead")
    fun addPlaybackSelector(): ExploreCommandBuilder {
        TODO("no longer supported")
    }

    fun usingDFS(): ExploreCommandBuilder {
        return addDFSStrategy()
    }

    fun addDFSStrategy(): ExploreCommandBuilder {
        strategies.add(DFS())
        return this
    }

    @Deprecated("no longer used, invoke addStrategy directly instead")
    fun addDFSSelector(): ExploreCommandBuilder {
        TODO("no longer supported")
    }

    fun collectStatementCoverage(): ExploreCommandBuilder {
        selectors.add( DefaultSelector.statementCoverage(getNextSelectorPriority()) )
        return this
    }

    @Deprecated("no longer supported use AExplorationStrategy type instead")
    fun withStrategy(strategy: ISelectableExplorationStrategy): ExploreCommandBuilder {
        TODO("deprecated")
    }

    fun withStrategy(strategy: AExplorationStrategy): ExploreCommandBuilder {
        strategies.add(strategy)
        return this
    }

    @Deprecated("no longer supported use AStrategySelector type instead")
    fun withSelector(selector: StrategySelector): ExploreCommandBuilder {
        TODO("deprecated")
    }

    fun withSelector(selector: AStrategySelector): ExploreCommandBuilder {
        selectors.add(selector)
        return this
    }

    @Deprecated("no longer supported use AStrategySelector type instead")
    fun remove(selector: SelectorFunction): ExploreCommandBuilder {
        TODO("deprecated")
    }

    fun remove(selector: AStrategySelector): ExploreCommandBuilder {
        val target = selectors.firstOrNull { it.uniqueStrategyName == selector.uniqueStrategyName }

        if (target != null) {
            selectors.remove(target)
        }
        return this
    }

    @JvmOverloads
    @Deprecated("no longer supported use addSelector instead")
    fun append(
        newDescription: String,
        newSelector: SelectorFunction,
        bundle: Array<Any> = emptyArray()
    ): ExploreCommandBuilder {
        TODO("deprecated")
    }

    @Deprecated("no longer supported use addSelector instead")
    @JvmOverloads
    fun insertBefore(
        oldSelector: SelectorFunction,
        newDescription: String,
        newSelector: SelectorFunction,
        bundle: Array<Any> = emptyArray()
    ): ExploreCommandBuilder {
        TODO("deprecated")
    }

    @JvmOverloads
    open fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> build(
        cfg: ConfigurationWrapper,
        deviceTools: IDeviceTools = DeviceTools(cfg),
        strategyProvider: ExplorationStrategyPool = ExplorationStrategyPool(
            this.strategies,
            this.selectors
        ),
        watcher: List<ModelFeatureI> = defaultReportWatcher(cfg),
        modelProvider: ModelProvider<M>
    ): ExploreCommand<M, S, W> {
        val apksProvider = ApksProvider(deviceTools.aapt)

        this.watcher.addAll(watcher)

        return ExploreCommand(
            cfg, apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer,
            strategyProvider, modelProvider, this.watcher
        )
    }
}