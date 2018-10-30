package org.droidmate.explorationModel.retention.loading

import kotlinx.coroutines.experimental.*
import org.droidmate.explorationModel.Model
import org.slf4j.Logger
import kotlin.coroutines.experimental.EmptyCoroutineContext

internal interface ParserI<T,out R> {
	val parentJob: Job?
	val model: Model

	val processor: suspend (s: List<String>) -> T
	suspend fun getElem(e: T): R

	val logger: Logger
	@Suppress("UNUSED_PARAMETER")
	fun log(msg: String)
	{}
//		 = logger.debug("[${Thread.currentThread().name}] $msg")

	fun newContext(name: String) = GlobalScope.newCoroutineContext(CoroutineName(name) + (parentJob ?: EmptyCoroutineContext))

	val compatibilityMode: Boolean
	val enableChecks: Boolean
	/** assert that a condition [c] is fulfilled or apply the [repair] function if compatibilityMode is enabled
	 * if neither [c] is fulfilled nor compatibilityMode is enabled we throw an assertion error with message [msg]
	 */
	suspend fun verify(msg:String,c: suspend ()->Boolean,repair: suspend ()->Unit){
		if(!enableChecks) return
		if(!compatibilityMode) {
			if (!c())
				throw IllegalStateException("invalid Model(enable compatibility mode to attempt transformation to valid state):\n$msg")
		} else if(!c()){
			logger.warn("had to apply repair function due to parse error '$msg' in thread [${Thread.currentThread().name}]")
			repair()
		}
	}

	/**
	 * verify that condition [c] is fulfilled and throw an [IllegalStateException] otherwise.
	 * If the model could be automatically repared please use the alternative verify method and provide a repair function
	 */
	suspend fun verify(msg:String,c: suspend ()->Boolean){
		verify(msg,c) {}
	}
}