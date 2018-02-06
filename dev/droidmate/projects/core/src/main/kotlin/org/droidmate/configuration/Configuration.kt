// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
package org.droidmate.configuration

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import org.droidmate.misc.BuildConstants
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * <p>
 *
 * This class holds all the configuration data of DroidMate. The configuration is obtained from command line arguments by
 * a call to {@code n ew ConfigurationBuilder().build(args)}. This happens in {@code DroidmateFrontend},
 * just before DroidMate constructs a {@code DroidmateCommand} and thus, its object graph of dependencies.
 *
 * </p><p>
 * This class relies heavily on the {@code jCommander} library, http://jcommander.org
 *
 * </p>
 *
 * @see ConfigurationBuilder
 */
// Explanation of @SuppressWarnings("UnnecessaryQualifiedReference"):
// This is a workaround for https://issues.apache.org/jira/browse/GROOVY-3278, which appears in @Parameter "names" argument declaration.
// Solution adapted from: http://stackoverflow.com/a/29042946/986533
@Suppress("RedundantVisibilityModifier")
@SuppressWarnings("UnnecessaryQualifiedReference")
@Parameters(separators = " =")
class Configuration(val args: Array<String>) : IConfiguration {
    companion object {
        @Throws(ConfigurationException::class)
        @JvmOverloads
        fun getDefault(fs: FileSystem = FileSystems.getDefault()): Configuration =
                ConfigurationBuilder().build(emptyArray(), fs)

        //region Instance construction logic

        /** The raw args (as given to {@code public static void main(String[] args)}) from which this configuration was obtained. */

        //endregion

        //region Cmd line parameters names and defaults

        // @formatter:off
        // "pn" stands for "parameter name"
        const val pn_actionsLimit = "-actionsLimit"
        const val pn_alwaysClickFirstWidget = "-alwaysClickFirstWidget"
        const val pn_androidApi = "-androidApi"
        const val pn_apksNames = "-apksNames"
        const val pn_apksDir = "-apksDir"
        const val pn_apksLimit = "-apksLimit"
        const val pn_checkAppIsRunningRetryAttempts = "-checkAppIsRunningRetryAttempts"
        const val pn_checkAppIsRunningRetryDelay = "-checkAppIsRunningRetryDelay"
        const val pn_checkDeviceAvailableAfterRebootAttempts = "-checkDeviceAvailableAfterRebootAttempts"
        const val pn_checkDeviceAvailableAfterRebootFirstDelay = "-checkDeviceAvailableAfterRebootFirstDelay"
        const val pn_checkDeviceAvailableAfterRebootLaterDelays = "-checkDeviceAvailableAfterRebootLaterDelays"
        const val pn_clearPackageRetryAttempts = "-clearPackageRetryAttempts"
        const val pn_clearPackageRetryDelay = "-clearPackageRetryDelay"
        const val pn_closeANRAttempts = "-closeANRAttempts"
        const val pn_closeANRDelay = "-closeANRDelay"
        const val pn_deployRawApks = "-deployRawApks"
        const val pn_device = "-device"
        const val pn_deviceSN = "-deviceSN"
        const val pn_replaceExtractedResources = "-replaceExtractedResources"
        const val pn_droidmateOutputDir = "-droidmateOutputDirPath"
        const val pn_getValidGuiSnapshotRetryAttempts = "-getValidGuiSnapshotRetryAttempts"
        const val pn_getValidGuiSnapshotRetryDelay = "-getValidGuiSnapshotRetryDelay"
        const val pn_inline = "-inline"
        const val pn_installAux = "-installAux"
        const val pn_installApk = "-installApk"
        const val pn_unpack = "-unpack"
        const val pn_launchActivityDelay = "-launchActivityDelay"
        const val pn_launchActivityTimeout = "-launchActivityTimeout"
        const val pn_monitorSocketTimeout = "-monitorSocketTimeout"
        const val pn_monitorUseLogcat = "-monitorUseLogcat"
        const val pn_randomSeed = "-randomSeed"
        const val pn_reportIncludePlots = "-reportIncludePlots"
        const val pn_reportInputDir = "-reportInputDir"
        const val pn_reportOutputDir = "-reportOutputDir"
        const val pn_resetEveryNthExplorationForward = "-resetEvery"
        const val pn_runOnNotInlined = "-runOnNotInlined"
        const val pn_shuffleApks = "-shuffleApks"
        const val pn_takeScreenshots = "-takeScreenshots"
        const val pn_timeLimit = "-timeLimit"
        const val pn_uiautomatorDaemonServerStartTimeout = "-uiautomatorDaemonServerStartTimeout"
        const val pn_uiautomatorDaemonServerStartQueryDelay = "-uiautomatorDaemonServerStartQueryDelay"
        const val pn_uiautomatorDaemonSocketTimeout = "-uiautomatorDaemonSocketTimeout"
        const val pn_uiautomatorDaemonTcpPort = "-tcpPort"
        const val pn_uiautomatorDaemonWaitForGuiToStabilize = "-waitForGuiToStabilize"
        const val pn_uiautomatorDaemonWaitForWindowUpdateTimeout = "-waitForWindowUpdateTimeout"
        const val pn_uninstallAux = "-uninstallAux"
        const val pn_uninstallApk = "-uninstallApk"
        const val pn_useApkFixturesDir = "-useApkFixturesDir"
        const val pn_report = "-report"
        const val pn_replay = "-replay"
        const val pn_stopAppRetryAttempts = "-stopAppRetryAttempts"
        const val pn_stopAppSuccessCheckDelay = "-stopAppSuccessCheckDelay"
        const val pn_waitForCanRebootDelay = "-waitForCanRebootDelay"
        const val pn_widgetIndexes = "-widgetIndexes"
        // @formatter:on
        //endregion

        val defaultActionsLimit = 10
        val defaultApksDir = "apks"
        // !!! DUPLICATION WARNING !!! org.droidmate.logging.LogbackConstants.getLogsDirPath
        // !!! DUPLICATION WARNING !!! repo\dev\droidmate\.gitignore
        val defaultDroidmateOutputDir = "output_device1"
        val defaultResetEveryNthExplorationForward = 0

        val api23 = "api23"
        //region Cmd line parameters
    }

