package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*

class WidgetActor: Actor<WidgetMsg>{
	private val widgets = HashSet<Widget>() // actor state
	override suspend fun onReceive(msg: WidgetMsg){
		when(msg){
			is AddWidget -> widgets.add(msg.elem)
			is AddWidgets -> widgets.addAll(msg.elements)
			is GetWidgets -> msg.response.complete(widgets)
		}.run{ /* do nothing but keep this .run to ensure when raises compile error if not all sealed class cases are implemented */  }
//		println("[${Thread.currentThread().name}] CONSUME Widget ${widgets.size}")
	}
}

sealed class WidgetMsg

class AddWidget(val elem:Widget): WidgetMsg()
class AddWidgets(val elements:Collection<Widget>): WidgetMsg()
class GetWidgets(val response: CompletableDeferred<Collection<Widget>>): WidgetMsg()

interface Actor<in E>{
	suspend fun onReceive(msg: E)
}

/** REMARK: buffered channel currently have some race condition bug when the full capacity is reached.
 * In particular, these sender are not properly woken up unless there is a print statement in the receiving loop of this actor
 * (which probably eliminates any potential race condition due to sequential console access)*/
inline fun<reified MsgType> Actor<MsgType>.create(job: Job)
		= actor<MsgType>(newCoroutineContext(CoroutineName(this.javaClass.simpleName),parent = job), capacity = Channel.UNLIMITED){
	for (msg in channel)	onReceive(msg)
}
