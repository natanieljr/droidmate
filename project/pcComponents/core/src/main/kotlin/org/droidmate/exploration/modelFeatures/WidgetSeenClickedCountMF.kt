package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.TableDataFile
import org.droidmate.exploration.modelFeatures.misc.WidgetSeenClickedTable
import kotlin.coroutines.CoroutineContext

class WidgetSeenClickedCountMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val coroutineContext: CoroutineContext = CoroutineName("WidgetApiTraceMF")

    override suspend fun onAppExplorationFinished(context: ExplorationContext) {
        val dataTable = WidgetSeenClickedTable(context)

        val reportPath = context.getModel().config.baseDir.resolve("viewCount.txt")
        val report = TableDataFile(dataTable, reportPath)

        report.write()
        if (includePlots) {
            log.info("Writing out plot $report")
            report.writeOutPlot(context.getModel().config.baseDir)
        }
    }
}