    val screenshotsOutputSubDir = "screenshots"
    val reportOutputSubDir = "report"

    @Parameter(names = arrayOf(Configuration.pn_actionsLimit, "-actions", "-clicks"), description =
    "How many actions the GUI exploration strategy can conduct before terminating.")
    public var actionsLimit = defaultActionsLimit

    @Parameter(names = arrayOf(Configuration.pn_alwaysClickFirstWidget, "-clickFirst"), description =
    "Should the exploration strategy always click the first widget instead of its default more complex behavior")
    public var alwaysClickFirstWidget = false

    @Parameter(names = arrayOf(Configuration.pn_androidApi, "-api", "-apiLevel"),
            description = "Has to be set to the Android API version corresponding to the (virtual) devices on which DroidMate will run. Currently supported values: api23")
    public var androidApi = api23

    @Parameter(names = arrayOf(pn_apksLimit, "-limit"),
            description = "Limits the number of apks on which DroidMate will run. 0 means no limit.")
    public var apksLimit = 0

    @Parameter(names = arrayOf(pn_apksNames, "-apks", "-apps"), listConverter = ListOfStringsConverter::class,
            description = "Filters apps on which DroidMate will be run. Supply full file names, separated by commas, surrounded by square brackets. If the list is empty, it will run on all the apps in the apks dir. Example value: [app1.apk, app2.apk]")
    public var apksNames: MutableList<String> = ArrayList()

    @Parameter(names = arrayOf(pn_apksDir),
            description = "Directory containing the apks to be processed by DroidMate.")
    public var apksDirName = defaultApksDir

    @Parameter(names = arrayOf(pn_checkAppIsRunningRetryAttempts))
    public var checkAppIsRunningRetryAttempts = 2

