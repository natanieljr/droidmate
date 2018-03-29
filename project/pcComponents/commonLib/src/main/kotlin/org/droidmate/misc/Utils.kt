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

import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.reflect.KClass

class Utils {
	companion object {
		private val log = LoggerFactory.getLogger(Utils::class.java)

		@JvmStatic
		@Throws(Throwable::class)
		fun <T> retryOnException(target: () -> T,
		                         beforeRetryCommand: () -> Any,
		                         retryableExceptionClass: KClass<out DroidmateException>,
		                         attempts: Int,
		                         delay: Int,
		                         targetName: String): T {
			assert(attempts > 0)
			var attemptsLeft = attempts
			var succeeded = false
			var exception: Throwable? = null
			var out: T? = null


			while (!succeeded && attemptsLeft > 0) {
				try {
					out = target.invoke()
					succeeded = true
					exception = null
				} catch (e: Throwable) {
					if (retryableExceptionClass.java.isAssignableFrom(e.javaClass)) {

						beforeRetryCommand()

						exception = e
						attemptsLeft--

						if (attemptsLeft > 0) {
							log.trace("Discarded $e from \"$targetName\". Sleeping for $delay and retrying.")
							Thread.sleep(delay.toLong())
						} else
							log.trace("Discarded $e from \"$targetName\". Giving up.")
					} else
						throw e
				}
			}

			if (succeeded) {
				assert(exception == null)
				return out!!
			} else {
				assert(exception != null)
				throw exception!!
			}
		}

		@JvmStatic
		@Throws(Throwable::class)
		fun retryOnFalse(target: () -> Boolean, attempts: Int, delay: Int): Boolean {
			assert(attempts > 0)
			var attemptsLeft = attempts

			var succeeded = target.invoke()
			attemptsLeft--
			while (!succeeded && attemptsLeft > 0) {
				Thread.sleep(delay.toLong())
				succeeded = target.invoke()
				attemptsLeft--
			}

			assert((attemptsLeft <= 0) || succeeded)
			return succeeded
		}

		@JvmStatic
		fun quoteIfIsPathToExecutable(path: String): String {
			if (SystemUtils.IS_OS_WINDOWS) {
				if (Files.isExecutable(Paths.get(path)))
					return '"' + path + '"'
				else
					return path
			} else {
				return path
			}
		}

		@JvmStatic
		fun quoteAbsolutePaths(stringArray: Array<String>): Array<String> {
			stringArray.forEachIndexed { idx, it ->
				if (File(it).isAbsolute())
					stringArray[idx] = '"' + it + '"'
			}
			return stringArray
		}
	}
}
