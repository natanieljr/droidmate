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
import org.droidmate.legacy.Resource
import org.droidmate.misc.getTextFromExtractedResource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

class Summary @JvmOverloads constructor(reportDir: Path, resourceDir: Path, val fileName: String = "summary.txt")
    : AggregateReporterMF(reportDir, resourceDir) {

	override val coroutineContext: CoroutineContext = CoroutineName("AggregateStatsMF")

    override fun safeWrite(eContexts: List<ExplorationContext>) {
		val file = reportDir.resolve(this.fileName)

		val reportData = if (eContexts.isEmpty())
			"Exploration output was empty (no apks), so this summary is empty."
		else
			Resource("apk_exploration_summary_header.txt").getTextFromExtractedResource(resourceDir) +
					eContexts.joinToString(separator = System.lineSeparator()) { it ->
						ApkSummary.build(it)
					}

		Files.write(file, reportData.toByteArray())
	}
}