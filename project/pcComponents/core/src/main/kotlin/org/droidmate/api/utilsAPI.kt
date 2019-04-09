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

package org.droidmate.api

import org.droidmate.command.DroidmateCommand
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.logging.LogbackUtilsRequiringLogbackLog
import org.slf4j.LoggerFactory
import java.time.LocalDate

private val log by lazy { LoggerFactory.getLogger("API-Command") }

internal fun setup(args: Array<String>): ConfigurationWrapper {
	println(copyRight)

	LogbackUtilsRequiringLogbackLog.cleanLogsDir()  // FIXME this logPath crap should use our config properties
	log.info("Bootstrapping DroidMate: building ${ConfigurationWrapper::class.java.simpleName} from args " +
			"and instantiating objects for ${DroidmateCommand::class.java.simpleName}.")
	log.info("IMPORTANT: for help on how to configure DroidMate, run it with --help")

	return ExplorationAPI.config(args)
}

private val copyRight = """ |DroidMate, an automated execution generator for Android apps.
                  |Copyright (c) 2012 - ${LocalDate.now().year} Saarland University
                  |This program is free software licensed under GNU GPL v3.
                  |
                  |You should have received a copy of the GNU General Public License
                  |along with this program.  If not, see <http://www.gnu.org/licenses/>.
                  |
                  |email: jamrozik@st.cs.uni-saarland.de
                  |web: www.droidmate.org""".trimMargin()

