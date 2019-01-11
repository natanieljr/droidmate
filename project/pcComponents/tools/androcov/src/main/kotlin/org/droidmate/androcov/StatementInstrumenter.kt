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

package org.droidmate.androcov

import com.konradjamrozik.Resource
import org.droidmate.ApkContentManager
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.IApk
import org.droidmate.helpClasses.Helper
import org.droidmate.instrumentation.Runtime
import org.droidmate.manifest.ManifestConstants
import org.droidmate.misc.*
import org.json.JSONObject
import org.slf4j.LoggerFactory
import soot.*
import soot.jimple.internal.JIdentityStmt
import soot.options.Options
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Instrument statements in an apk.
 */
class StatementInstrumenter(private val cfg: ConfigurationWrapper,
                            private val sysCmdExecutor: ISysCmdExecutor = SysCmdExecutor(),
                            private val jarsignerWrapper: IJarsignerWrapper = JarsignerWrapper(sysCmdExecutor,
									EnvironmentConstants.jarsigner.toAbsolutePath(),
									Resource("debug.keystore").extractTo(cfg.resourceDir))) {

	companion object {
		private val log by lazy { LoggerFactory.getLogger(StatementInstrumenter::class.java) }
	}

	private val allMethods = HashSet<String>()
	private val helperClasses = listOf("MonitorTcpServer",
										"Runtime",
										"SerializationHelper",
										"TcpServerBase\$1",
										"TcpServerBase\$MonitorServerRunnable",
										"TcpServerBase")
	private lateinit var helperSootClasses: List<SootClass>
	private lateinit var runtime: Runtime
	private lateinit var apkContentManager: ApkContentManager

	private lateinit var tmpOutputDir: Path

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
	fun instrument(apk: IApk, outputDir: Path): Path {
		if (!Files.exists(outputDir))
			Files.createDirectories(outputDir)
		assert(Files.isDirectory(outputDir))

		tmpOutputDir = outputDir.resolve("tmp")
		if (!Files.exists(tmpOutputDir))
			Files.createDirectories(tmpOutputDir)
		assert(Files.isDirectory(tmpOutputDir))

		allMethods.clear()

		val contentDir = tmpOutputDir.resolve("apkTool")
		apkContentManager = org.droidmate.ApkContentManager(apk.path, contentDir, tmpOutputDir)
		apkContentManager.extractApk(true)

		// Add internet permission
		Helper.initializeManifestInfo(apk.absolutePath)
		// The apk will need internet permissions to make sure that the TCP communication works
		if (!Helper.hasPermission(ManifestConstants.PERMISSION_INET)) {
			apkContentManager.addPermissionsToApp(ManifestConstants.PERMISSION_INET)
		}

		val tmpOutApk = tmpOutputDir.resolve(apk.fileName)
		apkContentManager.buildApk(tmpOutApk)

		configSoot(tmpOutApk, cfg.droidmateOutputDirPath)

		return instrumentAndSign(apk, cfg.droidmateOutputDirPath)
	}

	/**
	 * Note: Whenever you change the files in Runtime.PACKAGE, recompile them and replace the existing .class'es
	 * in the resources folder.
	 */
	@Throws(IOException::class)
	private fun configSoot(processingApk: Path, sootOutputDir: Path) {
		Options.v().set_allow_phantom_refs(true)
		Options.v().set_src_prec(Options.src_prec_apk)
		Options.v().set_output_dir(sootOutputDir.toString())
		Options.v().set_debug(true)
		Options.v().set_validate(true)
		Options.v().set_output_format(Options.output_format_dex)

		val processDirs = ArrayList<String>()
		processDirs.add(processingApk.toString())

		val resourceDir = cfg.resourceDir
			.resolve("Runtime/${Runtime.PACKAGE.replace('.', '/')}")

		helperClasses.forEach { Resource("$it.class").extractTo(resourceDir) }

		val helperDirPath = cfg.resourceDir.resolve("Runtime")
		processDirs.add(helperDirPath.toString())

		// Consider using multiplex, but it crashed for some apps
		//Options.v().set_process_multiple_dex(true)
		Options.v().set_process_dir(processDirs)

		// Consider using set_android_jars instead of set_force_android_jar
		Options.v().set_force_android_jar(EnvironmentConstants.android_jar_api23)
		//soot.options.Options.v().set_android_jars()

		Options.v().set_force_overwrite(true)
		Scene.v().loadNecessaryClasses()

		runtime = Runtime.v(Paths.get(EnvironmentConstants.AVD_dir_for_temp_files + EnvironmentConstants.coverage_port_file_name))
		helperSootClasses = helperClasses.map { Scene.v().getSootClass("${Runtime.PACKAGE}.$it") }
	}

	private fun instrumentAndSign(apk: IApk, sootOutputDir: Path): Path {
		log.info("Start instrumenting coverage...")

		val refinedPackageName = refinePackageName(apk.packageName)
		PackManager.v().getPack("jtp").add(Transform("jtp.androcov", object : BodyTransformer() {
			override fun internalTransform(b: Body, phaseName: String, options: MutableMap<String, String>) {
				val units = b.units
				// important to use snapshotIterator here
				// Skip if the current class is one of the classes we use to instrument the coverage
				if (helperSootClasses.any { it === b.method.declaringClass }) { return }
				val methodSig = b.method.signature

				if (methodSig.startsWith("<$refinedPackageName")) {
					// perform instrumentation here
					val iterator = units.snapshotIterator()
					while (iterator.hasNext()) {
						val u = iterator.next()
						val uuid = UUID.randomUUID()
						// Instrument statements
						if (u !is JIdentityStmt) {
							allMethods.add(u.toString() + " uuid=" + uuid)

							val logStatement = runtime.makeCallToStatementPoint("$methodSig uuid=$uuid")
							units.insertBefore(logStatement, u)
						}
					}
					b.validate()
				}
			}
		}))

		PackManager.v().runPacks()
		PackManager.v().writeOutput()

		cleanUp()

		val instrumentedApk = sootOutputDir.resolve(apk.fileName)
		if (Files.exists(instrumentedApk)) {
			log.info("finish instrumenting")
			val signedInlinedApk = jarsignerWrapper.signWithDebugKey(instrumentedApk)
			log.info("finish signing")
			log.info("instrumented apk: $instrumentedApk")

			writeOutput(signedInlinedApk)

			return Files.move(signedInlinedApk,
					cfg.apksDirPath.resolve(signedInlinedApk.fileName.toString().replace(".apk", "-instrumented.apk")),
					StandardCopyOption.REPLACE_EXISTING)
		} else {
			log.warn("error instrumenting")
		}
		throw DroidmateException("Failed to instrument $apk. Instrumented APK not found.")
	}

	private fun writeOutput(instrumentedApk: Path) {
		val outputMap = HashMap<String, Any>()
		val apkName = instrumentedApk.fileName.toString()
		outputMap["outputAPK"] = instrumentedApk.toString()
		outputMap["allMethods"] = allMethods
		val instrumentResultFile = cfg.droidmateOutputDirPath.resolve("$apkName.json")
		val resultJson = JSONObject(outputMap)
		try {
			Files.write(instrumentResultFile, resultJson.toString(2).toByteArray())
		} catch (e: IOException) {
			e.printStackTrace()
		}
	}

	private fun cleanUp() {
		assert(tmpOutputDir.deleteDir())
	}

	/**
	 * In the package has more than 2 parts, it returns the 2 first parts
	 * @param pkg
	 * @return
	 */
	private fun refinePackageName(pkg: String): String {
		val parts = pkg.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		return if (parts.size > 2)
			"${parts[0]}.${parts[1]}"
		else
			pkg
	}

}