    @Parameter(names = arrayOf(pn_checkAppIsRunningRetryDelay))
    public var checkAppIsRunningRetryDelay = 5 * 1000 // ms

    @Parameter(names = arrayOf(pn_checkDeviceAvailableAfterRebootAttempts))
    public var checkDeviceAvailableAfterRebootAttempts = 2

    @Parameter(names = arrayOf(pn_checkDeviceAvailableAfterRebootFirstDelay))
    public var checkDeviceAvailableAfterRebootFirstDelay = 60 * 1000

    @Parameter(names = arrayOf(pn_checkDeviceAvailableAfterRebootLaterDelays))
    public var checkDeviceAvailableAfterRebootLaterDelays = 10 * 1000

    @Parameter(names = arrayOf(pn_clearPackageRetryAttempts), arity = 1)
    public var clearPackageRetryAttempts = 2

    @Parameter(names = arrayOf(pn_clearPackageRetryDelay), arity = 1)
    public var clearPackageRetryDelay = 1000

    @Parameter(names = arrayOf(pn_closeANRAttempts))
    public var closeANRAttempts = 2

    @Parameter(names = arrayOf(pn_closeANRDelay))
    public var closeANRDelay = 1000

    @Parameter(names = arrayOf(pn_droidmateOutputDir, "-outputDir"), description =
    "Path to the directory that will contain DroidMate exploration output.")
    public var droidmateOutputDir = defaultDroidmateOutputDir

    @Parameter(names = arrayOf(pn_deployRawApks), arity = 1,
            description = "Deploys apks to device in 'raw' form, that is, without instrumenting them. Will deploy them raw even if instrumented version is available from last run.")
    public var deployRawApks = false

    @Parameter(names = arrayOf(pn_deviceSN))
    public var deviceSerialNumber = ""

    @Parameter(names = arrayOf(pn_device))
    public var deviceIndex = 0

    @Parameter(names = arrayOf(pn_installAux), arity = 1, description =
    "Reinstall the auxiliary files (UIAutomator and Monitor) to the device. If the auxiliary files are not previously installed the exploration will fail.")
    public var installAux: Boolean = true

    @Parameter(names = arrayOf(pn_installApk), arity = 1, description =
    "Reinstall the app to the device. If the app is not previously installed the exploration will fail.")
    public var installApk: Boolean = true

    @Parameter(names = arrayOf("-displayHelp", "-help", "-h", "-?"), help = true, description =
    "Displays command line parameters description.")
    public var displayHelp: Boolean = false

    @Parameter(names = arrayOf(pn_replaceExtractedResources), arity = 1,
            description = "Replace the resources from the extracted resources folder upon execution.")
    public var replaceExtractedResources = true

    @Parameter(names = arrayOf("-extractSummaries"), arity = 1)
    public var extractSummaries = true

    @Parameter(names = arrayOf(pn_getValidGuiSnapshotRetryAttempts))
    public var getValidGuiSnapshotRetryAttempts = 4

    @Parameter(names = arrayOf(pn_getValidGuiSnapshotRetryDelay))
    // Exploration of com.facebook.orca_v12.0.0.21.14-inlined.apk shows that that 4 attempts with 4000 ms delays (16s in total)
    // is not enough: all attempts get exhausted and only the repeated set of attempts, after restarting uia-d, succeeds.
    // com.netbiscuits.bild.android_v3.5.6-inlined needs more than 20s.
    public var getValidGuiSnapshotRetryDelay = 4 * 1000 // ms

    @Parameter(names = arrayOf(pn_inline), description =
    "If present, instead of normal run, DroidMate will inline all non-inlined apks. Before inlining backup copies of the apks will be created and put into a sub-directory of the directory containing the apks.")
    public var inline = false

    @Parameter(names = arrayOf(pn_unpack), description =
    "If present, instead of normal run, DroidMate will unpack all exploration output (SER) files into a directory.")
    public var unpack = false

