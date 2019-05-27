@file:Suppress("ClassName")

package org.droidmate.exploration.strategy.manual

import org.droidmate.explorationModel.interaction.Widget


private const val typeIdx = 0
private const val targetIdx = 1
const val labelIdx = 2

/**
 * @input list of string entries split by '.'
 */
typealias CandidateCreator<T,R> = (command: TargetTypeI<T, R>, w: Widget?, candidate: T?, input: List<String>)->R

/**
 * interface to map (suggested) candidates <T>(the type may be an index for mapping or a complex type if feasible) and
 * the result <R> supposed to be triggered by a specific command (like [CANDIDATE] or [FETCH]).
 * Available commands are specified below,
 * but may be extended with custom ones (these have to overwrite isValid(s,noWIdx,suggested accordingly)
 */
open class TargetTypeI<T,R>(val createCandidate: CandidateCreator<T, R>, var id: Int = -42) {
	/** true if target indices are not specified explicitly in STDIN(i.e. when the widgetTarget is passed to method fromSTDIN) */
	var noWIdx: Boolean = false

	private fun createCandidateAction(input: List<String>, w: Widget?, candidates: List<T>, suggested: T?): R {
		return when (this) {
			is ACCEPT -> createCandidate(this, w, suggested!!, input)
			is CANDIDATE -> createCandidate(this, w, candidates[id], input)
			is CLICK -> createCandidate(this, w, null, input)
			else -> createCandidate(this, w, null, input)
		}
	}

	fun exec(input: List<String>, widgetTarget: Widget?, candidates: List<T>, suggested: T?): R =
			createCandidateAction(input, widgetTarget, candidates, suggested)

	fun isValid(): Boolean = when{
		this is INVALID -> false.also { if(msg.isNotBlank()) println(msg) }
		else -> true
	}

	protected open fun isValid(s: Int, noWIdx: Boolean = false, suggested: T?): Boolean {
		return when(this) {
			is INVALID -> false
					.also { if(msg.isNotBlank()) println(msg) }
			is DEBUG -> s >= 1
			is ControlCmd -> (s == 1)
					.also{ if(!it) println("the command $this does not take any additional arguments")}
			is ACCEPT -> (s == 1 && suggested != null)
					.also { if (suggested==null)println("there is no suggested candidate to ACCEPT") else if(!it) println("ACCEPT takes no arguments") }
			is CLICK, is CANDIDATE, is NO_CANDIDATE -> (s == 1 + if(noWIdx) 0 else 1)
					.also { if(!it) println("wrong number of arguments, use the format: ' id "+if(noWIdx)"" else{". widget id"}+"'") }
			is TEXT_INPUT -> ((s==2 && noWIdx) || s == 3)
					.also { if(!it) print("wrong number of arguments, use the format: ' id. "
							+if(noWIdx)"data . additional custom args " else{ "widget id . data . additional custom args "}+"'") }
			else -> false.also { println("missing case implementation in protected fun isValid") }
		}
	}

	private val name: String get() = this::class.java.simpleName

	override fun toString(): String {
		return "$name:\t ( ${id.toString().padStart(2)} )"
	}

	open fun requiresWidget(): Boolean = false

	companion object {
		fun<T,R> isValid(noWIdx: Boolean, input: List<String>, suggested: T?,
		                 createCandidate: CandidateCreator<T, R>, options: List<TargetTypeI<T, R>>,
		                 numCandidates: Int): TargetTypeI<T, R> =
				when {
					input.size<= typeIdx || input[typeIdx].isBlank() -> INVALID(createCandidate, "You have to enter the id of the command to be executed. The options are: \n${options.joinToString(separator = "\n"){
						if(it is CANDIDATE) "CANDIDATE:\t ( 0..${numCandidates-1} )"
						else it.toString()
					}}")
					else -> {
						parse(input[typeIdx].toInt(), createCandidate, options, numCandidates)
								.let { cmd: TargetTypeI<T, R> ->
									if (cmd.isValid(input.size,noWIdx,suggested) && input.all { it.isNotBlank() }) cmd
									else INVALID(createCandidate, "the given command is invalid")
								}.also { it.noWIdx = noWIdx }
					}
				}


		@Suppress("UNCHECKED_CAST")
		private fun<T,R> parse(i: Int, createCandidate: CandidateCreator<T, R>, options: List<TargetTypeI<T, R>>,
		                       numCandidates: Int): TargetTypeI<T, R>
				= if (i in 0 until numCandidates){
			val requireWidget = options.find{ it is CANDIDATE}?.requiresWidget() ?: true
			CANDIDATE(createCandidate, i, requireWidget)
		} else options.find { it.id == i }
			?: INVALID(createCandidate,
				if (i >= 0) "cannot create Candidate $i since only $numCandidates [0..${numCandidates-1}] are available"
				else "no command available with id $i. The options are: \n" + options.joinToString(separator = "\n"){
					if(it is CANDIDATE) "CANDIDATE:\t ( 0..${options.size-1} )"
					else it.toString()
				}
			) as TargetTypeI<T, R>

	}

}

