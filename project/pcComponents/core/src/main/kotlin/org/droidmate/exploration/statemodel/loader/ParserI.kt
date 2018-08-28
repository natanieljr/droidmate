package org.droidmate.exploration.statemodel.loader

import kotlinx.coroutines.experimental.*
import org.droidmate.exploration.statemodel.Model
import kotlin.coroutines.experimental.coroutineContext

/**
 * if [isSequential]==true @T is Deferred<R> and otherwise we directly compute @R
 * we need a reference to the model in order to add the States and Widgets, TODO alternatively we could return the queues to the calling instance and have them added by the main thread
 */
interface ParserI<T,out R> {
	val isSequential: Boolean
	val parentJob: Job?
	val model: Model

	val processor: suspend (s: List<String>) -> T
	suspend fun getElem(e: T): R

	@Suppress("UNUSED_PARAMETER")
	fun log(msg: String)
	{}
//		 = println("[${Thread.currentThread().name}] $msg")

	suspend fun context(name:String, parent: Job? = parentJob) = newCoroutineContext(context = CoroutineName(name), parent = coroutineContext[Job])
	fun newContext(name: String) = newCoroutineContext(context = CoroutineName(name), parent = parentJob)

	val compatibilityMode: Boolean
	/** assert that a condition [c] is fulfilled or apply the [repair] function if compatibilityMode is enabled
	 * if neither [c] is fulfilled nor compatibilityMode is enabled we throw an assertion error with message [msg]
	 */
	suspend fun verify(msg:String,c: suspend ()->Boolean,repair: suspend ()->Unit){
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