    @Parameter(names = arrayOf(pn_launchActivityDelay))
    // Empirically checked that for com.skype.raider_v5.0.0.51733-inlined.apk 5000 ms is sometimes not enough.
    public var launchActivityDelay = 15 * 1000 // ms

    @Parameter(names = arrayOf(pn_launchActivityTimeout))
    public var launchActivityTimeout = 1 * 60 * 1000 // ms

    @Parameter(names = arrayOf("-logLevel"), description =
    "Logging level of the entirety of application. Possible values, comma separated: trace, debug, info.")
    var logLevel = "trace"

    @Parameter(names = arrayOf(pn_monitorSocketTimeout), arity = 1)
    public var monitorSocketTimeout = 1 * 60 * 1000 // ms

    @Parameter(names = arrayOf(Configuration.pn_monitorUseLogcat), arity = 1)
    public var monitorUseLogcat = false

    @Parameter(names = arrayOf(pn_uninstallAux), arity = 1, description =
    "Uninstall auxiliary files (UIAutomator and Monitor) after the exploration.")
    public var uninstallAux = true

    @Parameter(names = arrayOf(pn_uninstallApk), arity = 1, description =
    "Uninstall the APK after the exploration.")
    public var uninstallApk = true

    @Parameter(names = arrayOf(pn_randomSeed, "-seed"), description =
    "The seed for a random generator used by a random-clicking GUI exploration strategy. If null, a seed will be randomized.")
    public var randomSeed = -1

    @Parameter(names = arrayOf(pn_reportIncludePlots), arity = 1)
    public var reportIncludePlots = true

    @Parameter(names = arrayOf(pn_reportInputDir), description =
    "Path to the directory containing report input. The input is to be DroidMate exploration output.")
    public var reportInputDir = "reportInput"

    @Parameter(names = arrayOf(pn_reportOutputDir), description =
    "Path to the directory that will contain the report files.")
    public var reportOutputDir = "reportOutput"

    @Parameter(names = arrayOf(pn_resetEveryNthExplorationForward))
    public var resetEveryNthExplorationForward = defaultResetEveryNthExplorationForward

    @Parameter(names = arrayOf(pn_runOnNotInlined), description =
    "Allow DroidMate to run on non-inlined apks.")
    public var runOnNotInlined = false

    @Parameter(names = arrayOf(pn_replay), description =
    "Path do a previously recorded exploration for replay.")
    public var replayFile = ""

    @Parameter(names = arrayOf(pn_uiautomatorDaemonServerStartTimeout), description =
    "How long DroidMate should wait, in milliseconds, for message on logcat confirming that UiAutomatorDaemonServer has started on android (virtual) device.")
    public var uiautomatorDaemonServerStartTimeout = 20000

    @Parameter(names = arrayOf(pn_uiautomatorDaemonServerStartQueryDelay), description =
    "How often DroidMate should query, in milliseconds, for message on logcat confirming that UiDaemonServer has started on android (virtual) device.")
    public var uiautomatorDaemonServerStartQueryDelay = 2000

    @Parameter(names = arrayOf(pn_uiautomatorDaemonSocketTimeout), arity = 1)
    // Currently, this has to be at least higher than
    // "maxIterations" in org.droidmate.uiautomator2daemon.UiAutomatorDaemonDriver.waitForGuiToStabilize
    // times (uiautomatorDaemonWaitForWindowUpdateTimeout + 10s (default waitForIdle for each iteration))

    // So if (assuming API 23 / uiautomator2Daemon):
    // org.droidmate.configuration.Configuration.uiautomatorDaemonWaitForWindowUpdateTimeout = 1200ms
    // org.droidmate.uiautomator2daemon.UiAutomator2DaemonDriver.waitForGuiToStabilizeMaxIterations = 5
    // then minimum time should be: 5*(1'200ms + 10'000ms) = 56'000 ms
    // Plus add ~20 second to make things safe, as in practice even 60 ms caused java.net.SocketTimeoutException: Read timed out,
    // which I confirmed by seeing that logcat uiad logs took more than 61 seconds to process a GUI that fails to stabilize.
    public var uiautomatorDaemonSocketTimeout = 1 * 60 * 1000 // ms

