package org.droidmate.tools

import kotlinx.coroutines.experimental.*
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.logging.LogbackUtils
import org.droidmate.logging.Markers
import org.droidmate.misc.SysCmdInterruptableExecutor
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.experimental.CoroutineContext

class LogcatMonitor(private val cfg: ConfigurationWrapper,
                    private val adbWrapper: IAdbWrapper) {

        private val log: Logger by lazy { LoggerFactory.getLogger(LogcatMonitor::class.java) }

        private var job = Job()
        val context: CoroutineContext = newCoroutineContext(context = CoroutineName("LogcatMonitor"), parent = job)

        // Coverage monitor variables
        private val sysCmdExecutor = SysCmdInterruptableExecutor()
        private var running: AtomicBoolean = AtomicBoolean(true)

        init {
            job = Job(parent = (this.job)) // We don't want to wait, therefore create our own (child) job
        }

        /**
        * Starts the monitoring job.
        */
        fun start() {
            launch {
                run()
            }
        }

        /**
         * Starts monitoring logcat.
         */
        private fun run() {
            log.info(Markers.appHealth, "Start monitoring logcat. Output to ${getLogfilePath().toAbsolutePath()}")

            try {
                Files.createDirectories(getLogfilePath().parent)

                while (running.get()) {
                    monitorLogcat()
                    Thread.sleep(5)
                }

            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        /**
         * Starts executing a command in order to monitor the logcat if the previous command is already terminated.
         */
        private fun monitorLogcat() {

            val path = getLogfilePath()
            val output = adbWrapper.executeCommand(sysCmdExecutor, cfg.deviceSerialNumber, "", "Logcat logfile monitor",
                    "logcat", "-v", "time")

            // Append the logcat content to the logfile
            log.info("Writing logcat output into $path")
            val file = path.toFile()
            file.appendBytes(output.toByteArray())
        }

        /**
         * Returns the logfile name in which the logcat content is written into.
         */
        private fun getLogfilePath(): Path {
            return cfg.getPath(LogbackUtils.getLogFilePath("logcat.txt"))
        }

        /**
         * Notifies the logcat monitor and [sysCmdExecutor] to finish.
         */
        fun terminate() {
            running.set(false)
            sysCmdExecutor.stopCurrentExecutionIfExisting()
            log.info("Logcat monitor thread destroyed")
        }

    }