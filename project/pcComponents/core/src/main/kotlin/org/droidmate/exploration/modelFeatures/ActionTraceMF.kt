package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.ExplorationContext
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

class ActionTraceMF : ModelFeature() {

    override val coroutineContext: CoroutineContext = CoroutineName("ActionTraceMF")

    override suspend fun onAppExplorationFinished(context: ExplorationContext) {
        val sb = StringBuilder()
        val header = "actionNr\tType\tdecisionTime\tscreenshot\n"
        sb.append(header)

        context.explorationTrace.getActions().forEachIndexed { actionNr, record ->
            sb.appendln("$actionNr\t${record.actionType}\t${record.decisionTime}}")
        }

        val reportFile = context.getModel().config.baseDir.resolve("action_trace.txt")
        Files.write(reportFile, sb.toString().toByteArray())
    }
}