    @Parameter(names = arrayOf(pn_uiautomatorDaemonWaitForGuiToStabilize), arity = 1, description =
    "Should the uiautomator-daemon wait for GUI state to stabilize after each click performed on the android device. Setting this to false will drastically speedup the clicking process, but will probably result in new clicks being sent while the results of previous one are still being processed.")
    public var uiautomatorDaemonWaitForGuiToStabilize = true

    /* Empirical evaluation shows that setting this to 600 will sometimes cause DroidMate to consider GUI stable while it
       actually isn't, yet.
       For more, see: org.droidmate.uiautomator_daemon.UiAutomatorDaemonDriver.waitForGuiToStabilize
     */
    @Parameter(names = arrayOf(pn_uiautomatorDaemonWaitForWindowUpdateTimeout), arity = 1)
    public var uiautomatorDaemonWaitForWindowUpdateTimeout = 1200 // ms

    @Parameter(names = arrayOf(pn_uiautomatorDaemonTcpPort), description =
    "TCP port used by DroidMate to communicate with the android (virtual) device.")
    public var uiautomatorDaemonTcpPort = UiautomatorDaemonConstants.UIADAEMON_SERVER_PORT

    @Parameter(names = arrayOf(pn_useApkFixturesDir), arity = 1)
    public var useApkFixturesDir = false

    @Parameter(names = arrayOf(pn_report), description =
    "If present, instead of normal run, DroidMate will generate reports from previously serialized data.")
    public var report = false

    @Parameter(names = arrayOf(pn_shuffleApks), arity = 1)
    public var shuffleApks = false

    @Parameter(names = arrayOf(pn_takeScreenshots), description = "Take screenshot after each exploration action.")
    public var takeScreenshots = false

    @Parameter(names = arrayOf(pn_timeLimit), description = "How long the exploration of any given apk should take, in seconds. If set to 0, instead actionsLimit will be used.")
    public var timeLimit = 0

    @Parameter(names = arrayOf(pn_widgetIndexes), listConverter = ListOfIntegersConverter::class,
            description = "Makes the exploration strategy to choose widgets to click that have the indexes as provided by this parameter, in sequence. The format is: [<first widget index>,<second widget index>,...<nth widget index>], starting indexing at 0. Example: [0,7,3]")
    public var widgetIndexes: MutableList<Int> = ArrayList()

    @Parameter(names = arrayOf(pn_stopAppRetryAttempts))
    public var stopAppRetryAttempts = 4

    @Parameter(names = arrayOf(pn_stopAppSuccessCheckDelay))
    public var stopAppSuccessCheckDelay = 5 * 1000 // ms

    @Parameter(names = arrayOf(pn_waitForCanRebootDelay))
    public var waitForCanRebootDelay = 30 * 1000

    //endregion

    //region Values set by ConfigurationBuilder

    public lateinit var droidmateOutputDirPath: Path

    public lateinit var droidmateOutputReportDirPath: Path

    public lateinit var reportInputDirPath: Path

    public lateinit var reportOutputDirPath: Path

    public lateinit var apksDirPath: Path

    public lateinit var monitorApkApi23: Path

    public val aaptCommand = BuildConstants.aapt_command
    public val adbCommand = BuildConstants.adb_command

    /**
     * Apk with uiautomator-daemon. This is a dummy package required only by instrumentation command (instrumentation target property)
     * More information about th property in: http://developer.android.com/guide/topics/manifest/instrumentation-element.html
     */
    public lateinit var uiautomator2DaemonApk: Path

    /**
     * Apk with "real" uiautomator-daemon. This apk will be deployed be on the android (virtual) device
     * to enable GUI actions execution.
     */
    public lateinit var uiautomator2DaemonTestApk: Path

    /**
     * File with API policies. This file will be deployed be on the android (virtual) device
     * to define which APIs will be accessible
     */
    public lateinit var apiPoliciesFile: Path

    //endregion
}
