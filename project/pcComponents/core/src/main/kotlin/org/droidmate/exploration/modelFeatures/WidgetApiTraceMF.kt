package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.deviceInterface.guimodel.isClick
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.ActionData
import org.droidmate.explorationModel.StateData
import org.droidmate.explorationModel.Widget
import java.nio.file.Files
import kotlin.coroutines.experimental.CoroutineContext

class WidgetApiTraceMF : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("WidgetApiTraceMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {
        val sb = StringBuilder()
        val header = "actionNr\ttext\tapi\tuniqueStr\taction\n"
        sb.append(header)

        context.actionTrace.getActions().forEachIndexed { actionNr, record ->
            if (record.actionType.isClick()) {
                val text = runBlocking { context.getState(record.resState)?.let { getActionWidget(record, it) } }
                val logs = record.deviceLogs
                val widget = record.targetWidget

                logs.forEach { ApiLogcatMessage.from(it).let { log ->
                    sb.appendln("$actionNr\t$text\t${log.objectClass}->${log.methodName}\t$widget\t${log.uniqueString}")
                }}
            }
        }

        val reportFile = context.getModel().config.baseDir.resolve("widget_api_trace.txt")
        Files.write(reportFile, sb.toString().toByteArray())
    }

    private fun getActionWidget(actionResult: ActionData, state: StateData): Widget? {
        return if (actionResult.actionType.isClick()) {

            getWidgetWithTextFromAction(actionResult.targetWidget!!, state)
        } else
            null
    }

    private fun getWidgetWithTextFromAction(widget: Widget, state: StateData): Widget {
        // If has Text
        if (widget.text.isNotEmpty())
            return widget

        val children = state.widgets
                .filter { p -> p.parentId == widget.id }

        // If doesn't have any children
        if (children.isEmpty()) {
            return widget
        }

        val childrenWithText = children.filter { p -> p.text.isNotEmpty() }

        return when {
            // If a single children have text
            childrenWithText.size == 1 -> childrenWithText.first()

            // Single child, drill down
            children.size == 1 -> getWidgetWithTextFromAction(children.first(), state)

            // Multiple children, skip
            else -> widget
        }
    }
}