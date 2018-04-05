package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*

interface Actor<in E>{
	suspend fun onReceive(msg: E)
}

/** REMARK: buffered channel currently have some race condition bug when the full capacity is reached.
 * In particular, these sender are not properly woken up unless there is a print statement in the receiving loop of this actor
 * (which probably eliminates any potential race condition due to sequential console access)*/
inline fun<reified MsgType> Actor<MsgType>.create(job: Job)
		= actor<MsgType>(newCoroutineContext(CoroutineName(this.toString()),parent = job), capacity = Channel.UNLIMITED){
	for (msg in channel)	onReceive(msg)
}

/** in principle adding any element to a collection would be a fast task, however due to the potential delay for the widget.uid computation
 * the state id may be delayed as well and the hash function of Widget and StateData base on this id.
 * Therefore this computation and the set management was taken from the critical path of the execution by using this actor
 */
class CollectionActor<T>(private val actorState: MutableCollection<T>, private val actorName: String): Actor<CollectionMsg<T>>{
	override suspend fun onReceive(msg: CollectionMsg<T>){
		println("[${Thread.currentThread().name}] START msg handling ${msg::class.simpleName}: ${actorState.size}")
		when(msg){
			is Add -> actorState.add(msg.elem)
			is AddAll -> actorState.addAll(msg.elements)
			is GetAll -> msg.response.complete(actorState)
		}.run{  /* do nothing but keep this .run to ensure when raises compile error if not all sealed class cases are implemented */  }
		println("[${Thread.currentThread().name}] msg handling ${msg::class.simpleName}: ${actorState.size}")
	}
	override fun toString(): String = actorName
}
@kotlin.Suppress("unused")
sealed class CollectionMsg<T>

class Add<T>(val elem: T): CollectionMsg<T>()
class AddAll<T>(val elements: Collection<T>): CollectionMsg<T>()
class GetAll<T>(val response: CompletableDeferred<Collection<T>>): CollectionMsg<T>()

