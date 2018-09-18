package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.report.misc.uniqueActionableWidgets
import org.droidmate.report.misc.uniqueClickedWidgets
import java.nio.file.Files
import kotlin.coroutines.experimental.CoroutineContext

class ApkViewsFileMF : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ApkViewsFileMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {  /* do nothing [to be overwritten] */
        val reportData = getReportData(context)
        val reportFile = context.getModel().config.baseDir.resolve("views.txt")
        Files.write(reportFile, reportData.toByteArray())
    }

    private fun getReportData(data: ExplorationContext): String {
        val sb = StringBuilder()
        sb.append("Unique actionable widget\n")
                .append(data.uniqueActionableWidgets.joinToString(separator = System.lineSeparator()) { it.uid.toString() })
                .append("\n====================\n")
                .append("Unique clicked widgets\n")
                .append(data.uniqueClickedWidgets.joinToString(separator = System.lineSeparator()) { it.uid.toString() })

        return sb.toString()
    }
}
