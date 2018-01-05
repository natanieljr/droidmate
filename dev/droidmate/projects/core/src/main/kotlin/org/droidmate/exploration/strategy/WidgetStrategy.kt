// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.exploration.strategy

import org.droidmate.device.datatypes.IGuiState
import org.droidmate.device.datatypes.IWidget
import org.droidmate.device.datatypes.RuntimePermissionDialogBoxGuiState
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newIgnoreActionForTerminationWidgetExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newWidgetExplorationAction
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList

class WidgetStrategy constructor(randomSeed: Long,
                                 private val alwaysClickFirstWidget: Boolean,
                                 private var widgetIndexes: List<Int>) : IWidgetStrategy {
    companion object {
        private val log = LoggerFactory.getLogger(WidgetStrategy::class.java)
    }

    private val random = Random(randomSeed)


    private val widgetContexts: MutableList<WidgetContext> = ArrayList()
    private var currentWidgetContext: WidgetContext? = null
    private var lastWidgetInfo: WidgetInfo? = null
    private var repeatLastAction = false

    init {
        assert(!(alwaysClickFirstWidget && !widgetIndexes.isEmpty()))
    }

    private var firstCallToUpdateState = true
    private var alreadyUpdatedAfterLastDecide = false

    override fun updateState(guiState: IGuiState, exploredAppPackageName: String): Boolean {
        currentWidgetContext = updateWidgetContexts(guiState)

        if (!guiState.belongsToApp(exploredAppPackageName)) {
            if (firstCallToUpdateState || alreadyUpdatedAfterLastDecide) {
                // Do not blacklist anything, as either exploration just started or the current GUI state was not triggered by this
                // widget strategy.
            } else {
                if (lastWidgetInfo == null)
                    assert(guiState.isRequestRuntimePermissionDialogBox)
                else {
                    // TODO Nataniel: Review
                    //assert !lastWidgetInfo.blackListed
                    lastWidgetInfo!!.blackListed = true
                    log.debug("Blacklisted $lastWidgetInfo")
                }
            }
        }

        if (firstCallToUpdateState)
            firstCallToUpdateState = false

        if (!alreadyUpdatedAfterLastDecide)
            alreadyUpdatedAfterLastDecide = true

        return currentWidgetContext!!.all { it.blackListed }
    }

    override fun decide(guiState: IGuiState): ExplorationAction {
        alreadyUpdatedAfterLastDecide = false

        val action: ExplorationAction

        // Sometimes the runtime permission dialog is displayed upon starting the application, thus there's no previous widget
        if ((repeatLastAction) && (lastWidgetInfo != null)) {
            repeatLastAction = false

            action = chooseAction(lastWidgetInfo!!)
        } else {
            repeatLastAction = false

            if (guiState.isRequestRuntimePermissionDialogBox) {
                action = clickRuntimePermissionAllowWidget(guiState)
                repeatLastAction = true
            } else if (alwaysClickFirstWidget) {
                lastWidgetInfo = currentWidgetContext!![0]
                action = newWidgetExplorationAction(currentWidgetContext!![0].widget)
            } else if (widgetIndexes.size > 0)
                action = clickWidgetByIndex()
            else
                action = biasedRandomAction()
        }

        return action
    }

    private fun clickRuntimePermissionAllowWidget(guiState: IGuiState): WidgetExplorationAction {
        assert(guiState is RuntimePermissionDialogBoxGuiState)

        val allowButton = (guiState as RuntimePermissionDialogBoxGuiState).allowWidget

        // Remove blacklist restriction from previous action since it will need to be executed again
        if (lastWidgetInfo != null)
            lastWidgetInfo!!.blackListed = false

        return newIgnoreActionForTerminationWidgetExplorationAction(allowButton)
    }

    private fun clickWidgetByIndex(): WidgetExplorationAction {
        val widgetIndex = widgetIndexes.first()
        widgetIndexes = widgetIndexes.drop(1)

        assert(currentWidgetContext!!.size >= widgetIndex + 1)

        val chosenWidget = currentWidgetContext!![widgetIndex].widget
        val chosenWidgetInfo = currentWidgetContext!!.first { it.index == widgetIndex }

        lastWidgetInfo = chosenWidgetInfo
        return newWidgetExplorationAction(chosenWidget)
    }

    private fun biasedRandomAction(): ExplorationAction = chooseWidgetAndAction(currentWidgetContext!!)

    private fun updateWidgetContexts(guiState: IGuiState): WidgetContext {
        var currCtxt = WidgetContext.from(guiState.topNodePackageName,
                guiState.widgets
                        .filter { it.canBeActedUpon() }
                        .map { WidgetInfo.from(it) }
        )

        val eqCtxt = widgetContexts.filter { it.uniqueString == currCtxt.uniqueString }

        assert(eqCtxt.size <= 1)

        if (eqCtxt.isEmpty()) {
            // The flaw of the currently applied algorithm is that here we will have imprecise representation of the GUI if the widgets
            // seen on the screen will have their unique properties modified: if, for example, one widget is added because some
            // sub-menu got displayed, the algorithm will think it has found entirely new widget context, being exactly the same as
            // the original one, but having one new widget.
            widgetContexts.add(currCtxt)
            log.debug("Encountered NEW widget context:\n${currCtxt.toString()}")
        } else {
            currCtxt = eqCtxt[0]
            log.debug("Encountered existing widget context:\n${currCtxt.toString()}")
        }

        currCtxt.seenCount++

        return currCtxt
    }

    private fun chooseWidgetAndAction(widgetContext: WidgetContext): ExplorationAction {
        assert(widgetContext.any { !it.blackListed })
        val minActedUponCount = widgetContext.filter { !it.blackListed }.map { it.actedUponCount }.min()
        val candidates = widgetContext.filter { !it.blackListed && it.actedUponCount == minActedUponCount }

        val chosenWidgetInfo = candidates[random.nextInt(candidates.size)]

        lastWidgetInfo = chosenWidgetInfo
        // TODO Nataniel: Review
        //assert !lastWidgetInfo.blackListed

        return chooseAction(chosenWidgetInfo)
    }

    fun chooseAction(chosenWidgetInfo: WidgetInfo): ExplorationAction {
        val chosenWidget = chosenWidgetInfo.widget

        val action: ExplorationAction
        if (chosenWidget.longClickable && !chosenWidget.clickable && !chosenWidget.checkable) {
            chosenWidgetInfo.longClickedCount++
            action = newWidgetExplorationAction(chosenWidget, /* longClick */ true)

        } else if (chosenWidget.longClickable) {

            if ((chosenWidgetInfo.actedUponCount <= 1) && (chosenWidgetInfo.longClickedCount > 0))
                log.warn(Markers.appHealth,
                        "Expectation violated: (chosenWidgetInfo.actedUponCount <= 1).implies(chosenWidgetInfo.longClickedCount == 0).\n" +
                                "Actual actedUponCount:  ${chosenWidgetInfo.actedUponCount}.\n" +
                                "Actual longClickedCount: ${chosenWidgetInfo.longClickedCount}")

            // The sequence of clicks (C) and long-clicks (LC) is:
            // C, LC, C, C, LC, C, C, LC, ..., C, C, LC, ...
            if (chosenWidgetInfo.actedUponCount % 3 == 1) {
                chosenWidgetInfo.longClickedCount++
                action = newWidgetExplorationAction(chosenWidget, /* longClick */ true)
            } else
                action = newWidgetExplorationAction(chosenWidget)

        } else
            action = newWidgetExplorationAction(chosenWidget)

        chosenWidgetInfo.actedUponCount++

        log.debug("Chosen widget info: $chosenWidgetInfo")
        return action
    }

    //region Nested classes

    class WidgetContext private constructor(val widgetInfos: List<WidgetInfo>,
                                            val packageName: String) : List<WidgetInfo> by widgetInfos {

        var seenCount = 0

        companion object {
            @JvmStatic
            fun from(packageName: String, widgetInfos: List<WidgetInfo>): WidgetContext =
                    WidgetContext(widgetInfos, packageName)
        }

        val uniqueString: String
            get() = packageName + " " + this.map { it.uniqueString }.joinToString(" ")

        override fun toString(): String {
            return "WC:[seenCount=$seenCount, package=$packageName\n" +
                    this.joinToString(System.lineSeparator()) + "]"
        }
    }

    class WidgetInfo constructor(val widget: IWidget) : IWidget by widget {

        /** clicked (including checked or unchecked) + long clicked */
        var actedUponCount = 0
        var longClickedCount = 0

        var blackListed = false

        companion object {
            @JvmStatic
            fun from(widget: IWidget): WidgetInfo = WidgetInfo(widget)
        }

        val uniqueString: String
            get() {
                with(widget) {
                    return if (arrayListOf("Switch", "Toggle").any { className.contains(it) })
                        "$className[$index] $resourceId $contentDesc $bounds"
                    else
                        "$className[$index] $resourceId $text $contentDesc $bounds"
                }
            }

        override fun toString(): String =
                "WI: bl? ${if (blackListed) 1 else 0} act#: $actedUponCount lcc#: $longClickedCount ${widget.toShortString()}"
    }

    //endregion Nested classes
}
