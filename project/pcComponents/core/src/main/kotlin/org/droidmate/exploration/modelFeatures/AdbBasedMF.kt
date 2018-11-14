package org.droidmate.exploration.modelFeatures

import org.droidmate.configuration.ConfigurationWrapper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

abstract class AdbBasedMF(val cfg: ConfigurationWrapper): ModelFeature() {
	internal fun runAdbCommand(commandArgs: List<String>,
							   timeout : Int = 1,
							   timeoutUnit: TimeUnit = TimeUnit.SECONDS): List<String> {
		val command = listOf(cfg.adbCommand, "-s", cfg.deviceSerialNumber, *commandArgs.toTypedArray())

		val builder = ProcessBuilder(command)

		val process = builder.start()

		val inputReader = InputStreamReader(process.inputStream)

		// Fixed maximum time because sometimes the process is not stopping automatically
		val success = process.waitFor(timeout.toLong(), timeoutUnit)

		val stdout = BufferedReader(inputReader).lines().toList()

		if (success) {
			val exitVal = process.exitValue()
			assert(exitVal == 0) { "Logcat process exited with error $exitVal." }
		}

		process.destroy()
		return stdout
	}
}