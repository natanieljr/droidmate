package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import java.nio.file.Files
import kotlin.coroutines.experimental.CoroutineContext

class ActionTraceMF : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ActionTraceMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {
        val sb = StringBuilder()
        val header = "actionNr\tType\tdecisionTime\tscreenshot\n"
        sb.append(header)

        context.actionTrace.getActions().forEachIndexed { actionNr, record ->
            sb.appendln("$actionNr\t${record.actionType}\t${record.decisionTime}}")
        }

        val reportFile = context.getModel().config.baseDir.resolve("action_trace.txt")
        Files.write(reportFile, sb.toString().toByteArray())
    }
}