package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.ClickFrequencyTable
import org.droidmate.exploration.modelFeatures.misc.TableDataFile
import kotlin.coroutines.CoroutineContext

class ClickFrequencyMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val coroutineContext: CoroutineContext = CoroutineName("ClickFrequencyMF")

    override suspend fun onAppExplorationFinished(context: ExplorationContext) {
        val dataTable = ClickFrequencyTable(context)
        val reportPath = context.getModel().config.baseDir.resolve("clickFrequency.txt")
        val report = TableDataFile(dataTable, reportPath)

        report.write()
        if (includePlots) {
            log.info("Writing out plot $report")
            report.writeOutPlot(context.getModel().config.baseDir)
        }
    }
}