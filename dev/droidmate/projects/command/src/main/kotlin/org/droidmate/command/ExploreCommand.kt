// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.command

import com.konradjamrozik.isRegularFile
import org.droidmate.android_sdk.*
import org.droidmate.command.exploration.Exploration
import org.droidmate.command.exploration.IExploration
import org.droidmate.configuration.Configuration
import org.droidmate.deleteDir
import org.droidmate.exploration.data_aggregators.ExplorationOutput2
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.exploration.device.IRobustDevice
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.exploration.strategy.IExplorationStrategy
import org.droidmate.logging.Markers
import org.droidmate.misc.ITimeProvider
import org.droidmate.misc.ThrowablesCollection
import org.droidmate.misc.TimeProvider
import org.droidmate.report.AggregateStats
import org.droidmate.report.Reporter
import org.droidmate.report.Summary
import org.droidmate.report.apk.*
import org.droidmate.report.misc.withFilteredApiLogs
import org.droidmate.storage.IStorage2
import org.droidmate.storage.Storage2
import org.droidmate.tools.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

open class ExploreCommand constructor(private val apksProvider: IApksProvider,
                                      private val deviceDeployer: IAndroidDeviceDeployer,
                                      private val apkDeployer: IApkDeployer,
                                      private val exploration: IExploration,
                                      private val storage2: IStorage2) : DroidmateCommand() {
    companion object {
        @JvmStatic
        protected val log: Logger = LoggerFactory.getLogger(ExploreCommand::class.java)

        fun build(cfg: Configuration,
                  strategyProvider: (IExplorationLog) -> IExplorationStrategy = { ExplorationStrategyPool.build(it, cfg) },
                  timeProvider: ITimeProvider = TimeProvider(),
                  deviceTools: IDeviceTools = DeviceTools(cfg)): ExploreCommand {
            val apksProvider = ApksProvider(deviceTools.aapt)

            val storage2 = Storage2(cfg.droidmateOutputDirPath)
            val exploration = Exploration.build(cfg, timeProvider, strategyProvider)
            val command = ExploreCommand(apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer, exploration, storage2)

            command.registerReporter(AggregateStats())
            command.registerReporter(Summary())
            command.registerReporter(ApkViewsFile())
            command.registerReporter(ApiCount(cfg.reportIncludePlots))
            command.registerReporter(ClickFrequency(cfg.reportIncludePlots))
            command.registerReporter(WidgetSeenClickedCount(cfg.reportIncludePlots))
            command.registerReporter(ApiActionTrace())
            command.registerReporter(ActivitySeenSummary())
            command.registerReporter(ActionTrace())
            command.registerReporter(WidgetApiTrace())

            if (cfg.takeScreenshots)
                command.registerReporter(EffectiveActions())

            return command
        }
    }

    private val reporters: MutableList<Reporter> = ArrayList()

    override fun execute(cfg: Configuration) {
        cleanOutputDir(cfg)

        val apks = this.apksProvider.getApks(cfg.apksDirPath, cfg.apksLimit, cfg.apksNames, cfg.shuffleApks)
        if (!validateApks(apks, cfg.runOnNotInlined)) return

        val explorationExceptions = execute(cfg, apks)
        if (!explorationExceptions.isEmpty())
            throw ThrowablesCollection(explorationExceptions)
    }

    private fun writeReports(reportDir: Path, rawData: List<IExplorationLog>) {
        if (!Files.exists(reportDir))
            Files.createDirectories(reportDir)

        assert(Files.exists(reportDir), { "Unable to create report directory ($reportDir)" })

        log.info("Writing reports")
        val reportData = rawData.withFilteredApiLogs
        reporters.forEach { it.write(reportDir.toAbsolutePath(), reportData) }
    }

    fun registerReporter(report: Reporter) {
        reporters.add(report)
    }

    private fun validateApks(apks: List<Apk>, runOnNotInlined: Boolean): Boolean {
        if (apks.isEmpty()) {
            log.warn("No input apks found. Terminating.")
            return false
        }

        if (apks.any { !it.inlined }) {
            if (runOnNotInlined) {
                log.info("Not inlined input apks have been detected, but DroidMate was instructed to run anyway. Continuing with execution.")
            } else {
                log.warn("At least one input apk is not inlined. DroidMate will not be able to monitor any calls to Android SDK methods done by such apps.")
                log.warn("If you want to inline apks, run DroidMate with ${Configuration.pn_inline}")
                log.warn("If you want to run DroidMate on non-inlined apks, run it with ${Configuration.pn_runOnNotInlined}")
                log.warn("DroidMate will now abort due to the not-inlined apk.")
                return false
            }
        }
        return true
    }

    private fun cleanOutputDir(cfg: Configuration) {
        val outputDir = cfg.droidmateOutputDirPath

        if (!Files.isDirectory(outputDir))
            return

        arrayListOf(cfg.screenshotsOutputSubDir, cfg.reportOutputSubDir).forEach {

            val dirToDelete = outputDir.resolve(it)
            if (Files.isDirectory(dirToDelete))
                dirToDelete.deleteDir()
        }

        Files.walk(outputDir).filter { it.isRegularFile }.forEach { Files.delete(it) }

        Files.walk(outputDir).forEach { assert(Files.isDirectory(it)) }
    }

    private fun execute(cfg: Configuration, apks: List<Apk>): List<ExplorationException> {
        val out = ExplorationOutput2()

        val explorationExceptions: MutableList<ExplorationException> = ArrayList()

        try {
            explorationExceptions += deployExploreSerialize(cfg.deviceSerialNumber, cfg.deviceIndex, apks, out)
        } catch (deployExploreSerializeThrowable: Throwable) {
            log.error("!!! Caught ${deployExploreSerializeThrowable.javaClass.simpleName} " +
                    "in execute(configuration, apks)->deployExploreSerialize(${cfg.deviceIndex}, apks, out). " +
                    "This means ${ExplorationException::class.java.simpleName}s have been lost, if any! " +
                    "Skipping summary output analysis persisting. " +
                    "Rethrowing.")
            throw deployExploreSerializeThrowable
        }

        writeReports(cfg.droidmateOutputReportDirPath, out)

        return explorationExceptions
    }

    private fun deployExploreSerialize(deviceSerialNumber: String,
                                       deviceIndex: Int,
                                       apks: List<Apk>,
                                       out: ExplorationOutput2): List<ExplorationException> {
        return this.deviceDeployer.withSetupDevice(deviceSerialNumber, deviceIndex) { device ->

            val allApksExplorationExceptions: MutableList<ApkExplorationException> = ArrayList()

            var encounteredApkExplorationsStoppingException = false

            apks.forEachIndexed { i, apk ->
                if (!encounteredApkExplorationsStoppingException) {
                    log.info(Markers.appHealth, "Processing ${i + 1} out of ${apks.size} apks: ${apk.fileName}")

                    allApksExplorationExceptions +=
                            this.apkDeployer.withDeployedApk(device, apk) { deployedApk ->
                                tryExploreOnDeviceAndSerialize(deployedApk, device, out)
                            }

                    if (allApksExplorationExceptions.any { it.shouldStopFurtherApkExplorations() }) {
                        log.warn("Encountered an exception that stops further apk explorations. Skipping exploring the remaining apks.")
                        encounteredApkExplorationsStoppingException = true
                    }

                    // Just preventative measures for ensuring healthiness of the device connection.
                    //          device.reconnectAdb()
                    //          device.restartUiaDaemon(false)
                }
            }

            allApksExplorationExceptions
        }
    }

    @Throws(DeviceException::class)
    private fun tryExploreOnDeviceAndSerialize(
            deployedApk: IApk, device: IRobustDevice, out: ExplorationOutput2) {
        val fallibleApkOut2 = this.exploration.run(deployedApk, device)

        if (fallibleApkOut2.result != null) {
            fallibleApkOut2.result!!.serialize(this.storage2)
            out.add(fallibleApkOut2.result!!)
        }

        if (fallibleApkOut2.exception != null)
            throw fallibleApkOut2.exception!!
    }
}