/*package org.droidmate.command

import com.konradjamrozik.createDirIfNotExists
import org.droidmate.androcov.StatementInstrumenter
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.AaptWrapper
import org.droidmate.exploration.ExplorationContext
import org.droidmate.misc.SysCmdExecutor
import org.droidmate.tools.ApksProvider

class CoverageCommand @JvmOverloads constructor(cfg: ConfigurationWrapper,
												private val instrumenter: StatementInstrumenter = StatementInstrumenter(cfg)) : DroidmateCommand() {
	override fun execute(cfg: ConfigurationWrapper): List<ExplorationContext> {
		val apksProvider = ApksProvider(AaptWrapper(cfg, SysCmdExecutor()))
		val apks = apksProvider.getApks(cfg.apksDirPath, 0, ArrayList(), false)

		if (apks.all { it.instrumented }) {
			log.warn("No non-instrumented apks found. Aborting.")
			return emptyList()
		}

		val originalsDir = cfg.apksDirPath.resolve("originals").toAbsolutePath()
		if (originalsDir.createDirIfNotExists())
			log.info("Created directory to hold original apks, before instrumenting: $originalsDir")

		if (apks.size > 1)
			log.warn("More than one no-instrumented apk on the input dir. Instrumenting only the first one.")

		val apk = apks.first { !it.instrumented }
		instrumenter.instrument(apk, apk.path.parent)
		log.info("Instrumented ${apk.fileName}")
		moveOriginal(apk, originalsDir)

		return emptyList()
	}
}*/