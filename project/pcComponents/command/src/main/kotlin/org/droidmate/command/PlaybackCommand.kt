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

import org.droidmate.command.exploration.Exploration
import org.droidmate.command.exploration.IExploration
import org.droidmate.configuration.Configuration
import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.exploration.strategy.IExplorationStrategy
import org.droidmate.exploration.strategy.playback.MemoryPlayback
import org.droidmate.misc.ITimeProvider
import org.droidmate.misc.TimeProvider
import org.droidmate.report.Reporter
import org.droidmate.report.apk.playback.ReproducibilityRate
import org.droidmate.report.apk.playback.TraceDump
import org.droidmate.storage.IStorage2
import org.droidmate.storage.Storage2
import org.droidmate.tools.*
import java.nio.file.Files
import java.nio.file.Paths

class PlaybackCommand(apksProvider: IApksProvider,
                      deviceDeployer: IAndroidDeviceDeployer,
                      apkDeployer: IApkDeployer,
                      exploration: IExploration,
                      storage2: IStorage2) : ExploreCommand(apksProvider, deviceDeployer, apkDeployer, exploration, storage2) {
	companion object {
		private lateinit var playbackStrategy: MemoryPlayback

		private fun getExplorationStrategy(explorationLog: AbstractContext, cfg: Configuration): IExplorationStrategy {
			val pool = ExplorationStrategyPool.build(explorationLog, cfg)

			pool.registerStrategy(playbackStrategy)

			return pool
		}

		fun build(cfg: Configuration,
		          strategyProvider: (AbstractContext) -> IExplorationStrategy = { getExplorationStrategy(it, cfg) },
		          timeProvider: ITimeProvider = TimeProvider(),
		          deviceTools: IDeviceTools = DeviceTools(cfg),
		          reportCreators: List<Reporter> = defaultReportWatcher(cfg)): PlaybackCommand {
			val apksProvider = ApksProvider(deviceTools.aapt)

			val storage2 = Storage2(cfg.droidmateOutputDirPath)
			val exploration = Exploration.build(cfg, timeProvider, strategyProvider)

			val command = PlaybackCommand(apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer, exploration, storage2)

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