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
import org.droidmate.exploration.modelFeatures.misc.uniqueActionableWidgets
import org.droidmate.exploration.modelFeatures.misc.uniqueClickedWidgets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

class ApkViewsFileMF(reportDir: Path,
                     resourceDir: Path,
                     val fileName: String = "views.txt") : ApkReporterMF(reportDir, resourceDir) {

    override val coroutineContext: CoroutineContext = CoroutineName("ApkViewsFileMF")

    override suspend fun safeWriteApkReport(context: ExplorationContext<*, *, *>, apkReportDir: Path, resourceDir: Path) {
        val reportData = getReportData(context)
        val reportFile = apkReportDir.resolve(fileName)
        Files.write(reportFile, reportData.toByteArray())
    }

    private fun getReportData(data: ExplorationContext<*,*,*>): String {
        val sb = StringBuilder()
        sb.append("Unique actionable widget\n")
                .append(data.uniqueActionableWidgets.joinToString(separator = System.lineSeparator()) { it.uid.toString() })
                .append("\n====================\n")
                .append("Unique clicked widgets\n")
                .append(data.uniqueClickedWidgets.joinToString(separator = System.lineSeparator()) { it.uid.toString() })

        return sb.toString()
    }

    override fun reset() {
        // Do nothing
        // Nothing to reset here
    }
}
