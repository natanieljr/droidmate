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
package org.droidmate.misc

import com.konradjamrozik.Resource
import com.konradjamrozik.asEnvDir
import com.konradjamrozik.resolveRegularFile
import org.apache.commons.lang3.StringUtils
import java.util.*

/**
 * This class contains fields whose values are necessary both by the compiled classes and by gradle build scripts compiling the
 * classes.
 *
 * The values of these fields come originally from the Gradle-special "buildSrc" project. The values have to be copied here, as
 * buildSrc is not distributed with the binary and thus any dependencies on it from the compiled classes would cause runtime
 * "NoClassDefFoundError" error.
 */
class BuildConstants {
	companion object {
		@JvmStatic
		val properties = loadProperties("buildConstants.properties")

		@JvmStatic
		val aapt_command = safeGetProperty(properties, "ANDROID_HOME", "aapt_command_relative")
		@JvmStatic
		val adb_command = safeGetProperty(properties, "ANDROID_HOME", "adb_command_relative")
		@JvmStatic
		val jarsigner = safeGetProperty(properties, "JAVA_HOME", "jarsigner_relative_path")
		@JvmStatic
		val android_jar_api23 = safeGetProperty(properties, "ANDROID_HOME", "android_jar_api23")

		@JvmStatic
		val apk_fixtures = safeGetProperty(properties, "apk_fixtures")
		@JvmStatic
		val apk_inliner_param_input = safeGetProperty(properties, "apk_inliner_param_input")
		@JvmStatic
		val apk_inliner_param_output_dir = safeGetProperty(properties, "apk_inliner_param_output_dir")
		@JvmStatic
		val apk_inliner_param_input_default = safeGetProperty(properties, "apk_inliner_param_input_default")
		@JvmStatic
		val apk_inliner_param_output_dir_default = safeGetProperty(properties, "apk_inliner_param_output_dir_default")
		@JvmStatic
		val AVD_dir_for_temp_files = safeGetProperty(properties, "AVD_dir_for_temp_files")
		@JvmStatic
		val dir_name_temp_extracted_resources = safeGetProperty(properties, "dir_name_temp_extracted_resources")
		@JvmStatic
		val coverage_monitor_script = safeGetProperty(properties, "coverage_monitor_script")
		@JvmStatic
		val monitor_generator_res_name_monitor_template = safeGetProperty(properties, "monitor_generator_res_name_monitor_template")
		@JvmStatic
		val monitor_generator_output_relative_path_api23 = safeGetProperty(properties, "monitor_generator_output_relative_path_api23")
		@JvmStatic
		val monitor_api23_apk_name = safeGetProperty(properties, "monitor_api23_apk_name")
		@JvmStatic
		val monitor_on_avd_apk_name = safeGetProperty(properties, "monitor_on_avd_apk_name")
		@JvmStatic
		val api_policies_file_name = safeGetProperty(properties, "api_policies_file_name")
		@JvmStatic
		val port_file_name = safeGetProperty(properties, "port_file_name")
		@JvmStatic
		val monitored_inlined_apk_fixture_api23_name = safeGetProperty(properties, "monitored_inlined_apk_fixture_api23_name")
		@JvmStatic
		val test_temp_dir_name = safeGetProperty(properties, "test_temp_dir_name")
		@JvmStatic
		val locale = Locale(safeGetProperty(properties, "locale"))

		@JvmStatic
		private fun loadProperties(fileName: String): Map<String, String> {
			val text = Resource(fileName).text
			val out: MutableMap<String, String> = hashMapOf()
			StringUtils.split(text, "\r\n").forEach { line ->
				if (line.isNotEmpty()) {
					val splitLine = line.split("=")
					assert(splitLine.size == 2)
					out.put(splitLine[0], splitLine[1])
				}
			}
			return out
		}

		@JvmStatic
		private fun safeGetProperty(properties: Map<String, String>, envVarName: String, key: String): String {
			assert(properties.containsKey(key))
			val value = properties[key]!!
			assert(value.isNotEmpty())

			val dir = envVarName.asEnvDir

			return dir.resolveRegularFile(value).toAbsolutePath().toString()
		}

		@JvmStatic
		private fun safeGetProperty(properties: Map<String, String>, key: String): String {
			assert(properties.containsKey(key))
			val value = properties[key]!!
			assert(value.length > 0)
			return value
		}
	}
}
