package cispa.saarland.coveragecalc

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.Math.max
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.collections.HashMap

class NewCoverageCalculator {

	// Jie
	//private val instrumentationDir = Paths.get("/Users/nataniel/Desktop/jie/data/instrumentation-statements/")
	//private val resultsDir = Paths.get("/Users/nataniel/Desktop/jie/data/results/")

	private val instrumentationDir = Paths.get("/Users/nataniel/Documents/saarland/2018-SS/paper-droidmate-tool/data/instrumentation-statements/")
	// DroidMate
	private val resultsDir = Paths.get("/Users/nataniel/Documents/saarland/2018-SS/paper-droidmate-tool/data/results/")
	// DroidBot
	//private val resultsDir = Paths.get("/Users/nataniel/Documents/saarland/2018-SS/paper-droidmate-tool/data/droidbot-results/")

	private val outputDir = Paths.get("./out/reports")

	init{
		if (!Files.exists(outputDir))
			Files.createDirectories(outputDir)
	}

	private fun getInstrumentation(apkName: String): Map<String, String>{
		val instrFile = getInstrumentationFile(apkName)

		return readInstrumentationFile(instrFile)
	}

	private fun getInstrumentationFile(apkName: String): Path{
		//instrumentation-a2dp.Vol.json
		//a2dp.Vol_137.apk
		val procApkName = apkName.split("_").firstOrNull()?.replace(".apk", "") ?: apkName.replace(".apk", "")
		val instrumentationFileName = "instrumentation-$procApkName.json"

		val instrumentationFile = instrumentationDir.resolve(instrumentationFileName).toAbsolutePath()

		if (!Files.exists(instrumentationFile))
			error("Instrumentation file not found: $instrumentationFile")

		return instrumentationFile
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

				assert(uuid.length == l, { "Invalid UUID $uuid $method" })

				statements[uuid] = method.toString()
			}
		}

		return statements
	}

	private fun processResults(){
		val overallRunsCoverage : MutableList<Map<Long,Double>> = mutableListOf()

		Files.list(resultsDir)
				.filter { !it.fileName.toString().startsWith("old.") }
				.sorted()
				.forEach { dir ->
					if (Files.isDirectory(dir) && dir.fileName.toString().endsWith(".apk")){
						overallRunsCoverage.addAll(processApk(dir))
					}
				}

		val inflatedData = inflateOverMax(overallRunsCoverage)
		reportAverageTime("overall", inflatedData)
	}

	private fun processApk(apkDir: Path): List<Map<Long,Double>> {
		//println("Obtaining instrumentation data for APK ${apkDir.fileName}")

		val apkName = apkDir.fileName.toString()
		val instrumentation = getInstrumentation(apkName)

		//println("Calculating coverage for APK $apkName")
		val originalRuns : MutableList<Map<String, Date>> = mutableListOf()
		val updatedRuns : MutableList<Map<String, Date>> = mutableListOf()

		val runsCoverage : MutableList<Map<Long,Double>> = mutableListOf()

		Files.list(apkDir)
				.filter { !it.fileName.toString().startsWith("old.") }
				.sorted()
				.forEach { dir ->
					if (Files.isDirectory(dir)) {
						val runData = processRun(dir, apkDir.fileName.toString(), instrumentation)

						if (dir.fileName.toString().startsWith("run"))
							originalRuns.add(runData)
						else if (!dir.fileName.toString().startsWith("old."))
							updatedRuns.add(runData)

						printRun(apkName, dir, runData, instrumentation)
						val statementsSeconds = generateStatementsTimeData(apkName, dir, runData, instrumentation)

						runsCoverage.add(statementsSeconds)
					}
				}

		val originalRunsIntersection = calculateIntersectionMap(originalRuns)
		val updatedRunsIntersection = calculateIntersectionMap(updatedRuns)
		val overallIntersection = calculateIntersectionSet(listOf(originalRunsIntersection, updatedRunsIntersection))
		println("Original runs intersection size: ${originalRunsIntersection.size}")
		println("Update runs intersection size: ${updatedRunsIntersection.size}")
		println("Intersection between original and updated: ${overallIntersection.size} (${overallIntersection.size/originalRunsIntersection.size.toDouble()})")

		val inflatedData = inflateOverMax(runsCoverage)
		reportAverageTime(apkName, inflatedData)

		return runsCoverage
	}

	private fun inflateOverMax(runsCoverage: MutableList<Map<Long,Double>>): List<Map<Long,Double>>{
		val maxTime = runsCoverage.map { it.keys.max() ?: 0L }.max() ?: 0L

		val result : MutableList<MutableMap<Long,Double>> = mutableListOf()
		runsCoverage.forEach { run ->
			val mutableRun = run.toMutableMap()
			val maxTimeRun = run.keys.max() ?: 0L

			if (maxTimeRun < maxTime) {
				(maxTimeRun..maxTime step 5).forEach { i ->
					if (!mutableRun.containsKey(i)) {
						val prevVal = mutableRun[i - 5]!!
						mutableRun[i] = prevVal
					}
				}
			}

			result.add(mutableRun)
		}

		return result
	}

	private fun reportAverageTime(apkName: String, runsCoverage: List<Map<Long,Double>>){
		val sb = StringBuilder()
		sb.appendln("Seconds\tStatements")
		val maxTime = runsCoverage.map { it.keys.max() ?: 0L }.max() ?: 0L

		(0..maxTime step 5).forEach { seconds ->
			val percentage = runsCoverage.map { it.getValue(seconds) }.average()
			sb.appendln("$seconds\t${String.format("%.2f", percentage).replace(".", ",")}")
		}

		val reportDir = outputDir.resolve(apkName)
		Files.createDirectories(reportDir)

		val reportFile = reportDir.resolve("_average_statementsTime.txt")
		Files.write(reportFile, sb.toString().toByteArray())
	}

	private fun processRun(runDir: Path, apkName: String, instrumentation: Map<String, String>): Map<String, Date> {

		val executedStatements: MutableMap<String, Date> = HashMap()

		val coverageDir = if (Files.exists(runDir.resolve("droidMate").resolve("coverage")))
			runDir.resolve("droidMate").resolve("coverage")
		else
			runDir

		Files.list(coverageDir)
				.sorted()
				.filter { !Files.isDirectory(it) }
				.filter { it.fileName.toString().replace("mypix-", "mypix_") .startsWith(apkName.split(".").first()) }
				.forEach { logFile ->
					//println("Reading log file $logFile")

					Files.lines(logFile)
							.forEach { line ->
								if (line.contains("[androcov]") && !line.contains("CoverageHelper")) {
									val parts = line.split("uuid=".toRegex(), 2).toTypedArray()

									// Get uuid
									val uuid = parts[1]

									// Get timestamp
									val logParts = parts[0].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
									val timestamp = logParts[0] + " " + logParts[1]

									val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS")
									val tms = dateFormat.parse(timestamp)

									// Add the statement if it wasn't executed before
									val found = executedStatements.containsKey(uuid)

									if (!found && instrumentation.containsKey(uuid))
										executedStatements[uuid] = tms
									/*else if (!instrumentation.containsKey(uuid)){
										println("Not found $uuid")
									}*/
								}
							}
				}

		return executedStatements
	}

	private fun calculateIntersectionSet(runs: List<Set<String>>): Set<String>{
		val res : MutableSet<String> = mutableSetOf()

		runs.forEach { run ->
			run.forEach { key ->
				if (runs.all { it.contains(key) })
					res.add(key)
			}
		}

		return res
	}

	private fun calculateIntersectionMap(runs: List<Map<String, Date>>): Set<String>{
		return calculateIntersectionSet(runs.map { it.keys })
	}

	private fun printRun(apkName: String, runFolder: Path, run: Map<String, Date>, instrumentation: Map<String, String>){
		try {
			val numExecutedStatements = run.size
			val numStatementsInApp = instrumentation.size
			val coverage = numExecutedStatements / instrumentation.size.toDouble()

			// Write coverage report
			//val header = "App\t#StatementsInApp\t#ExecStatements\tCoverage\n"
			val result = "$apkName\t\t${runFolder.fileName}\t\t$numStatementsInApp\t\t$numExecutedStatements\t\t${String.format("%.2f", coverage)}\n"
			print("Coverage: $result")

			/*val outCovReportFile
			Files.write(outCovReportFile, header.toByteArray(), StandardOpenOption.CREATE)
			Files.write(outCovReportFile, result.toByteArray(), StandardOpenOption.APPEND)*/
		} catch (ex: IOException) {
			ex.printStackTrace()
		}

	}

	private fun inflateMap(mappedData: MutableMap<Long, List<Pair<Long,String>>>,
						   maxTime : Long = max(mappedData.keys.sorted().lastOrNull() ?: 0L, 3600)): Map<Long, List<Pair<Long,String>>>{
		(0..maxTime step 5).forEach{ i ->
			if (!mappedData.containsKey(i))
				mappedData[i] = emptyList()
		}

		return mappedData.toSortedMap()
	}

	private fun generateStatementsTimeData(apkName: String, runFolder: Path, run: Map<String, Date>, instrumentation: Map<String, String>): Map<Long,Double>{
		val firstDate = run.map { it.value }.sorted().firstOrNull()?.toInstant() ?: Instant.now()

		val usedStatements : MutableSet<String> = mutableSetOf()

		val mappedData = inflateMap(run
				.map { Pair((Duration.between(firstDate, it.value.toInstant()).seconds / 5) * 5, it.key) }
				.groupBy { it.first }
				.toSortedMap()
				.toMutableMap())

		var totalItems = 0
		val sb = StringBuilder()
		sb.appendln("Seconds\tStatements")

		val results : MutableMap<Long,Double> = mutableMapOf()

		mappedData
				.forEach { seconds, items ->
					val newStatements = items
							.filter { !usedStatements.contains(it.second) }
							.map { it.second }
					usedStatements.addAll(newStatements)

					totalItems += newStatements.size
					val percentage = totalItems/instrumentation.size.toDouble()
					sb.appendln("$seconds\t${String.format("%.2f", percentage).replace(".", ",")}")

					results[seconds] = percentage
				}

		val reportDir = outputDir.resolve(apkName)
		Files.createDirectories(reportDir)

		val reportFile = reportDir.resolve("${runFolder.fileName}_statementsTime.txt")
		Files.write(reportFile, sb.toString().toByteArray())

		return results
	}

