package org.droidmate.example

import kotlinx.coroutines.runBlocking
import org.droidmate.api.ExplorationAPI

class CoverageInstrumentationExample {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                println("Instrumenting APK to monitor APIs")

                // Create a configuration to run DroidMate
                val cfg = ExplorationAPI.config(args)

                val instrumentationResult = ExplorationAPI.instrument(cfg)

                if (instrumentationResult != null) {
                    val apkFile = instrumentationResult.first
                    val statements = instrumentationResult.second

                    println("Instrumented apk: $apkFile")
                    println("List of statements: $statements")

                    // Run DroidMate
                    Example.run(cfg)
                }
            }
        }
    }
}