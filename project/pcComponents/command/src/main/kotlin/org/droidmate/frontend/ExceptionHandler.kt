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

import org.droidmate.device.android_sdk.ApkExplorationException
import org.droidmate.device.android_sdk.ExplorationException
import org.droidmate.configuration.Configuration
import org.droidmate.logging.LogbackConstants
import org.droidmate.logging.Markers.Companion.exceptions
import org.droidmate.misc.ThrowablesCollection
import org.slf4j.LoggerFactory

class ExceptionHandler : IExceptionHandler {
	override fun handle(e: Throwable): Int {
		val returnCode = internalHandle(e)
		log.error(LogbackConstants.err_log_msg)
		return returnCode
	}

	companion object {
		private val log = LoggerFactory.getLogger(ExceptionHandler::class.java)

		@JvmStatic
		private fun internalHandle(e: Throwable): Int {
			assert(e.suppressed.isEmpty())


			when (e) {
				is ApkExplorationException -> {
					logApkExplorationException(e)
					return 1
				}

				is ExplorationException -> {
					logExplorationException(e)
					return 2
				}

				is ThrowablesCollection -> {
					logThrowablesCollection(e)
					return 3
				}

				else -> {
					logThrowable(e)
					return 4
				}
			}
		}

		private fun logApkExplorationException(e: ApkExplorationException) {
			val message = "An ${e.javaClass.simpleName} was thrown during DroidMate run, pertaining to ${e.apk.fileName}:"

			log.error("$message $e")
			log.error(exceptions, "$message\n", e)
		}

		private fun logExplorationException(e: ExplorationException) {
			val message = "An ${e.javaClass.simpleName} was thrown during DroidMate run:"

			log.error("$message $e")
			log.error(exceptions, "$message\n", e)
		}

		private fun logThrowablesCollection(e: ThrowablesCollection) {
			assert(!(e.throwables.isEmpty()))
			assert(e.cause == null)
			assert(e.suppressed.isEmpty())
			assert(e.throwables.all { it is ExplorationException })

			val message = "A nonempty ${e.javaClass.simpleName} was thrown during DroidMate run. " +
					"Each of the ${e.throwables.size} ${Throwable::class.java.simpleName}s will now be logged."
			log.error(message)
			log.error(exceptions, message)

			val throwableDelimiter = "========================================"
			log.error(throwableDelimiter)
			log.error(exceptions, throwableDelimiter)
			e.throwables.forEach {
				internalHandle(it)
				log.error(throwableDelimiter)
				log.error(exceptions, throwableDelimiter)
			}
		}

		private fun logThrowable(e: Throwable) {
			val message = "An unhandled exception of ${e.javaClass.simpleName} was thrown during DroidMate run. If you cannot diagnose " +
					"and fix the problem yourself by inspecting the logs, this might a bug in the code. Sorry!\n" +
					"In such case, please contact the DroidMate developer, Konrad Jamrozik, at jamrozik@st.cs.uni-saarland.de.\n" +
					"Please include the output dir (by default set to ${Configuration.defaultDroidmateOutputDir}).\n" +
					"A cookie for you, brave human.\n"

			log.error("$message$e")
			log.error(exceptions, message, e)
		}
	}
}
