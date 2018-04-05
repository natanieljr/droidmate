package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.CompletableDeferred

/** in principle adding a state would be a fast task, however due to the potential delay for the widget.uid computation
 * the state id may be delayed as well and the hash function of Widget and StateData base on this id.
 * Therefore this computation and the set management was taken from the critical path of the execution by using this actor
 */
class StateActor: Actor<StateMsg>{
	private val states = HashSet<StateData>() // actor state
	override suspend fun onReceive(msg: StateMsg){
		when(msg){
			is AddState -> states.add(msg.elem)
			is GetStates -> msg.response.complete(states)
		}.run{  /* do nothing but keep this .run to ensure when raises compile error if not all sealed class cases are implemented */  }
	}
}

sealed class StateMsg

class AddState(val elem:StateData): StateMsg()
class GetStates(val response: CompletableDeferred<Collection<StateData>>): StateMsg()

