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

package org.droidmate.exploration.modelFeatures.reporter

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.TableDataFile
import org.droidmate.exploration.modelFeatures.misc.WidgetSeenClickedTable
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

class WidgetSeenClickedCountMF(reportDir: Path,
                               resourceDir: Path,
                               private val includePlots: Boolean = true,
                               private val fileName: String = "viewCount.txt") : ApkReporterMF(reportDir, resourceDir) {

    override val coroutineContext: CoroutineContext = CoroutineName("WidgetApiTraceMF")

    override fun safeWriteApkReport(context: ExplorationContext, apkReportDir: Path, resourceDir: Path) {
        val dataTable = WidgetSeenClickedTable(context)

        val reportPath = apkReportDir.resolve(fileName)
        val report = TableDataFile(dataTable, reportPath)

        report.write()
        if (includePlots) {
            log.info("Writing out plot $report")
            report.writeOutPlot(context.getModel().config.baseDir)
        }
    }

    override fun reset() {
        // Do nothing
        // Nothing to reset here
    }
}