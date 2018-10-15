package org.droidmate

import org.droidmate.command.InlineCommand
import org.droidmate.configuration.ConfigurationWrapper
import org.slf4j.LoggerFactory

@Suppress("unused")
object Instrumentation {
	private val log by lazy { LoggerFactory.getLogger(Instrumentation::class.java) }

	/****************************** Apk-Inline API methods *****************************/
	@JvmStatic
	@JvmOverloads
	fun inline(args: Array<String> = emptyArray()) {
		inline(setup(args))
	}

	@JvmStatic
	fun inline(cfg: ConfigurationWrapper) {
		log.info("inline the apks if necessary")
		InlineCommand(cfg).execute(cfg)
	}
}