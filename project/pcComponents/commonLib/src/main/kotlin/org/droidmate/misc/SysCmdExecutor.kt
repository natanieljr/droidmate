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

import com.google.common.base.Stopwatch
import org.apache.commons.exec.*
import org.droidmate.logging.Markers
import org.droidmate.misc.ISysCmdExecutor.Companion.getExecutionTimeMsg
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class SysCmdExecutor : ISysCmdExecutor {
	companion object {
		private val log by lazy { LoggerFactory.getLogger(SysCmdExecutor::class.java) }
	}

	/** Timeout for executing system commands, in milliseconds. Zero or negative value means no timeout. */
	// App that often requires more than one minute for "adb start": net.zedge.android_v4.10.2-inlined.apk
	private val sysCmdExecuteTimeout = 1000 * 60 * 2

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
		assert(cmdLineParams.isNotEmpty()) { "At least one command line parameters has to be given, denoting the executable." }

		val params = cmdLineParams.toList().toTypedArray()

		// It is recommended to build the command incrementally using .addArgument(..)
		// rather than using CommandLine.parse(..)
		val command = CommandLine(params[0])
		for (param in params.drop(1).toTypedArray()) {
			command.addArgument(param, false)
		}

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
		log.trace(command.toString())
		log.info(Markers.osCmd, command.toString())

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
					getExecutionTimeMsg(executionTimeStopwatch, timeout, e.exitValue, commandDescription),
					if (processStdoutStream.toString().isNotEmpty()) processStdoutStream.toString() else "<stdout is empty>",
					if (processStderrStream.toString().isNotEmpty()) processStderrStream.toString() else "<stderr is empty>"),
					e)

		} catch (e: IOException) {
			throw SysCmdExecutorException(String.format("Failed to execute a system command.\n"
					+ "Command: %s\n"
					+ "Captured stdout: %s\n"
					+ "Captured stderr: %s",
					command.toString(),
					if (processStdoutStream.toString().isNotEmpty()) processStdoutStream.toString() else "<stdout is empty>",
					if (processStderrStream.toString().isNotEmpty()) processStderrStream.toString() else "<stderr is empty>"),
					e)
		} finally {
			log.trace("Captured stdout:")
			log.trace(processStdoutStream.toString())

			log.trace("Captured stderr:")
			log.trace(processStderrStream.toString())
		}
		log.trace("Captured exit value: $exitValue")
		log.trace("DONE executing system command")

		return arrayOf(processStdoutStream.toString(), processStderrStream.toString())
	}
}