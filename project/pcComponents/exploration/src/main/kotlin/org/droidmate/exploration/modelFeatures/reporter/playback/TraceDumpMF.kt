package org.droidmate.exploration.modelFeatures.reporter.playback

import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.strategy.playback.Playback
import java.nio.file.Path

/**
 * Report to print the state of each playback action, as well as the action which was taken
 */
class TraceDump @JvmOverloads constructor(reportDir: Path,
                                          resourceDir: Path,
                                          playbackStrategy: Playback,
                                          fileName: String = "dump.txt") : PlaybackReportMF(reportDir, resourceDir, playbackStrategy, fileName) {
    override fun safeWriteApkReport(data: ExplorationContext, apkReportDir: Path, resourceDir: Path) {
//		val reportSubDir = getPlaybackReportDir(apkReportDir)

        val sb = StringBuilder()

        val header = "TraceNr\tActionNr\tRequested\tReproduced\tExplorationAction\n"
        sb.append(header)

        TODO("use ModelFeature with own dump method if the current model does not have sufficient data")
//		playbackStrategy.traces.forEachIndexed { traceNr, trace ->
//			trace.getTraceCopy().forEachIndexed { actionNr, traceData ->
//				sb.append("$traceNr\t$actionNr\t${traceData.requested}\t${traceData.explored}\t${traceData.action}\n")
//			}
//		}

//		val reportFile = reportSubDir.resolve(fileName)
//		Files.write(reportFile, sb.toString().toByteArray())
    }

    override fun reset() {
        // Do nothing
    }
}