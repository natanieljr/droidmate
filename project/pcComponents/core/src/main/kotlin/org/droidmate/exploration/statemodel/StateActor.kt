package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.newCoroutineContext
import kotlin.coroutines.experimental.CoroutineContext

sealed class StateActor

class AddState(val elem:StateData): StateActor()
class GetStates(val response: CompletableDeferred<Collection<StateData>>): StateActor()

private val modelJob = Job()
private val context: CoroutineContext = newCoroutineContext(context = CoroutineName("StateActor"), parent = modelJob)

/** in principle adding a state would be a fast task, however due to the potential delay for the widget.uid computation
 * the state id may be delayed as well and the hash function of Widget and StateData base on this id.
 * Therefore this computation and the set management was taken from the critical path of the execution by using this actor
 */
fun stateQueue() = actor<StateActor>(context, parent = modelJob, capacity = 3) {
	val states = HashSet<StateData>() // actor state
	for (msg in channel){
		when(msg){
			is AddState -> states.add(msg.elem)
			is GetStates -> msg.response.complete(states)
		}.run{  /* do nothing but keep this .run to ensure when raises compile error if not all sealed class cases are implemented */  }
	}
}
