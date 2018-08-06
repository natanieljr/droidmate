package org.droidmate.androcov

import com.konradjamrozik.Resource
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.IApk
import org.droidmate.misc.*
import org.json.JSONObject
import org.slf4j.LoggerFactory
import soot.*
import soot.jimple.Jimple
import soot.jimple.StringConstant
import soot.jimple.internal.JIdentityStmt
import soot.options.Options
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Instrument statements in apk
 */
class StatementInstrumenter(private val cfg: ConfigurationWrapper,
							private val sysCmdExecutor: ISysCmdExecutor = SysCmdExecutor(),
							private val jarsignerWrapper: IJarsignerWrapper = JarsignerWrapper(sysCmdExecutor,
									cfg.getPath(BuildConstants.jarsigner),
									Resource("debug.keystore").extractTo(cfg.resourceDir))) {
	private val allMethods = HashSet<String>()

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

		allMethods.clear()

		configSoot(apk)

		return instrumentAndSign(apk)
	}


	@Throws(IOException::class)
	private fun configSoot(apk: IApk) {
		Options.v().set_allow_phantom_refs(true)
		Options.v().set_src_prec(Options.src_prec_apk)
		Options.v().set_output_dir(cfg.droidmateOutputDirPath.toString())
		Options.v().set_debug(true)
		Options.v().set_validate(true)
		Options.v().set_output_format(Options.output_format_dex)

		val processDirs = ArrayList<String>()
		//processDirs.add(cfg.apksDirPath.toString())
		processDirs.add(apk.absolutePath)

		// NOTE: If you change the CoverageHelper.java class, recompile it!
		val resourceDir = cfg.resourceDir
				.resolve("CoverageHelper/xyz/ylimit/androcov")

		//Resource("CoverageHelper.java").extractTo(resourceDir)
		Resource("CoverageHelper.java").extractTo(resourceDir)

		val helperDirPath = cfg.resourceDir.resolve("CoverageHelper")
		processDirs.add(helperDirPath.toString())

		Options.v().set_process_dir(processDirs)
		Options.v().set_force_android_jar(BuildConstants.android_jar_api23)
		Options.v().set_force_overwrite(true)
	}

	private fun instrumentAndSign(apk: IApk): Path {
		log.info("Start instrumenting...")

		Scene.v().loadNecessaryClasses()
		val helperClass = Scene.v().getSootClass("xyz.ylimit.androcov.CoverageHelper")
		val helperMethod = helperClass.getMethodByName("reach")

		val refinedPackageName = refinePackageName(apk.packageName)
		PackManager.v().getPack("jtp").add(Transform("jtp.androcov", object : BodyTransformer() {
			override fun internalTransform(b: Body, phaseName: String, options: MutableMap<String, String>) {
				val units = b.units
				// important to use snapshotIterator here
				if (b.method.declaringClass === helperClass) return
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
							val logStatement = Jimple.v().newInvokeStmt(
									Jimple.v().newStaticInvokeExpr(helperMethod.makeRef(), StringConstant.v("$methodSig uuid=$uuid")))
							units.insertBefore(logStatement, u)
						}
					}
					b.validate()
				}
			}
		}))

		PackManager.v().runPacks()
		PackManager.v().writeOutput()
		val instrumentedApk = cfg.droidmateOutputDirPath.resolve(apk.fileName)
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

	companion object {
		private val log by lazy { LoggerFactory.getLogger(StatementInstrumenter::class.java) }
	}
}
