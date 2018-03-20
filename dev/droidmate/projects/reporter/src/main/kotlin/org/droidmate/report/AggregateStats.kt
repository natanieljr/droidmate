// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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

import org.droidmate.exploration.data_aggregators.AbstractContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

class AggregateStats @JvmOverloads constructor(private val fileName: String = "aggregate_stats.txt") : IReporter {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(AggregateStats::class.java)
    }

    fun getTableData(rawData: List<AbstractContext>, path: Path): TableDataFile<Int, String, String> {
        return TableDataFile(AggregateStatsTable(rawData), path)
    }

    fun getFilePath(reportDir: Path): Path {
        return reportDir.resolve(fileName)
    }

    override fun write(reportDir: Path, rawData: List<AbstractContext>) {
        val path = getFilePath(reportDir)
        val report = getTableData(rawData, path)
        log.info("Writing out report $report")
        report.write()
    }
}