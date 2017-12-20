// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.frontend

import org.droidmate.command.DroidmateCommand
import org.droidmate.configuration.Configuration
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.logging.LogbackConstants
import org.droidmate.logging.LogbackConstants.Companion.system_prop_stdout_loglevel
import org.droidmate.logging.LogbackUtilsRequiringLogbackLog
import org.droidmate.logging.Markers
import org.droidmate.logging.Markers.Companion.runData
import org.droidmate.misc.DroidmateException
import org.slf4j.LoggerFactory
import java.io.File

import java.nio.file.FileSystem
import java.nio.file.FileSystems
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
        private val log = LoggerFactory.getLogger(DroidmateFrontend::class.java)

        /**
         * @see DroidmateFrontend
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val exitStatus = main(args, null)
            System.exit(exitStatus)
        }

        @JvmStatic
        @JvmOverloads
        fun main(args: Array<String>,
                 commandProvider: ICommandProvider?,
                 fs: FileSystem = FileSystems.getDefault(),
                 exceptionHandler: IExceptionHandler = ExceptionHandler(),
                 receivedCfg: Configuration? = null): Int {
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
                log.info("Bootstrapping DroidMate: building ${Configuration::class.java.simpleName} from args " +
                        "and instantiating objects for ${DroidmateCommand::class.java.simpleName}.")
                log.info("IMPORTANT: for help on how to configure DroidMate, run it with -help")
                log.info("IMPORTANT: for detailed logs from DroidMate run, please see ${LogbackConstants.LOGS_DIR_PATH}.")

                val cfg = receivedCfg ?: ConfigurationBuilder().build(args, fs)

                val command = commandProvider?.provide(cfg) ?: determineAndBuildCommand(cfg)

                log.info("Successfully instantiated ${command.javaClass.simpleName}. Welcome to DroidMate. Lie back, relax and enjoy.")
                log.info("Run start timestamp: " + runStart)
                log.info("Running in Android $cfg.androidApi compatibility mode (api23+ = version 6.0 or newer).")

                command.execute(cfg)

            } catch (e: Throwable) {
                exitStatus = exceptionHandler.handle(e)
            }

            logDroidmateRunEnd(runStart, /* boolean encounteredExceptionsDuringTheRun = */ exitStatus > 0)
            return exitStatus
        }

        @JvmStatic
        private fun determineAndBuildCommand(cfg: Configuration): DroidmateCommand
                = DroidmateCommand.build(cfg.report, cfg.inline, cfg.unpack, cfg)

        @JvmStatic
        private fun validateStdoutLogLevel() {
            if (!System.getProperties().contains(system_prop_stdout_loglevel))
                return

            if (System.getProperty(system_prop_stdout_loglevel).toUpperCase() !in arrayListOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR"))
                throw DroidmateException("The $system_prop_stdout_loglevel environment variable has to be set to TRACE, " +
                        "DEBUG, INFO, WARN or ERROR. Instead, it is set to ${System.getProperty(system_prop_stdout_loglevel)}.")
        }

        private fun logDroidmateRunEnd(runStart: Date, encounteredExceptionsDuringTheRun: Boolean) {
            val runEnd = Date()
            val diffInMillis = runEnd.time - runStart.time
            val runDuration = TimeUnit.MINUTES.convert(diffInMillis, TimeUnit.MILLISECONDS)
            val timestampFormat = "yyyy MMM dd HH:mm:ss"

            if (encounteredExceptionsDuringTheRun)
                log.warn(Markers.appHealth,
                        "DroidMate run finished, but some exceptions have been thrown and handled during the run. See previous logs for details.")
            else
                log.info("DroidMate run finished successfully.")

            log.info("Run finish timestamp: ${runEnd.toString().format(timestampFormat)}. DroidMate ran for $runDuration.")
            log.info("By default, the results from the run can be found in .${File.separator}${Configuration.defaultDroidmateOutputDir} directory.")
            log.info("By default, for detailed diagnostics logs from the run, see ${LogbackConstants.LOGS_DIR_PATH} directory.")

            log.info(runData, "Run start  timestamp: ${runStart.toString().format(timestampFormat)}")
            log.info(runData, "Run finish timestamp: ${runEnd.toString().format(timestampFormat)}")
            log.info(runData, "DroidMate ran for: $runDuration")
        }
    }
}
