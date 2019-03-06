package org.droidmate.exploration.modelFeatures.reporter.playback

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.modelFeatures.reporter.ApkReporterMF
import org.droidmate.exploration.strategy.playback.Playback
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

abstract class PlaybackReportMF(reportDir: Path,
                                resourceDir: Path,
                                protected val playbackStrategy: Playback,
                                protected val fileName: String,
                                private val includePlots: Boolean = true) : ApkReporterMF(reportDir, resourceDir) {

    override val coroutineContext: CoroutineContext = CoroutineName("PlaybackReportMF")

    fun getPlaybackReportDir(apkReportDir: Path): Path {
        val playbackDir = apkReportDir.resolve("playback")

        if (!Files.exists(playbackDir))
            Files.createDirectories(playbackDir)

        return playbackDir
    }

}