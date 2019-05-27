package org.droidmate.exploration.strategy.manual



interface StdinCommandI<T,R> {
	val createAction:CandidateCreator<T, R?>
	val actionOptions: List<TargetTypeI<T, R?>>

	val isValid : (input: List<String>, suggested: T?, numCandidates: Int) -> TargetTypeI<T, R?>

	suspend fun decideBySTDIN(suggested: T? = null, candidates: List<T> = emptyList()): R {
		println("_____________________________")
		var action: R?
		do {
			action = fromSTDIN(isValid,
				widgetTarget = { null },
				candidates = candidates,
				suggested = suggested
			)
		}while(action == null)
		return action
	}
}

@Suppress("ClassName")
class LIST_C<T,R>(createCandidate: CandidateCreator<T, R>, id: Int): ControlCmd<T, R>(createCandidate,  id)