/*
	private fun getExecutedStatementsEvol(executedMethodsFile: Path) {
		//println("Processing $executedMethodsFile")
		var isFirst = true
		try {

			val br = BufferedReader(FileReader(executedMethodsFile.toFile()))

			//var idx = 0
			var line: String? = br.readLine()
			while (line != null) {
				//if (idx++ % 1000 == 0)
				//	println("Processing line $idx")

				if (line.contains("[androcov]")) {
					val parts = line.split("uuid=".toRegex(), 2).toTypedArray()

					// Get uuid
					val uuid = parts[1]

					// Get timestamp
					val logParts = parts[0].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
					val timestamp = logParts[0] + " " + logParts[1]

					val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS")
					val tms = dateFormat.parse(timestamp)
					lastT = tms

					if (isFirst) {
						//firstT=tms;
						isFirst = false
					}

					// Add the statement if it wasn't executed before
					val found = executedStatements.containsKey(uuid)

					if (!found)
						executedStatements[uuid] = tms
				}
				line = br.readLine()
			}
			println()
		} catch (e: Exception) {
			e.printStackTrace()
		}

	}
*/
/*
	/**
	 * Generate the report with the cumulative statement coverage evolution
	 *
	 * @param outCovReportFile
	 * @param apkName
	 */
	private fun genCovStatementsEvolReport(outCovReportFile: Path, apkName: String, tick: Long) {
		println("Generating coverage evolution file.....")
		val outFileReport = "$outCovReportFile-$apkName-EVOL.txt"

		val header = "T\t#ExecMethods\n"

		try {
			Files.write(Paths.get(outFileReport), header.toByteArray(), StandardOpenOption.CREATE)

			val methodsPerInterval = newMethodsByTime(executedStatements, tick)

			for (i in methodsPerInterval.indices) {
				val result = i.toString() + "\t" + methodsPerInterval[i] + "\n"
				Files.write(Paths.get(outFileReport), result.toByteArray(), StandardOpenOption.APPEND)
			}

		} catch (ex: IOException) {
			ex.printStackTrace()
		}

	}
