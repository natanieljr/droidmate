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
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

interface ISysCmdExecutor {

	companion object {
		private val log = LoggerFactory.getLogger(SysCmdInterruptableExecutor::class.java)
		private val TIMEOUT_REACHED_ZONE = 100

		fun getExecutionTimeMsg(executionTimeStopwatch: Stopwatch, timeout: Int, exitValue: Int, commandDescription: String): String {
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

	@Throws(SysCmdExecutorException::class)
	fun execute(commandDescription: String, vararg cmdLineParams: String): Array<String>

	@Throws(SysCmdExecutorException::class)
	fun executeWithoutTimeout(commandDescription: String, vararg cmdLineParams: String): Array<String>

	/**
	 * @timeout timout for the process in milli seconds
	 */
	@Throws(SysCmdExecutorException::class)
	fun executeWithTimeout(commandDescription: String, timeout: Int, vararg cmdLineParams: String): Array<String>
}