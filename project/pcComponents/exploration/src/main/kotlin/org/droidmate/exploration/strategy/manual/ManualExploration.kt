@file:Suppress("unused")

package org.droidmate.exploration.strategy.manual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.droidmate.configuration.ConfigProperties.Exploration.launchActivityDelay
import org.droidmate.configuration.ConfigProperties.Exploration.widgetActionDelay
import org.droidmate.configuration.ConfigProperties.Output.outputDir
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.deviceInterface.exploration.LaunchApp
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.strategy.AbstractStrategy
import org.slf4j.Logger
import saarland.cispa.exploration.android.strategy.action.SwipeTo
import org.droidmate.exploration.strategy.manual.action.showTargetsInImg
import saarland.cispa.exploration.android.strategy.action.triggerTap
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

typealias ActionOnly = TargetTypeI<Int, ExplorationAction?>

/**
 * This class allows to manually navigate through by choosing action targets via keyboard input.
 * This feature may be used for debugging purposes or as base class
 * to manually construct exploration models (i.e. for GroundTruth creation).
 */
open class ManualExploration<T>(private val resetOnStart: Boolean = true) : AbstractStrategy(), StdinCommandI<T,ExplorationAction> {
	protected val state get() = eContext.getCurrentState()
	private val cfg get() = eContext.model.config
	// these properties have to be lazy since eContext is only initialized after the method initialize was called
	protected open val decisionImgDir: Path by lazy { Paths.get(eContext.cfg[outputDir].toString()) }
	private val imgFile by lazy { decisionImgDir.resolve("stateImg.jpg").toFile() }
	/** temporary state image (fetched via adb) for target highlighting. */
	private val tmpImg by lazy { Paths.get(decisionImgDir.resolve("deviceImg.png").toString()) }
	private val debugXmlFile by lazy { decisionImgDir.resolve("state.xml").toFile() }

	//			(command: ActionOnly, w: Widget?, candidate: Int?, input: List<String>)->ExplorationAction?
	override val createAction:CandidateCreator<T, ExplorationAction?> 	=
		{cmd, w, _, input ->
			val lIdx = if(!cmd.requiresWidget()) labelIdx -1 else labelIdx
			when(cmd){
				is DEBUG -> {
					debugXmlFile.writeText(eContext.lastDump)
					@Suppress("UNUSED_VARIABLE")
					val widgets = state.widgets
					w?.let{
						println("${w.id}: resId=${w.resourceId}, nlpText=${w.nlpText}")
					}
					null
				}
				is CLICK -> w?.triggerTap(delay=eContext.cfg[widgetActionDelay])
				is TEXT_INPUT ->
					if(w?.isInputField == false){
						log.error("target is no input field")
						null
					} else w?.setText(input[lIdx], sendEnter = false,delay=200)
				is BACK -> eContext.pressBack()
				is RESET -> LaunchApp(eContext.apk.packageName, eContext.cfg[launchActivityDelay])
				is SCROLL_RIGHT -> SwipeTo.right(state.widgets)
				is SCROLL_LEFT -> SwipeTo.left(state.widgets)
				is SCROLL_UP -> SwipeTo.bottom(state.widgets)
				is SCROLL_DOWN -> SwipeTo.top(state.widgets)
				is FETCH ->  GlobalAction(ActionType.FetchGUI)
				is TERMINATE -> GlobalAction(ActionType.Terminate)
				is LIST_INPUTS -> {
					println( state.actionableWidgets.mapIndexedNotNull { index, it ->  if(it.isInputField) "[$index]:\t $it" else null }
						.joinToString(separator = "\n"))
					null
				}
				is LIST ->  {
					var i=0
					println( state.actionableWidgets.joinToString(separator = "\n") { "[${i++}]:\t "+it }  )
					null
				}
				else -> throw NotImplementedError("missing case")
			}
		}

	// REMARK has to be lazy since createAction is implemented by child classes
	override val actionOptions: List<TargetTypeI<T, ExplorationAction?>> by lazy { listOf(
		CLICK(createAction),
		TEXT_INPUT(createAction),
		BACK(createAction),
		RESET(createAction),
		SCROLL_RIGHT(createAction),
		SCROLL_LEFT(createAction),
		SCROLL_UP(createAction),
		SCROLL_DOWN(createAction),
		FETCH(createAction),
		TERMINATE(createAction),
		LIST_INPUTS(createAction),
		LIST(createAction),
		DEBUG(createAction)
	) }

	override val isValid : (input: List<String>, suggested: T?, numCandidates: Int) -> TargetTypeI<T, ExplorationAction?>
			= { input: List<String>, suggested: T?, numCandidates: Int ->
		ActionOnly.isValid(false,input,suggested, createAction,	actionOptions, numCandidates)
	}

	override fun initialize(memory: ExplorationContext<*,*,*>) {
		super.initialize(memory)

		Files.deleteIfExists(tmpImg)
		imgFile.delete()
		println("\n-----------------------")
		println("available command options are:")
		println(actionOptions.joinToString(separator = "\n"))
		println("-----------------------")
		println("\tstart exploration for for ${memory.model.config.appName}")
		println("-----------------------\n")

	}

	override suspend fun internalDecide(): ExplorationAction = decideBySTDIN()

	override suspend fun decideBySTDIN(suggested: T?, candidates: List<T>): ExplorationAction{
		if(eContext.isEmpty()) {
			return if (resetOnStart) eContext.resetApp()
			else GlobalAction(ActionType.FetchGUI)
		}

		println("_____________________________")
		println(state)
		showTargetsInImg(eContext,state.actionableWidgets,imgFile)
		withContext(Dispatchers.IO){ Files.deleteIfExists(tmpImg) }

		if(state.actionableWidgets.any { !it.isVisible }) println("Note there are (invisible) target elements outside of this screen.")
		var action: ExplorationAction?
		do {
			action = fromSTDIN(isValid,
				widgetTarget = { idString -> idString.toIntOrNull()?.let { id ->
					if(id<0 || id >= state.actionableWidgets.size){
						println("ERROR no widget with id $id available")
						null
					}
					else state.actionableWidgets[id] } },
				candidates = candidates,
				suggested = suggested
			)
		}while(action == null)
		return action
	}


	companion object: Logging {
		override val log: Logger = getLogger()

		/** to activate the Strategy */
		val selector:(priority:Int)-> StrategySelector = { priority: Int ->
			StrategySelector(priority, "manual navigation", { _, pool, _ ->
				pool.getFirstInstanceOf(ManualExploration::class.java)}) }
	}

}
