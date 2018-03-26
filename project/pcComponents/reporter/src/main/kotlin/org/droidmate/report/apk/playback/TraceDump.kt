// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Saarland University
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
package org.droidmate.report.apk.playback

import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.exploration.strategy.playback.MemoryPlayback
import java.nio.file.Files
import java.nio.file.Path

/**
 * Report to print the state of each playback action, as well as the action which was taken
 */
class TraceDump @JvmOverloads constructor(playbackStrategy: MemoryPlayback,
                                          fileName: String = "dump.txt") : PlaybackReport(playbackStrategy, fileName) {
	override fun safeWriteApkReport(data: AbstractContext, apkReportDir: Path) {
		val reportSubDir = getPlaybackReportDir(apkReportDir)

		val sb = StringBuilder()

		val header = "TraceNr\tActionNr\tRequested\tReproduced\tAction\n"
		sb.append(header)

		playbackStrategy.traces.forEachIndexed { traceNr, trace ->
			trace.getTraceCopy().forEachIndexed { actionNr, traceData ->
				sb.append("$traceNr\t$actionNr\t${traceData.requested}\t${traceData.explored}\t${traceData.action}\n")
			}
		}

		val reportFile = reportSubDir.resolve(fileName)
		Files.write(reportFile, sb.toString().toByteArray())
	}
}