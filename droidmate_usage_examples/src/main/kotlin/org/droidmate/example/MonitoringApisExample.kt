package org.droidmate.example

import kotlinx.coroutines.runBlocking
import org.droidmate.api.ExplorationAPI

class MonitoringApisExample {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                println("Instrumenting APK to monitor APIs")

                // Create a configuration to run DroidMate
                val cfg = ExplorationAPI.config(args)

                //val inputDir = Paths.get("original-apks")
                //val outputDir = Paths.get("apks")
                //ApkInliner(cfg.resourceDir).instrumentApk(inputDir, outputDir)
                ExplorationAPI.inline(cfg)

                // Run DroidMate
                Example.run(cfg)
            }
        }
    }
}