*/

	/**
	 * Groups the statements by intervals of time
	 *
	 * @param executedStatements
	 * @param intervalLength
	 * @return
	 */
	private fun newMethodsByTime(executedStatements: Map<String, Date>, intervalLength: Long): ArrayList<Int> {
		println(">> Dividing logs by timestamp......")

		val methodsPerInterval = ArrayList<Int>()
		methodsPerInterval.add(0)

		val sortedStatements = executedStatements.entries
				.sortedBy { it.value }

		val lastT = sortedStatements.last().value
		try {
			var currentCounter = 0
			val statements = executedStatements.entries.sortedBy { p -> p.value }
			val startTime = statements[0].value.time

			var currentLimit = startTime + intervalLength

			for (executedStatement in statements) {
				if (currentLimit <= lastT.time) {

					if (executedStatement.value.time <= currentLimit) {
						currentCounter++
					} else {
						methodsPerInterval.add(currentCounter)
						currentCounter++
						currentLimit += intervalLength
					}
				} else {
					currentLimit = lastT.time
					println("**In the else!!!!")
				}//This is the last iteration
			}

			while (currentLimit <= lastT.time) {
				methodsPerInterval.add(currentCounter)
				currentLimit += intervalLength
			}

		} catch (ex: Exception) {
			ex.printStackTrace()
		}

		return methodsPerInterval
	}

	companion object {

		@JvmStatic
		fun main(args: Array<String>) {

			val cc = NewCoverageCalculator()

			cc.processResults()
		}
	}
}
