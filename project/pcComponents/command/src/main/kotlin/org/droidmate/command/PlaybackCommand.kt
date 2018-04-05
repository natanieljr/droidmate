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
package org.droidmate.command

import org.droidmate.configuration.Configuration
import org.droidmate.exploration.AbstractContext
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.exploration.strategy.IExplorationStrategy
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.exploration.strategy.playback.MemoryPlayback
import org.droidmate.misc.ITimeProvider
import org.droidmate.misc.TimeProvider
import org.droidmate.report.Reporter
import org.droidmate.report.apk.playback.ReproducibilityRate
import org.droidmate.report.apk.playback.TraceDump
import org.droidmate.storage.Storage2
import org.droidmate.tools.*
import java.nio.file.Files
import java.nio.file.Paths

class PlaybackCommand(apksProvider: IApksProvider,
					  deviceDeployer: IAndroidDeviceDeployer,
					  apkDeployer: IApkDeployer,
					  timeProvider: ITimeProvider,
					  strategyProvider: (AbstractContext) -> IExplorationStrategy,
					  cfg: Configuration,
					  context: AbstractContext?) : ExploreCommand(apksProvider, deviceDeployer, apkDeployer,
		timeProvider, strategyProvider, cfg, context) {
	companion object {
		private lateinit var playbackStrategy: MemoryPlayback

		private fun getExplorationStrategy(strategies: List<ISelectableExplorationStrategy>, selectors: List<StrategySelector>,
										   context: AbstractContext): IExplorationStrategy {
			val strategiesWithPlayback = strategies.toMutableList().also { it.add(playbackStrategy) }

			val selectorsWithPlayback = selectors.toMutableList()//.also { // TODO Instantiate playback selectors, waiting Jenny implementation }
			val pool = ExplorationStrategyPool(strategiesWithPlayback, selectorsWithPlayback, context)

			pool.registerStrategy(playbackStrategy)

			return pool
		}

		fun build(cfg: Configuration, // TODO initialize pool according to strategies parameter and allow for Model parameter for custom/shared model experiments
				  deviceTools: IDeviceTools = DeviceTools(cfg),
				  timeProvider: ITimeProvider = TimeProvider(),
				  strategies: List<ISelectableExplorationStrategy> = getDefaultStrategies(cfg),
				  selectors: List<StrategySelector> = getDefaultSelectors(cfg),
				  strategyProvider: (AbstractContext) -> IExplorationStrategy = { getExplorationStrategy(strategies, selectors, it) },
				  reportCreators: List<Reporter> = defaultReportWatcher(cfg),
				  context: AbstractContext? = null): PlaybackCommand {
			val apksProvider = ApksProvider(deviceTools.aapt)

			val command = PlaybackCommand(apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer,
					timeProvider, strategyProvider, cfg, context)

			val storedLogFile = Paths.get(cfg.playbackFile).toAbsolutePath()
			assert(Files.exists(storedLogFile), { "Stored exploration log $storedLogFile not found." })

			log.info("Loading stored exploration log from $storedLogFile")
			val storedLog = Storage2(storedLogFile.parent).deserialize(storedLogFile)
			playbackStrategy = MemoryPlayback(storedLog)

			reportCreators.forEach { r -> command.registerReporter(r) }

			command.registerReporter(ReproducibilityRate(playbackStrategy))
			command.registerReporter(TraceDump(playbackStrategy))

			return command
		}
	}
}