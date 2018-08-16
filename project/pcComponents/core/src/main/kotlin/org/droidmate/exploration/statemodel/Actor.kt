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

@file:Suppress("ReplaceSingleLineLet")

package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*

interface Actor<in E>{
	suspend fun onReceive(msg: E)
}

internal fun actorThreadPool(name:String) = newFixedThreadPoolContext (1,name="actor-thread:$name")

/** REMARK: buffered channel currently have some race condition bug when the full capacity is reached.
 * In particular, these sender are not properly woken up unless there is a print statement in the receiving loop of this actor
 * (which probably eliminates any potential race condition due to sequential console access)*/
internal inline fun<reified MsgType> Actor<MsgType>.create(job: Job)
		= actor<MsgType>(newCoroutineContext(context = actorThreadPool(this.toString()),parent = job), capacity = Channel.UNLIMITED){
	for (msg in channel)	onReceive(msg)
}

/** in principle adding any element to a collection would be a fast task, however due to the potential timeout for the widget.uid computation
 * the state id may be delayed as well and the hash function of Widget and StateData base on this id.
 * Therefore this computation and the set management was taken from the critical path of the execution by using this actor
 */
class CollectionActor<T>(private val actorState: MutableCollection<T>, private val actorName: String): Actor<CollectionMsg<T,Any?>>{
	override suspend fun onReceive(msg: CollectionMsg<T,Any?>){
//		println("[${Thread.currentThread().name}] START msg handling ${msg::class.simpleName}: ${actorState.size}")
		when(msg){
			is Add -> actorState.add( msg.elem )
			is AddAll -> actorState.addAll( msg.elements )
			is Get -> msg.response.complete( msg.predicate(actorState) )
			is GetAll ->
				if(actorState is Set<*>) msg.response.complete( actorState.toSet() )
				else msg.response.complete( actorState.toList() )
		} //.run{  /* do nothing but keep this .run to ensure when raises compile error if not all sealed class cases are implemented */  }
//		println("[${Thread.currentThread().name}] msg handling ${msg::class.simpleName}: ${actorState.size}")
	}
	override fun toString(): String = actorName
}
@kotlin.Suppress("unused")
sealed class CollectionMsg<T,out R>

class Add<T>(val elem: T): CollectionMsg<T,Any>()
class AddAll<T>(val elements: Collection<T>): CollectionMsg<T,Any>()
class GetAll<T,R>(val response: CompletableDeferred<R>): CollectionMsg<T,R>()
/** this method allows to retrieve a specific element as determined by @predicate,
 * however the predicate should only contain very cheap computations as otherwise the WHOLE exploration performance would suffer.
 * For more expensive computations please retrieve the whole collection via [GetAll] and perform your operation on the result
 */
class Get<T,R>(inline val predicate:(Collection<T>)->R, val response: CompletableDeferred<R>): CollectionMsg<T,R>()

/** this method allows to retrieve a specific element as determined by @predicate,
 * however the predicate should only contain very cheap computations as otherwise the WHOLE exploration performance would suffer.
 * For more expensive computations please retrieve the whole collection via [getAll] and perform your operation on the result
 */
suspend inline fun<reified T, reified R: Any?> SendChannel<CollectionMsg<T,R?>>.getOrNull(noinline predicate:(Collection<T>)->R): R?
		= this.let{actor -> with(CompletableDeferred<R>()){ actor.send(Get(predicate,this)); this.await()} }

/** this method allows to retrieve a specific element as determined by @predicate,
 * however the predicate should only contain very cheap computations as otherwise the WHOLE exploration performance would suffer.
 * For more expensive computations please retrieve the whole collection via [getAll] and perform your operation on the result
 */
suspend inline fun<reified T, reified R: Any> SendChannel<CollectionMsg<T,R?>>.get(noinline predicate:(Collection<T>)->R): R
		= this.let{actor -> with(CompletableDeferred<R?>()){ actor.send(Get(predicate,this)); this.await()} }!!

/** @return a copy of the actors current private collection (actorState) */
suspend inline fun<reified T, reified R> SendChannel<CollectionMsg<T,R>>.getAll(): R
		= this.let{actor -> with(CompletableDeferred<R>()){ actor.send(GetAll(this)); this.await()} }

/** @return (blocking) a copy of the actors current private collection (actorState) */
inline fun<reified T, reified R> SendChannel<CollectionMsg<T,R>>.S_getAll(): R
		= this.let{actor -> with(CompletableDeferred<R>()){ actor.sendBlocking(GetAll(this)); runBlocking {await()}} }
