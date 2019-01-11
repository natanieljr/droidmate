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

import com.konradjamrozik.*
import java.io.File
import java.nio.file.Files
import java.util.*

/**
 * This class contains fields whose values are necessary both by the compiled classes and by gradle build scripts compiling the
 * classes.
 *
 * The values of these fields come originally from the Gradle-special "buildSrc" project. The values have to be copied here, as
 * buildSrc is not distributed with the binary and thus any dependencies on it from the compiled classes would cause runtime
 * "NoClassDefFoundError" error.
 */
class EnvironmentConstants {
	companion object {

		const val apk_fixtures = "fixtures/apks"
		const val AVD_dir_for_temp_files = "/data/local/tmp/"
		const val dir_name_temp_extracted_resources = "temp_extracted_resources"
		const val monitor_generator_res_name_monitor_template = "monitorTemplate.txt"
		const val monitor_api23_apk_name = "monitor_api23.apk"
		const val monitor_on_avd_apk_name = "monitor.apk"
		const val api_policies_file_name = "api_policies.txt"
		const val monitor_port_file_name = "monitor_port.tmp"
		const val coverage_port_file_name = "coverage_port.tmp"


		private val exeExt = if (OS.isWindows) ".exe" else ""
		//region Values directly based on system environment variables
		private val java_home = "JAVA_HOME".asEnvDir
		private val android_sdk_dir = "ANDROID_HOME".asEnvDir
//endregion


		//region Android SDK components
//$ANDROID_HOME/build-tools/ lists available versions -> we want to find the highest installed version
		var max:Pair<String,Int> = Pair("",0).let {
			var max = Pair("",0)
			Files.list(android_sdk_dir.resolve("build-tools")).use { file ->
				println("available build-tools: ")
				file.forEach {
					val fileName = it.fileName.toString()
					println("\t$fileName")
					val versionCmp = fileName.replace(".","").toIntOrNull()
					if(versionCmp!=null && versionCmp > max.second)
						max = Pair(fileName,versionCmp)
				} }
			max
		}
		private val build_tools_version = max.first.also {
			println("max build tools $max")
		}

		private val aapt_command_relative = "build-tools/$build_tools_version/aapt$exeExt"
		@JvmStatic
		val aapt_command = android_sdk_dir.resolveRegularFile(aapt_command_relative).toString()
		private val adb_command_relative = "platform-tools/adb$exeExt"
		@JvmStatic
		val adb_command = android_sdk_dir.resolveRegularFile(adb_command_relative).toString()
		private val jarsigner_relative_path = "bin/jarsigner$exeExt"
		@JvmStatic
		val jarsigner = java_home.resolveRegularFile(jarsigner_relative_path)


		private val availableVersion = let{
			var minApi = Pair("",Int.MAX_VALUE)  //TODO do we really want the lowest from 23 and not the highest version?
			var anyVersion = ""
			Files.list(android_sdk_dir.resolveDir("platforms")).use{ file ->
				println("available platforms versions: ")
				file.forEach {
					val fileName = it.fileName.toString()
					anyVersion = fileName
					println("\t$fileName")
					val versionCmp = fileName.replace("android-","").toIntOrNull()
					if(versionCmp!=null && versionCmp>=23 && versionCmp < minApi.second)
						minApi = Pair(fileName,versionCmp)
				}
			}
			if(minApi.first.isBlank()) anyVersion else minApi.first
		}

		private val android_platform_dir_api23 =
				android_sdk_dir.resolveDir("platforms/$availableVersion")

		val uiautomator_jar_api23 = android_platform_dir_api23.resolveRegularFile("uiautomator.jar")
		@JvmStatic
		val android_jar_api23 = android_platform_dir_api23.resolveRegularFile("android.jar").toString()

		private const val monitor_generator_output_dir = "temp"
		private fun generatedMonitor(apiLevel: Int): String {
			return "$monitor_generator_output_dir/generated_Monitor_api$apiLevel.java"
		}
		@JvmStatic
		val monitor_generator_output_relative_path_api23 = generatedMonitor(23)

		private const val monitored_apk_fixture_api23_name = "MonitoredApkFixture_api23-debug.apk"
		@JvmStatic
		val monitored_inlined_apk_fixture_api23_name = "${monitored_apk_fixture_api23_name.removeSuffix(".apk")}-inlined.apk"
		@JvmStatic
		val test_temp_dir_name = "out${File.separator}temp_dir_for_tests"
		@JvmStatic
		val locale: Locale = Locale.US

	}
}
