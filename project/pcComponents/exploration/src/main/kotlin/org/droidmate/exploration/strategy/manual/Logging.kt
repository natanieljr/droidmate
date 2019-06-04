package org.droidmate.exploration.strategy.manual

import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

// TODO this should rather be moved to the common utils module
interface Logging{
	val log: Logger
}

@Suppress("unused")
inline fun <reified T : Logging> T.getLogger(): Logger =
		getLogger(T::class.java)

