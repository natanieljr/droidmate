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

package org.droidmate.tests_misc

import org.droidmate.test_tools.DroidmateTestCase
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class LogbackTest : DroidmateTestCase()
{
  /*companion object {
      @JvmStatic
      val fileAppenderName = "FileAppenderName"

      @JvmStatic
      private fun createLoggerWithFileAppender(loggerName: String, fileName: String, lazyFileAppender: Boolean): Logger {

          val lc = LoggerFactory.getILoggerFactory() as LoggerContext
          val ple = createAndSetupPatternLayoutEncoder(lc)
          val fileAppender = createAndSetupFileAppender(fileName, ple, lc, lazyFileAppender)

          val logger = LoggerFactory.getLogger(loggerName) as Logger
          logger.addAppender(fileAppender)
          logger.level = Level.DEBUG
          logger.isAdditive = false /* set to true if root should log too */

          return logger
      }

      @JvmStatic
      private fun createAndSetupPatternLayoutEncoder(lc: LoggerContext): PatternLayoutEncoder {
          val ple = PatternLayoutEncoder()

          ple.pattern = "%date %level [%thread] %logger{10} [%file:%line] %msg%n"
          ple.context = lc
          ple.start()
          return ple
      }

      @JvmStatic
      private fun createAndSetupFileAppender(fileName: String, ple: PatternLayoutEncoder,
                                             lc: LoggerContext, lazy: Boolean): OutputStreamAppender<ILoggingEvent>
      {
          val fileAppender : OutputStreamAppender<ILoggingEvent> = if (lazy)
              LazyFileAppender<ILoggingEvent>()
          else
              FileAppender<ILoggingEvent>()

          fileAppender.name = fileAppenderName
          fileAppender.setFile("${LogbackConstants.LOGS_DIR_PATH}${File.separator}" + fileName)
          fileAppender.setEncoder(ple)
          fileAppender.context = lc
          if (lazy)
              fileAppender.setLazy(true)
          fileAppender.start()

          return fileAppender
      }

      @JvmStatic
      fun measureTime(name: String, iterations: Int, computation: () -> Any) {
          val stopwatch = Stopwatch.createStarted()

          for (i in 1..iterations)
              computation()

          stopwatch.stop()

          println("Measured seconds for $name: " + stopwatch.elapsed(TimeUnit.MILLISECONDS) / 1000)
      }
  }

  @Test
  fun t1_rootLoggerHasStdAppenders() {
      val log = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
      val appenders = log.iteratorForAppenders().asSequence().toList()

      assert(LogbackAppenders.appender_stdout in appenders.map { it.name })
  }

  @Test
  fun t2_performanceTest()
  {
    val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    val entry = "t"

    measureTime("plain log", 1000) {logger.debug("The new entry is $entry.")}
    measureTime("fast log", 1000) {logger.debug("The new entry is {}.", entry)}
    measureTime("plain groovy log", 1000) {LoggedClass.useLog("The new entry is $entry.")}
    measureTime("fast groovy log", 1000) {LoggedClass.useLog("The new entry is {}.", entry)}
  }

  @Test
  fun t3_creatingLoggerAndAppender()
  {
    val foo = createLoggerWithFileAppender("foo", "foo.log", false)
    val bar = createLoggerWithFileAppender("bar", "bar.log", false)
    foo.info("test")
    bar.info("bar")
  }

  @SuppressWarnings("GroovyAssignabilityCheck")
  @Test
  fun t4_lazyFileAppenderCreatesFileLazily() {
      val logger = createLoggerWithFileAppender ("quxLogger", "qux.log", true)
      val fileAppender = logger.getAppender (fileAppenderName)

      assert(!(File(fileAppender..file).exists()))
      logger.info("something")
      assert new File(fileAppender.file).exists()

  }

  static class LoggedClass
  {
    static useLog(String msg)
    {
      log.debug(msg)
    }

     static useLog(String format, Object args)
    {
      log.debug(format, args)
    }
  }*/


}
