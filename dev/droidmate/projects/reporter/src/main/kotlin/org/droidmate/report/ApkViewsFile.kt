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
package org.droidmate.report

import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.misc.uniqueString
import org.droidmate.report.misc.apkFileNameWithUnderscoresForDots
import org.droidmate.report.misc.uniqueActionableWidgets
import org.droidmate.report.misc.uniqueClickedWidgets
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

class ApkViewsFile : IReporter {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(ApkViewsFile::class.java)
    }

    override fun write(reportDir: Path, rawData: List<IExplorationLog>) {
        rawData.forEach { data ->
            val reportPath = reportDir.resolve("${data.apkFileNameWithUnderscoresForDots}_views.txt")
            val reportData = getReportData(data)
            log.info("Writing out report $reportPath")
            Files.write(reportPath, reportData.toByteArray())
        }
    }

    private fun getReportData(data: IExplorationLog): String {
        val sb = StringBuilder()
        sb.append("Unique actionable widget\n")
                .append(data.uniqueActionableWidgets.joinToString(separator = System.lineSeparator()) { it.uniqueString })
                .append("\n====================\n")
                .append("Unique clicked widgets\n")
                .append(data.uniqueClickedWidgets.joinToString(separator = System.lineSeparator()) { it.uniqueString })

        return sb.toString()
    }
}