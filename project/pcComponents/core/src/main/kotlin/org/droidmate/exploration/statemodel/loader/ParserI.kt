package org.droidmate.exploration.statemodel.loader

import kotlinx.coroutines.experimental.*
import org.droidmate.exploration.statemodel.Model
import org.slf4j.LoggerFactory
import kotlin.coroutines.experimental.coroutineContext

internal interface ParserI<T,out R> {
	val parentJob: Job?
	val model: Model

	val processor: suspend (s: List<String>) -> T
	suspend fun getElem(e: T): R

//	@Suppress("UNUSED_PARAMETER")
//	fun log(msg: String)
//	{}
//		 = println("[${Thread.currentThread().name}] $msg")

	suspend fun context(name:String, parent: Job? = parentJob) = newCoroutineContext(context = CoroutineName(name), parent = coroutineContext[Job])
	fun newContext(name: String) = newCoroutineContext(context = CoroutineName(name), parent = parentJob)

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
			println("WARN: had to apply repair function")
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