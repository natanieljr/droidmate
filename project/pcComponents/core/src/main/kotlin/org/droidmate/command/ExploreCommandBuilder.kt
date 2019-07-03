package org.droidmate.command

import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors.pressBackProbability
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.configuration.ConfigProperties.Selectors.stopOnExhaustion
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.SelectorFunction
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.modelFeatures.reporter.*
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.others.MinimizeMaximize
import org.droidmate.exploration.strategy.others.RotateUI
import org.droidmate.exploration.strategy.playback.Playback
import org.droidmate.exploration.strategy.widget.AllowRuntimePermission
import org.droidmate.exploration.strategy.widget.DFS
import org.droidmate.exploration.strategy.widget.DenyRuntimePermission
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
    val strategies: MutableList<ISelectableExplorationStrategy> = mutableListOf(),
    val selectors: MutableList<StrategySelector> = mutableListOf(),
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
        addRequiredStrategies()

        conditionalEnable(cfg[ConfigProperties.Strategies.playback], cfg) { withPlayback(cfg) }

        startWithReset()

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

        conditionalEnable(cfg[ConfigProperties.Strategies.rotateUI], cfg) { addRotateUIStrategy(cfg) }
        conditionalEnable(cfg[ConfigProperties.Strategies.minimizeMaximize]) { addMinimizeMaximizeStrategy() }

        return this
    }

    private fun getNextSelectorPriority(): Int {
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

    fun addRequiredStrategies(): ExploreCommandBuilder {
        return addTerminateStrategy()
            .addBackStrategy()
            .addResetStrategy()
    }

    fun addTerminateStrategy(): ExploreCommandBuilder {
        strategies.add(Terminate)
        return this
    }

    fun terminateAfterTime(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return terminateAfterTime(cfg[ConfigProperties.Selectors.timeLimit])
    }

    fun terminateAfterTime(seconds: Int): ExploreCommandBuilder {
        selectors.add(
            StrategySelector(
                getNextSelectorPriority(),
                "timeBasedTerminate",
                StrategySelector.timeBasedTerminate,
                bundle = arrayOf(seconds)
            )
        )
        return this
    }

    fun terminateAfterActions(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return terminateAfterActions(cfg[actionLimit])
    }

    fun terminateAfterActions(actionLimit: Int): ExploreCommandBuilder {
        selectors.add(
            StrategySelector(
                getNextSelectorPriority(),
                "actionBasedTerminate",
                StrategySelector.actionBasedTerminate,
                bundle = arrayOf(actionLimit)
            )
        )
        return this
    }

    fun terminateIfAllExplored(): ExploreCommandBuilder {
        selectors.add(
            StrategySelector(
                getNextSelectorPriority(),
                "explorationExhausted",
                StrategySelector.explorationExhausted
            )
        )
        return this
    }

    fun addResetStrategy(): ExploreCommandBuilder {
        strategies.add(Reset())
        return this
    }

    fun resetOnInvalidState(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "cannotExplore", StrategySelector.cannotExplore))
        return this
    }

    fun resetOnIntervals(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return resetOnIntervals(cfg[ConfigProperties.Selectors.resetEvery])
    }

    fun resetOnIntervals(actionInterval: Int): ExploreCommandBuilder {
        selectors.add(
            StrategySelector(
                getNextSelectorPriority(),
                "intervalReset",
                StrategySelector.intervalReset,
                bundle = arrayOf(actionInterval)
            )
        )
        return this
    }

    fun startWithReset(): ExploreCommandBuilder {
        selectors.add(
            StrategySelector(
                getNextSelectorPriority(),
                "startExplorationReset",
                StrategySelector.startExplorationReset
            )
        )
        return this
    }

    fun resetOnCrash(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "appCrashedReset", StrategySelector.appCrashedReset))
        return this
    }

    fun addBackStrategy(): ExploreCommandBuilder {
        strategies.add(Back)
        return this
    }

    fun pressBackOnAds(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "ads", StrategySelector.ads))
        return this
    }

    fun randomBack(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return randomBack(cfg[ConfigProperties.Selectors.pressBackProbability], cfg.randomSeed)
    }

    fun randomBack(probability: Double, randomSeed: Long): ExploreCommandBuilder {
        selectors.add(
            StrategySelector(
                getNextSelectorPriority(),
                "randomBack",
                StrategySelector.randomBack,
                bundle = arrayOf(probability, Random(randomSeed))
            )
        )
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

    fun exploreRandomly(
        randomSeed: Long = 0,
        delay: Long = 0,
        enableScroll: Boolean = false,
        biasedRandom: Boolean = false
    ): ExploreCommandBuilder {
        return addRandomStrategy(randomSeed, delay, enableScroll, biasedRandom)
            .addRandomExploreSelector()
    }

    fun addRandomStrategy(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return addRandomStrategy(
            cfg.randomSeed,
            cfg[ConfigProperties.Exploration.widgetActionDelay],
            cfg[ConfigProperties.Strategies.Parameters.biasedRandom],
            cfg[ConfigProperties.Strategies.Parameters.randomScroll]
        )
    }

    @JvmOverloads
    fun addRandomStrategy(
        randomSeed: Long = 0,
        delay: Long = 0,
        enableScroll: Boolean = false,
        biasedRandom: Boolean = false
    ): ExploreCommandBuilder {

        strategies.add(RandomWidget(randomSeed, biasedRandom, enableScroll, delay = delay))

        return this
    }

    fun addRandomExploreSelector(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "randomWidget", StrategySelector.randomWidget))
        return this
    }

    fun allowRuntimePermissions(): ExploreCommandBuilder {
        addAllowPermissionStrategy()
        addAllowPermissionSelector()
        return this
    }

    fun addAllowPermissionStrategy(): ExploreCommandBuilder {
        strategies.add(AllowRuntimePermission())
        return this
    }

    fun addAllowPermissionSelector(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "allowPermission", StrategySelector.allowPermission))
        return this
    }

    fun denyRuntimePermissions(): ExploreCommandBuilder {
        addDenyPermissionStrategy()
        addDenyPermissionSelector()
        return this
    }

    fun addDenyPermissionStrategy(): ExploreCommandBuilder {
        strategies.add(DenyRuntimePermission())
        return this
    }

    fun addDenyPermissionSelector(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "denyPermission", StrategySelector.denyPermission))
        return this
    }

    fun addRotateUIStrategy(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return addRotateUIStrategy(cfg[ConfigProperties.Strategies.Parameters.uiRotation])
    }

    fun addRotateUIStrategy(uiRotation: Int): ExploreCommandBuilder {
        strategies.add(RotateUI(uiRotation))
        return this
    }

    fun addMinimizeMaximizeStrategy(): ExploreCommandBuilder {
        strategies.add(MinimizeMaximize())

        return this
    }

    fun withPlayback(cfg: ConfigurationWrapper): ExploreCommandBuilder {
        return withPlayback(cfg.getPath(cfg[ConfigProperties.Selectors.playbackModelDir]))
    }

    fun withPlayback(playbackModelDir: Path): ExploreCommandBuilder {
        return addPlaybackStrategy(playbackModelDir)
            .addPlaybackSelector()
    }

    fun addPlaybackStrategy(playbackModelDir: Path): ExploreCommandBuilder {
        strategies.add(Playback(playbackModelDir.toAbsolutePath()))
        return this
    }

    fun addPlaybackSelector(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "playback", StrategySelector.playback))
        return this
    }

    fun usingDFS(): ExploreCommandBuilder {
        return addDFSStrategy()
            .addDFSSelector()
    }

    fun addDFSStrategy(): ExploreCommandBuilder {
        strategies.add(DFS())
        return this
    }

    fun addDFSSelector(): ExploreCommandBuilder {
        selectors.add(StrategySelector(getNextSelectorPriority(), "dfs", StrategySelector.dfs))
        return this
    }

    fun collectStatementCoverage(): ExploreCommandBuilder {
        selectors.add(
            StrategySelector(
                getNextSelectorPriority(),
                "statementCoverageSync",
                StrategySelector.statementCoverage
            )
        )
        return this
    }

    fun withStrategy(strategy: ISelectableExplorationStrategy): ExploreCommandBuilder {
        strategies.add(strategy)
        return this
    }

    fun withSelector(selector: StrategySelector): ExploreCommandBuilder {
        selectors.add(selector)
        return this
    }

    fun remove(selector: SelectorFunction): ExploreCommandBuilder {
        val target = selectors.firstOrNull { it.selector == selector }

        if (target != null) {
            selectors.remove(target)
        }
        return this
    }

    @JvmOverloads
    fun append(
        newDescription: String,
        newSelector: SelectorFunction,
        bundle: Array<Any> = emptyArray()
    ): ExploreCommandBuilder {
        val priority = selectors.maxBy { it.priority }?.priority ?: selectors.size

        selectors.add(StrategySelector(priority + 1, newDescription, newSelector, bundle = bundle))

        return this
    }

    @JvmOverloads
    fun insertBefore(
        oldSelector: SelectorFunction,
        newDescription: String,
        newSelector: SelectorFunction,
        bundle: Array<Any> = emptyArray()
    ): ExploreCommandBuilder {
        val targetPriority = selectors.firstOrNull { it.selector == oldSelector }?.priority

        if (targetPriority != null) {
            selectors.add(StrategySelector(targetPriority - 1, newDescription, newSelector, bundle = bundle))
        } else {
            append(newDescription, newSelector, bundle)
        }

        return this
    }

    @JvmOverloads
    open fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> build(
        cfg: ConfigurationWrapper,
        deviceTools: IDeviceTools = DeviceTools(cfg),
        strategyProvider: (ExplorationContext<*, *, *>) -> IExplorationStrategy = {
            ExplorationStrategyPool(
                this.strategies,
                this.selectors,
                it
            )
        }, //FIXME is it really still useful to overwrite the eContext instead of the model?
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