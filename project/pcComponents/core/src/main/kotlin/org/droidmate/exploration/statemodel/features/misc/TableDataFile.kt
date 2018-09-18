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

package org.droidmate.exploration.statemodel.features.misc

import com.google.common.collect.Table
import org.droidmate.misc.withExtension
import org.droidmate.report.misc.plot
import java.nio.file.Files
import java.nio.file.Path

class TableDataFile<R, C, V>(val table: Table<R, C, V>,
                             private val file: Path) {
	fun write() {
		Files.write(file, tableString.toByteArray())
	}

	fun writeOutPlot(resourceDir: Path) {
		plot(file.toAbsolutePath().toString(), plotFile.toAbsolutePath().toString(), resourceDir)
	}

	private val tableString: String by lazy {

		val headerRowString = table.columnKeySet().joinToString(separator = "\t")

		val dataRowsStrings: List<String> = table.rowMap().map {
			val rowValues = it.value.values
			rowValues.joinToString(separator = "\t")
		}

		val tableString = headerRowString + System.lineSeparator() + dataRowsStrings.joinToString(separator = System.lineSeparator())
		tableString
	}

	private val plotFile = file.withExtension("pdf")

	override fun toString(): String {
		return file.toString()
	}
}



