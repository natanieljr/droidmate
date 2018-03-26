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

package org.droidmate.misc

import com.google.common.base.Joiner
import com.google.common.base.Stopwatch
import org.apache.commons.exec.*
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class SysCmdExecutor : ISysCmdExecutor {
	companion object {
		private val log = LoggerFactory.getLogger(SysCmdExecutor::class.java)
		private val TIMEOUT_REACHED_ZONE = 100

		@JvmStatic
		private fun getExecutionTimeMsg(executionTimeStopwatch: Stopwatch, timeout: Int, exitValue: Int, commandDescription: String): String {
			val mills = executionTimeStopwatch.elapsed(TimeUnit.MILLISECONDS)
			val seconds = executionTimeStopwatch.elapsed(TimeUnit.SECONDS)

			// WISH here instead I could determine if the process was killed by watchdog with
			// org.apache.commons.exec.ExecuteWatchdog.killedProcess
			// For more, see comment of org.apache.commons.exec.ExecuteWatchdog
			if (mills >= (timeout - TIMEOUT_REACHED_ZONE) && mills <= (timeout + TIMEOUT_REACHED_ZONE)) {
				var returnedString = "$seconds seconds. The execution time was +- $TIMEOUT_REACHED_ZONE " +
						"milliseconds of the execution timeout."

				if (exitValue != 0)
					returnedString += " Reaching the timeout might be the cause of the process returning non-zero value." +
							" Try increasing the timeout (by changing appropriate cmd line parameter) or, if this doesn't help, " +
							"be aware the process might not be terminating at all."

				log.debug("The command with description \"$commandDescription\" executed for $returnedString")

				return returnedString
			}

			return "$seconds seconds"
		}
	}

	/** Timeout for executing system commands, in milliseconds. Zero or negative value means no timeout. */
	// App that often requires more than one minute for "adb start": net.zedge.android_v4.10.2-inlined.apk
	val sysCmdExecuteTimeout = 1000 * 60 * 2

	/*
 * References:
 * http://commons.apache.org/exec/apidocs/index.html
 * http://commons.apache.org/exec/tutorial.html
 * http://blog.sanaulla.info/2010/09/07/execute-external-process-from-within-jvm-using-apache-commons-exec-library/
 */

	override fun execute(commandDescription: String, vararg cmdLineParams: String): Array<String> {
		return executeWithTimeout(commandDescription, sysCmdExecuteTimeout, *cmdLineParams)
	}

	override fun executeWithoutTimeout(commandDescription: String, vararg cmdLineParams: String): Array<String> {
		return executeWithTimeout(commandDescription, -1, *cmdLineParams)
	}

	override fun executeWithTimeout(commandDescription: String, timeout: Int, vararg cmdLineParams: String): Array<String> {
		assert(cmdLineParams.isNotEmpty(), { "At least one command line parameters has to be given, denoting the executable." })

		val params = cmdLineParams.toList().toTypedArray()

		// If the command string to be executed is a file path to an executable (as opposed to plain command e.g. "java"),
		// then it should be quoted so spaces in it are handled properly.
		params[0] = Utils.quoteIfIsPathToExecutable(cmdLineParams[0])

		// If a parameter is an absolute path it might contain spaces in it and if yes, the parameter has to be quoted
		// to be properly interpreted.
		val quotedCmdLineParamsTail = Utils.quoteAbsolutePaths(params.drop(1).toTypedArray())

		// Prepare the command to execute.
		val commandLine = Joiner.on(" ").join(arrayListOf(cmdLineParams[0], *quotedCmdLineParamsTail))

		val command = CommandLine.parse(commandLine)

		// Prepare the process stdout and stderr listeners.
		val processStdoutStream = ByteArrayOutputStream()
		val processStderrStream = ByteArrayOutputStream()
		val pumpStreamHandler = PumpStreamHandler(processStdoutStream, processStderrStream)

		// Prepare the process executor.
		val executor = DefaultExecutor()

		executor.streamHandler = pumpStreamHandler

		if (timeout > 0) {
			// Attach the process timeout.
			val watchdog = ExecuteWatchdog(timeout.toLong())
			executor.watchdog = watchdog
		}

		// Only exit value of 0 is allowed for the call to return successfully.
		executor.setExitValue(0)

		log.trace(commandDescription)
		log.trace("Timeout: {} ms", timeout)
		log.trace("Command:")
		log.trace(commandLine)
		log.trace(Markers.osCmd, commandLine)

		val executionTimeStopwatch = Stopwatch.createStarted()

		val exitValue: Int
		try {
			exitValue = executor.execute(command)

		} catch (e: ExecuteException) {
			throw SysCmdExecutorException(String.format("Failed to execute a system command.\n"
					+ "Command: %s\n"
					+ "Captured exit value: %d\n"
					+ "Execution time: %s\n"
					+ "Captured stdout: %s\n"
					+ "Captured stderr: %s",
					command.toString(),
					e.exitValue,
					getExecutionTimeMsg(executionTimeStopwatch, timeout, e.getExitValue(), commandDescription),
					if (processStdoutStream.toString().isEmpty()) processStdoutStream.toString() else "<stdout is empty>",
					if (processStderrStream.toString().isEmpty()) processStderrStream.toString() else "<stderr is empty>"),
					e)

		} catch (e: IOException) {
			throw SysCmdExecutorException(String.format("Failed to execute a system command.\n"
					+ "Command: %s\n"
					+ "Captured stdout: %s\n"
					+ "Captured stderr: %s",
					command.toString(),
					if (processStdoutStream.toString().isEmpty()) processStdoutStream.toString() else "<stdout is empty>",
					if (processStderrStream.toString().isEmpty()) processStderrStream.toString() else "<stderr is empty>"),
					e)
		} finally {
			log.trace("Captured stdout:")
			log.trace(processStdoutStream.toString())

			log.trace("Captured stderr:")
			log.trace(processStderrStream.toString())
		}
		log.trace("Captured exit value: " + exitValue)
		log.trace("DONE executing system command")

		return arrayOf(processStdoutStream.toString(), processStderrStream.toString())
	}
}