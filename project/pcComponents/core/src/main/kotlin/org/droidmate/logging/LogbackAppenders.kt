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

package org.droidmate.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import org.slf4j.LoggerFactory

@Suppress("RedundantVisibilityModifier", "MemberVisibilityCanPrivate")
class LogbackAppenders {
	companion object {
		val appender_stdout = "appender_STDOUT"
		val appender_stderr = "appender_STDERR"

		public fun stdStreamsAppenders(): List<String> = arrayListOf(appender_stdout, appender_stderr)

		public fun setThresholdLevelOfStdStreamsAppenders(level: Level) {
			getStdStreamsAppenders().forEach { it -> changeThresholdLevelOfFirstFilter(it, level) }
		}

		@JvmStatic
		fun getStdStreamsAppenders(): List<Appender<ILoggingEvent>> {
			val log = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)

			if (log is Logger) {
				val appenders = log.iteratorForAppenders()
						.asSequence()
						.filter { appender -> appender.name in stdStreamsAppenders() }
						.toList()
				return appenders
			} else {
				return emptyList()
			}
		}

		// Adapted from http://groovy.codehaus.org/JN3515-Interception
		// WARNING: possibly (not sure) this doesn't work on log methods residing in @Memoized method. Dunno why, AST transformation magic interferes?
		@JvmStatic
		fun changeThresholdLevelOfFirstFilter(appender: Appender<ILoggingEvent>, newLevel: Level) {
			val filters = appender.copyOfAttachedFiltersList

			val thresholdFilter = filters[0] as ThresholdFilter
			thresholdFilter.setLevel(newLevel.toString())

			appender.clearAllFilters()
			filters.forEach { it -> appender.addFilter(it) }
		}
	}
}
