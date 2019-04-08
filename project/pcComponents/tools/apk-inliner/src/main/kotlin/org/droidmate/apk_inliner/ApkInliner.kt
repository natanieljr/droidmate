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

import org.droidmate.legacy.Resource
import org.droidmate.misc.Dex
import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.IJarsignerWrapper
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.Jar
import org.droidmate.misc.JarsignerWrapper
import org.droidmate.misc.SysCmdExecutor
import org.slf4j.LoggerFactory
import java.nio.file.Files

import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ApkInliner constructor(private val sysCmdExecutor: ISysCmdExecutor,
                             private val jarsignerWrapper: IJarsignerWrapper,
                             private val inlinerJar: Jar,
                             private val appGuardLoader: Dex,
                             private val monitorClassName: String,
                             private val pathToMonitorApkOnAndroidDevice: String) {
	fun inline(inputPath: Path, outputDir: Path) {
		if (!Files.exists(inputPath))
			Files.createDirectories(inputPath)
		if (!Files.isDirectory(inputPath))
			assert(Files.exists(inputPath))
		assert(Files.isDirectory(outputDir))

		if (Files.isDirectory(inputPath)) {
			if (Files.list(inputPath).count() == 0L) {
				log.warn("No target apks for inlining found. Searched directory: ${inputPath.toRealPath().toString()}.\nAborting inlining.")
				return
			}

			Files.list(inputPath).filter { p -> p.fileName.toString() != ".gitignore" }
					.forEach { apkPath -> inlineApkIntoDir(apkPath, outputDir) }

			assert(Files.list(inputPath)
					.filter { p -> p.endsWith(".apk") }
					.count() <=
					Files.list(outputDir)
							.filter { p -> p.endsWith(".apk") }
							.count())
		} else
			inlineApkIntoDir(inputPath, outputDir)
	}

	/**
	 * <p>
	 * Inlines apk at path {@code apkPath} and puts its inlined version in {@code outputDir}.
	 *
	 * </p><p>
	 * For example, if {@code apkPath} is:
	 *
	 *   /abc/def/calc.apk
	 *
	 * and {@code outputDir} is:
	 *
	 *   /abc/def/out/
	 *
	 * then the output inlined apk will have path
	 *
	 *   /abc/def/out/calc-inlined.apk
	 *
	 * </p>
	 *
	 * @param apk
	 * @param outputDir
	 * @return
	 */
	private fun inlineApkIntoDir(apk: Path, outputDir: Path): Path {
		val unsignedInlinedApk = executeInlineApk(apk)
		assert(unsignedInlinedApk.fileName.toString().endsWith("-inlined.apk"))

		val signedInlinedApk = jarsignerWrapper.signWithDebugKey(unsignedInlinedApk)

		return Files.move(signedInlinedApk, outputDir.resolve(signedInlinedApk.fileName.toString()),
				StandardCopyOption.REPLACE_EXISTING)
	}

	private fun executeInlineApk(targetApk: Path): Path {
		val inlinedApkPath = targetApk.resolveSibling(targetApk.fileName.toString().replace(".apk", "-inlined.apk"))
		assert(Files.notExists(inlinedApkPath))

		sysCmdExecutor.execute(
				"Inlining ${targetApk.toRealPath().toString()}",
				"java", "-jar",
				inlinerJar.path.toRealPath().toString(),
				targetApk.toRealPath().toString(),
				appGuardLoader.path.toRealPath().toString(),
				pathToMonitorApkOnAndroidDevice,
				monitorClassName)

		assert(Files.exists(inlinedApkPath))
		return inlinedApkPath
	}

	companion object {
		private val log by lazy { LoggerFactory.getLogger(ApkInliner::class.java) }

		@JvmStatic
		fun build(resourceDir: Path): ApkInliner {
			val sysCmdExecutor = SysCmdExecutor()

			return ApkInliner(sysCmdExecutor,
					JarsignerWrapper(sysCmdExecutor,
							EnvironmentConstants.jarsigner.toAbsolutePath(),
							Resource("debug.keystore").extractTo(resourceDir)),
					Jar(Resource("appguard-inliner.jar").extractTo(resourceDir)),
					Dex(Resource("appguard-loader.dex").extractTo(resourceDir)),
					"org.droidmate.monitor.Monitor",
					EnvironmentConstants.AVD_dir_for_temp_files + EnvironmentConstants.monitor_apk_name)
		}
	}
}
