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

package org.droidmate.frontend

import org.droidmate.command.DroidmateCommand
import org.droidmate.configuration.ConfigProperties.Deploy.installApk
import org.droidmate.configuration.ConfigProperties.Deploy.installAux
import org.droidmate.configuration.ConfigProperties.Exploration.apiVersion
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.logging.LogbackConstants
import org.droidmate.logging.LogbackConstants.Companion.system_prop_stdout_loglevel
import org.droidmate.logging.LogbackUtilsRequiringLogbackLog
import org.droidmate.logging.Markers
import org.droidmate.logging.Markers.Companion.runData
import org.droidmate.misc.DroidmateException
import org.slf4j.LoggerFactory

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * <p>
 * Entry class of DroidMate. This class should be supplied to the JVM on the command line as the entry class
 * (i.e. the class containing the {@code main} method).
 * </p>
 */
class DroidmateFrontend {
	companion object {
		/**
		 * Load the loggers lazily so the initialization of logback is postponed.
		 * If the appropriate arguments are passed, the logger directory can be set
		 * beforehand and logback puts the logs into the desired directory.
		 */
		private val log by lazy { LoggerFactory.getLogger(DroidmateFrontend::class.java) }

		/**
		 * @see DroidmateFrontend
		 */
		@JvmStatic
		fun main(args: Array<String>) {
			val currArgs = if (args.isEmpty()) {
				println("Parameters not provided. Trying to read from args.txt file.")
				val argsFile = Paths.get("args.txt")

				if (Files.exists(argsFile))
					Files.readAllLines(argsFile)
							.joinToString(" ")
							.split(" ")
							.filter { it.isNotEmpty() }
							.toTypedArray()
				else
					emptyArray()
			} else
				args

			val exitStatus = execute(currArgs)
			System.exit(exitStatus)
		}

		@Suppress("MemberVisibilityCanBePrivate")
		@JvmStatic
		@JvmOverloads
		fun execute(args: Array<String>,
		            commandProvider: (ConfigurationWrapper) -> DroidmateCommand = { determineAndBuildCommand(it) },
		            fs: FileSystem = FileSystems.getDefault(),
		            exceptionHandler: IExceptionHandler = ExceptionHandler(),
		            cfg: ConfigurationWrapper = ConfigurationBuilder().build(args, fs)): Int {
			println("DroidMate, an automated execution generator for Android apps.")
			println("Copyright (c) 2012 - ${LocalDate.now().year} Konrad Jamrozik")
			println("This program is free software licensed under GNU GPL v3.")
			println("")
			println("You should have received a copy of the GNU General Public License")
			println("along with this program.  If not, see <http://www.gnu.org/licenses/>.")
			println("")
			println("email: jamrozik@st.cs.uni-saarland.de")
			println("web: www.droidmate.org")

			var exitStatus = 0
			val runStart = Date()

			try {
				validateStdoutLogLevel()
				LogbackUtilsRequiringLogbackLog.cleanLogsDir()
				log.info("Bootstrapping DroidMate: building ${ConfigurationWrapper::class.java.simpleName} from args " +
						"and instantiating objects for ${DroidmateCommand::class.java.simpleName}.")
				log.info("IMPORTANT: for help on how to configure DroidMate, run it with -help")
				log.info("IMPORTANT: for detailed logs from DroidMate run, please see ${LogbackConstants.LOGS_DIR_PATH}.")

				if (!cfg[installApk])
					log.warn("DroidMate will not reinstall the target APK(s). If the APK(s) are not previously installed on the device the exploration will fail.")

				if (!cfg[installAux])
					log.warn("DroidMate will not reinstall its auxiliary components (UIAutomator and Monitor). If the they are not previously installed on the device the exploration will fail.")

				val command = commandProvider.invoke(cfg)

				log.info("Successfully instantiated ${command.javaClass.simpleName}. Welcome to DroidMate. Lie back, relax and enjoy.")
				log.info("Run start timestamp: $runStart")
				log.info("Running in Android ${cfg[apiVersion]} compatibility mode (api23+ = version 6.0 or newer).")

				command.execute(cfg)

			} catch (e: Throwable) {
				exitStatus = exceptionHandler.handle(e)
			}

			logDroidmateRunEnd(runStart, /* boolean encounteredExceptionsDuringTheRun = */ exitStatus > 0, cfg)
			return exitStatus
		}

		@JvmStatic
		private fun determineAndBuildCommand(cfg: ConfigurationWrapper): DroidmateCommand = DroidmateCommand.build(cfg)

		@JvmStatic
		private fun validateStdoutLogLevel() {
			if (!System.getProperties().contains(system_prop_stdout_loglevel))
				return

			if (System.getProperty(system_prop_stdout_loglevel).toUpperCase() !in arrayListOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR"))
				throw DroidmateException("The $system_prop_stdout_loglevel environment variable has to be set to TRACE, " +
						"DEBUG, INFO, WARN or ERROR. Instead, it is set to ${System.getProperty(system_prop_stdout_loglevel)}.")
		}

		private fun logDroidmateRunEnd(runStart: Date, encounteredExceptionsDuringTheRun: Boolean, cfg: ConfigurationWrapper) {
			val runEnd = Date()
			val diffInMillis = runEnd.time - runStart.time
			val runDuration = TimeUnit.SECONDS.convert(diffInMillis, TimeUnit.MILLISECONDS)
			val timestampFormat = "yyyy MMM dd HH:mm:ss"

			if (encounteredExceptionsDuringTheRun)
				log.warn(Markers.appHealth,
						"DroidMate run finished, but some exceptions have been thrown and handled during the run. See previous logs for details.")
			else
				log.info("DroidMate run finished successfully.")

			log.info("Run finish timestamp: ${runEnd.toString().format(timestampFormat)}. DroidMate ran for $runDuration sec.")
			log.info("The results from the run can be found in ${cfg.droidmateOutputDirPath} directory.")
			log.info("By default, for detailed diagnostics logs from the run, see ${LogbackConstants.LOGS_DIR_PATH} directory.")

			log.info(runData, "Run start  timestamp: ${runStart.toString().format(timestampFormat)}")
			log.info(runData, "Run finish timestamp: ${runEnd.toString().format(timestampFormat)}")
			log.info(runData, "DroidMate ran for: $runDuration sec")
		}
	}
}
