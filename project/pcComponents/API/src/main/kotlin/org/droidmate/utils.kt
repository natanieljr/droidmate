package org.droidmate

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

