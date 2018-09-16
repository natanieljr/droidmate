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

package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.*
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.ModelProperties.Features.statementCoverageDir
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.ModelConfig
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.logging.Markers
import org.droidmate.misc.SysCmdInterruptableExecutor
import org.droidmate.misc.deleteDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.streams.toList

/**
 * Model feature to monitor the statement coverage by processing and optional instrumentation file and actively
 * monitor and parse the logcat in order to calculate the coverage.
 */
class StatementCoverageMF(private val cfg: ConfigurationWrapper,
						  private val modelCfg: ModelConfig,
						  private val adbWrapper: IAdbWrapper) : ModelFeature() {

	private val log: Logger by lazy { LoggerFactory.getLogger(StatementCoverageMF::class.java) }
	private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("StatementCoverageMF"), parent = job)

	private val instrumentationDir = Paths.get(modelCfg[statementCoverageDir].toString()).toAbsolutePath()
	private val logcatOutputDir: Path = cfg.coverageReportDirPath.toAbsolutePath().resolve(modelCfg.appName)

	private val executedStatementsMap: ConcurrentHashMap<String, Date> = ConcurrentHashMap()
	private val instrumentationMap: Map<String, String>

	// Coverage monitor variables
	private val sysCmdExecutor = SysCmdInterruptableExecutor()
	private val purgedDeviceSerialNumber = cfg[ConfigProperties.Exploration.deviceSerialNumber].toString().replace(":", "-")

	private var lastReadStatement = ""
	private var counter = 0

	init {
		job = Job(parent = (this.job)) // We don't want to wait for other features (or having them wait for us), therefore create our own (child) job
		logcatOutputDir.deleteDir()
		Files.createDirectories(logcatOutputDir)

		instrumentationMap = getInstrumentation(modelCfg.appName)
	}

	override suspend fun onNewAction(traceId: UUID, deferredAction: Deferred<ActionData>, prevState: StateData, newState: StateData) {
		// must wait for the action before reading the logcat data
		deferredAction.await()

		readCoverageFromLogcat()
	}

	/**
	 * Returns a map which is used for the coverage calculation.
	 */
	private fun getInstrumentation(apkName: String): Map<String, String> {
		return if (!Files.exists(instrumentationDir)) {
			log.warn("Provided statementCoverageDir does not exist: $statementCoverageDir. DroidMate will monitor coverage will not be able to calculate coverage.")
			emptyMap()
		}
		else {
			val instrumentationFile = getInstrumentationFile(apkName)

			if (instrumentationFile != null)
				readInstrumentationFile(instrumentationFile)
			else
				emptyMap()
		}
	}

	/**
	 * Returns the the given instrumentation file corresponding to the passed [apkName].
	 *
	 * Example:
	 * apkName = a2dp.Vol_137.apk
	 * return: instrumentation-a2dp.Vol.json
	 */
	private fun getInstrumentationFile(apkName: String): Path? {
		return Files.list(instrumentationDir)
				.toList()
				.firstOrNull{ it.fileName.toString().contains(apkName)
						&& it.fileName.toString().endsWith(".apk.json")}
	}

	@Throws(IOException::class)
	private fun readInstrumentationFile(instrumentationFile: Path): Map<String, String> {
		val jsonData = String(Files.readAllBytes(instrumentationFile))
		val jObj = JSONObject(jsonData)

		val jArr = JSONArray(jObj.getJSONArray("allMethods").toString())

		val l = "9946a686-9ef6-494f-b893-ac8b78efb667".length
		val statements : MutableMap<String, String> = mutableMapOf()
		(0 until jArr.length()).forEach { idx ->
			val method = jArr[idx]

			if (!method.toString().contains("CoverageHelper")) {
				val parts = method.toString().split("uuid=".toRegex(), 2).toTypedArray()
				val uuid = parts.last()

				assert(uuid.length == l) { "Invalid UUID $uuid $method" }

				statements[uuid] = method.toString()
			}
		}

		return statements
	}

	/**
	 * Returns the current measured coverage.
	 * Note: Returns 1 if [instrumentationMap] is empty.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun getCurrentCoverage(): Double {
		return if (instrumentationMap.isEmpty()) {
			1.0
		} else {
			executedStatementsMap.size / instrumentationMap.size.toDouble()
		}
	}

	/**
	 * Starts executing a command in order to monitor the logcat if the previous command is already terminated.
	 * Additionally, it parses the returned logcat output and updates [executedStatementsMap].
	 */
	private fun readCoverageFromLogcat() {

		val file = getLogFilename(counter)
		val output = adbWrapper.executeCommand(sysCmdExecutor, cfg.deviceSerialNumber, "", "Logcat coverage monitor",
				"logcat", "-d", "-v", "threadtime", "-s", "System.out")
		//log.info("Writing logcat output into $file")

		output.lines()
				.filter { it.contains("[androcov]")
						&& !it.contains("CoverageHelper")
						&& it > lastReadStatement }
				.forEach { line ->
					val parts = line.split("uuid=".toRegex(), 2).toTypedArray()

					// Get uuid
					val uuid = parts.last()

					val tms = if (parts.size > 1) {
						// Get timestamp
						val logParts = parts[0].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
						val timestamp = logParts[0] + " " + logParts[1]

						dateFormat.parse(timestamp)
					} else {
						Date.from(Instant.now())
					}

					// Add the statement if it wasn't executed before
					val found = executedStatementsMap.containsKey(uuid)

					if (!found /*&& instrumentationMap.containsKey(uuid)*/)
						executedStatementsMap[uuid] = tms

					lastReadStatement = line
				}

		log.info("Current statement coverage: ${getCurrentCoverage()}")

		// Write the logcat content into a file
		Files.write(file, output.toByteArray())

		counter++
	}

	/**
	 * Returns the logfile name depending on the [counter] in which the logcat content is written into.
	 */
	private fun getLogFilename(counter: Int): Path {
		return logcatOutputDir.resolve("${modelCfg.appName}-logcat_${purgedDeviceSerialNumber}_%04d".format(counter))
	}

	/**
	 * Notifies the logcat monitor and [sysCmdExecutor] to finish.
	 */
	override suspend fun dump(context: ExplorationContext) {
		job.joinChildren()
		sysCmdExecutor.stopCurrentExecutionIfExisting()
		log.info("Coverage monitor thread destroyed")

		val sb = StringBuilder()
		sb.appendln(header)

		if (executedStatementsMap.isNotEmpty()) {
			val sortedStatements = executedStatementsMap.entries
					.sortedBy { it.value }
			val initialDate = sortedStatements.first().value

			sortedStatements
					.forEach {
						sb.appendln("${it.key};${Duration.between(initialDate.toInstant(), it.value.toInstant()).toMillis() / 1000}")
					}
		}

		val outputFile = context.getModel().config.baseDir.resolve("coverage.txt")
		Files.write(outputFile, sb.lines())
	}

	companion object {
		private const val header = "Statement;Time"
	}

}