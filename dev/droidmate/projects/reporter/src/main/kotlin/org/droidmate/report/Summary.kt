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
package org.droidmate.report

import com.konradjamrozik.Resource
import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.extractedText
import java.nio.file.Files
import java.nio.file.Path

class Summary @JvmOverloads constructor(val fileName: String = "summary.txt") : Reporter() {

    override fun safeWrite(reportDir: Path, rawData: List<AbstractContext>) {
        val file = reportDir.resolve(this.fileName)

        val reportData = if (rawData.isEmpty())
            "Exploration output was empty (no apks), so this summary is empty."
        else
            Resource("apk_exploration_summary_header.txt").extractedText +
                    rawData.joinToString(separator = System.lineSeparator()) { it ->
                        ApkSummary.build(it)
                    }

        Files.write(file, reportData.toByteArray())
    }
}