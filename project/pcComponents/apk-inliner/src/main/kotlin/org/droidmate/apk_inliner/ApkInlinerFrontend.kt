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

package org.droidmate.apk_inliner

import joptsimple.OptionParser
import org.droidmate.misc.BuildConstants
import org.slf4j.LoggerFactory

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

class ApkInlinerFrontend {

	companion object {
		private val log = LoggerFactory.getLogger(ApkInlinerFrontend::class.java)

		@JvmStatic
		fun main(args: Array<String>) {
			try {
				val paths = parseArgs(args)
				val inputPath = paths[0]
				val outputPath = paths[1]
				val apkInliner = ApkInliner.build()
				apkInliner.inline(inputPath, outputPath)
			} catch (e: Exception) {
				handleException(e)
			}
		}

		@JvmStatic
		private fun parseArgs(args: Array<String>): List<Path> {
			assert(args.isEmpty() || args[0][0] == '-')

			val parser = OptionParser()

			val inputParam = BuildConstants.apk_inliner_param_input.drop(1)
			val outputParam = BuildConstants.apk_inliner_param_output_dir.drop(1)

			val path = PathValueConverter.pathIn(FileSystems.getDefault())
			parser.accepts(inputParam).withOptionalArg().defaultsTo(path.convert(BuildConstants.apk_inliner_param_input_default).toString()).withValuesConvertedBy(path)
			parser.accepts(outputParam).withRequiredArg().defaultsTo(path.convert(BuildConstants.apk_inliner_param_output_dir_default).toString()).withValuesConvertedBy(path)

			val options = parser.parse(*args)

			val inputPath = Paths.get(options.valueOf(inputParam).toString())
			val outputPath = Paths.get(options.valueOf(outputParam).toString())

			return arrayListOf(inputPath, outputPath)
		}

		@JvmStatic
		var handleException: (Exception) -> Any =
				{
					log.error("Exception was thrown and propagated to the frontend.", it)
					System.exit(1)
				}
	}
}