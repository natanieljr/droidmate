package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.uniqueActionableWidgets
import org.droidmate.exploration.modelFeatures.misc.uniqueClickedWidgets
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

class ApkViewsFileMF : ModelFeature() {

    override val coroutineContext: CoroutineContext = CoroutineName("ApkViewsFileMF")

    override suspend fun onAppExplorationFinished(context: ExplorationContext) {
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
