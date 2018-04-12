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
package org.droidmate

import org.droidmate.command.DroidmateCommand
import org.droidmate.command.ExploreCommand
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.exploration.strategy.Back
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.exploration.strategy.Reset
import org.droidmate.exploration.strategy.Terminate
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.frontend.ExceptionHandler
import org.droidmate.report.Reporter
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.time.LocalDate
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
object ExplorationAPI {
	private val log = LoggerFactory.getLogger(ExplorationAPI::class.java)

	/**
	 * entry-point to explore an application with a (subset) of default exploration strategies
	 * 1. inline the apks in the directory if they do not end on '-inlined.apk'
	 * 2. run the exploration with the strategies listed in the property 'explorationStrategies'
	 */
	@JvmStatic  // -config project/pcComponents/API/src/main/resources/customConfig.properties
	fun main(args: Array<String>) { // e.g.`-config filePath` or `--configPath=filePath`
		inlineAndExplore(args)
	}

	@JvmStatic
	fun inlineAndExplore(args: Array<String>, strategies: List<ISelectableExplorationStrategy> = listOf(RandomWidget(0), Terminate(), Back(), Reset()), reportCreators: List<Reporter> = emptyList()) {
		var exitStatus = 0

		try {
			println(copyRight)

//			LogbackUtilsRequiringLogbackLog.cleanLogsDir()  // FIXME this logPath crap should use our config properties
			log.info("Bootstrapping DroidMate: building ${org.droidmate.configuration.ConfigurationWrapper::class.java.simpleName} from args " +
					"and instantiating objects for ${DroidmateCommand::class.java.simpleName}.")
			log.info("IMPORTANT: for help on how to configure DroidMate, run it with --help")

//			log.info("inline the apks if necessary")
			val cfg = ConfigurationBuilder().build(args, FileSystems.getDefault())//ConfigurationWrapper(config, FileSystems.getDefault())
//			InlineCommand().execute(cfg)

			val runStart = Date()
			val command = ExploreCommand.build(cfg, reportCreators = reportCreators, strategies = strategies)
			log.info("EXPLORATION start timestamp: $runStart")
			log.info("Running in Android $cfg.androidApi compatibility mode (api23+ = version 6.0 or newer).")

			command.execute(cfg)

		} catch (e: Throwable) {
			e.printStackTrace()
			exitStatus = ExceptionHandler().handle(e)
		}
		System.exit(exitStatus)
	}
}

val copyRight = """ |DroidMate, an automated execution generator for Android apps.
                  |Copyright (c) 2012 - ${LocalDate.now().year} Saarland University
                  |This program is free software licensed under GNU GPL v3.
                  |
                  |You should have received a copy of the GNU General Public License
                  |along with this program.  If not, see <http://www.gnu.org/licenses/>.
                  |
                  |email: jamrozik@st.cs.uni-saarland.de
                  |web: www.droidmate.org""".trimMargin()