/** this command does nothing, it is only there to set a debug point and give detailed information for the given widget, if any */
class DEBUG<T,R>(createCandidate: CandidateCreator<T, R>, id: Int = -30): TargetTypeI<T, R>(createCandidate, id)

class CANDIDATE<T,R>(createCandidate: CandidateCreator<T, R>, id: Int, private val requireWidget: Boolean = true): TargetTypeI<T, R>(createCandidate, id) {  // all number 0+ matching the Candidate index in the Snippets
	override fun requiresWidget(): Boolean = requireWidget
}
class CLICK<T,R>(createCandidate: CandidateCreator<T, R>, id: Int = -1): TargetTypeI<T, R>(createCandidate, id) { // it does not match any reasonable ATD feature
	override fun requiresWidget(): Boolean = true
}
class TEXT_INPUT<T,R>(createCandidate: CandidateCreator<T, R>, id: Int = -2): TargetTypeI<T, R>(createCandidate, id){
	override fun requiresWidget(): Boolean = true
}
class ACCEPT<T,R>(createCandidate: CandidateCreator<T, R>, id: Int =-3, private val requireWidget: Boolean = true): TargetTypeI<T, R>(createCandidate, id) { // accept the suggested ATD as correct ground truth
	override fun requiresWidget(): Boolean = requireWidget
}
class NO_CANDIDATE<T,R>(createCandidate: CandidateCreator<T, R>, id: Int =-41): TargetTypeI<T, R>(createCandidate, id) { // it does not match any candidate in the current search space
	override fun requiresWidget(): Boolean = true
}
abstract class ControlCmd<T,R>(createCandidate: CandidateCreator<T, R>, id: Int): TargetTypeI<T, R>(createCandidate,id)

class BACK<T,R>(createCandidate: CandidateCreator<T, R>): ControlCmd<T, R>(createCandidate, -4)
class RESET<T,R>(createCandidate: CandidateCreator<T, R>): ControlCmd<T, R>(createCandidate, -5)
class SCROLL_RIGHT<T,R>(createCandidate: CandidateCreator<T, R>): ControlCmd<T, R>(createCandidate, -6)
class SCROLL_LEFT<T,R>(createCandidate: CandidateCreator<T, R>): ControlCmd<T, R>(createCandidate, -7)
class SCROLL_UP<T,R>(createCandidate: CandidateCreator<T, R>): ControlCmd<T, R>(createCandidate, -8)
class SCROLL_DOWN<T,R>(createCandidate: CandidateCreator<T, R>): ControlCmd<T, R>(createCandidate, -9)
class FETCH<T,R>(createCandidate: CandidateCreator<T, R>): ControlCmd<T, R>(createCandidate, -10)
class TERMINATE<T,R>(createCandidate: CandidateCreator<T, R>, id: Int = -11): ControlCmd<T, R>(createCandidate, id)
class LIST_INPUTS<T,R>(createCandidate: CandidateCreator<T, R>, id: Int = -13): ControlCmd<T, R>(createCandidate, id)
class LIST<T,R>(createCandidate: CandidateCreator<T, R>, id: Int =-14): ControlCmd<T, R>(createCandidate, id)

class INVALID<T,R>(createCandidate: CandidateCreator<T, R>, val msg:String = ""): TargetTypeI<T, R>(createCandidate)  // dummy if the input was invalid

fun <T,R, TargetType : TargetTypeI<T, R>> fromSTDIN(isValid: (input: List<String>, suggested: T?, numCandidates: Int) -> TargetType
                                                    , widgetTarget: (input: String) -> Widget?
                                                    , candidates: List<T>,
                                                    cmdMsg: String = "candidate-index . widget-Index . additional option specific args",
                                                    suggested: T? = null): R {
	println("please enter your values separated by '.' e.g. '$cmdMsg'")
	var input: List<String> = listOf()
	var t: TargetType?
	var w: Widget?
	do {
		try {
			print("$>\t")
			input = loadInput(readLine()!!)
			t = isValid(input, suggested, candidates.size)
			w = widgetTarget(if(input.size> targetIdx) input[targetIdx] else "")
			if(w == null && t.requiresWidget()) println("the command $t requires a target widget to be specified")
		}catch (e: Exception){
			t = null
			w = null
		}
	} while (t== null || !t.isValid() || (w == null && t.requiresWidget())) // allow for typos by just re-reading input if wrong number was entered
	return t.exec(input, w, candidates, suggested)
}

private val dotNotInQuotes = Regex("""\.(?=([^"]*"[^"]*")*[^"]*$)""")
private fun loadInput(line: String): List<String> = line.split(dotNotInQuotes).map {
	val trimmed = it.trim()
	when {
		trimmed.startsWith("\"") && trimmed.endsWith("\"") -> trimmed.substring(1, trimmed.length - 1).trim()
		else -> trimmed
	}
}


