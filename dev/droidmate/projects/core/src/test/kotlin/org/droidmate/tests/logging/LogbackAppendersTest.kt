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

package org.droidmate.tests.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.LoggerContextVO
import ch.qos.logback.core.Appender
import ch.qos.logback.core.spi.FilterReply
import org.droidmate.logging.LogbackAppenders.Companion.changeThresholdLevelOfFirstFilter
import org.droidmate.logging.LogbackAppenders.Companion.getStdStreamsAppenders
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import org.slf4j.Marker

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class LogbackAppendersTest {
    @Test
    fun `Changes threshold level of first filter`() {
        val appenders = getStdStreamsAppenders()

        // Copy filters to be restored after test
        val filters = hashMapOf(*appenders.map { Pair(it, it.copyOfAttachedFiltersList) }.toTypedArray())

        appenders.forEach { appender ->
            assertFirstFilterIsThresholdAndRepliesToTraceWith(appender, FilterReply.DENY)

            // Act
            changeThresholdLevelOfFirstFilter(appender, Level.TRACE)
        }

        appenders.forEach { appender ->
            assertFirstFilterIsThresholdAndRepliesToTraceWith(appender, FilterReply.NEUTRAL)
        }

        // Restore the filters
        appenders.forEach {
            it.clearAllFilters()
            filters[it].orEmpty().forEach { filter -> it.addFilter(filter) }
        }
    }

    //region Helper methods
    private fun assertFirstFilterIsThresholdAndRepliesToTraceWith(appender: Appender<ILoggingEvent>, expectedReply: FilterReply) {
        val filters = appender.copyOfAttachedFiltersList

        assert(filters[0] is ThresholdFilter)
        val thresholdFilter = filters[0] as ThresholdFilter

        val filterReply = thresholdFilter.decide(CustomLogginEvent())
        assert(filterReply == expectedReply)
    }
    //endregion

    class CustomLogginEvent : ILoggingEvent {
        override fun getMessage(): String {
            throw NotImplementedError()
        }

        override fun getTimeStamp(): Long {
            throw NotImplementedError()
        }

        override fun getThreadName(): String {
            throw NotImplementedError()
        }

        override fun getArgumentArray(): Array<Any> {
            throw NotImplementedError()
        }

        override fun getMarker(): Marker {
            throw NotImplementedError()
        }

        @Suppress("OverridingDeprecatedMember")
        override fun getMdc(): MutableMap<String, String> {
            throw NotImplementedError()
        }

        override fun getMDCPropertyMap(): MutableMap<String, String> {
            throw NotImplementedError()
        }

        override fun getLoggerName(): String {
            throw NotImplementedError()
        }

        override fun getFormattedMessage(): String {
            throw NotImplementedError()
        }

        override fun prepareForDeferredProcessing() {
            throw NotImplementedError()
        }

        override fun getThrowableProxy(): IThrowableProxy {
            throw NotImplementedError()
        }

        override fun hasCallerData(): Boolean {
            throw NotImplementedError()
        }

        override fun getLoggerContextVO(): LoggerContextVO {
            throw NotImplementedError()
        }

        override fun getCallerData(): Array<StackTraceElement> {
            throw NotImplementedError()
        }

        override fun getLevel(): Level = Level.TRACE
    }
}
