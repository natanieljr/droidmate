package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.newCoroutineContext
import kotlin.coroutines.experimental.CoroutineContext

sealed class WidgetActor

class AddWidget(val elem:Widget): WidgetActor()
class AddWidgets(val elements:Collection<Widget>): WidgetActor()
class GetWidgets(val response: CompletableDeferred<Collection<Widget>>): WidgetActor()

private val modelJob = Job()
private val context: CoroutineContext = newCoroutineContext(context = CoroutineName("WidgetActor"), parent = modelJob)

fun widgetQueue() = actor<WidgetActor>(context, parent = modelJob, capacity = 3) {
	val widgets = HashSet<Widget>() // actor state
	for (msg in channel){
		when(msg){
			is AddWidget -> widgets.add(msg.elem)
			is AddWidgets -> widgets.addAll(msg.elements)
			is GetWidgets -> msg.response.complete(widgets)
		}.run{  /* do nothing but keep this .run to ensure when raises compile error if not all sealed class cases are implemented */  }
	}
}
