package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.ApiCountTable
import org.droidmate.exploration.modelFeatures.misc.TableDataFile
import kotlin.coroutines.CoroutineContext

class ApiCountMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val coroutineContext: CoroutineContext = CoroutineName("ApiCountMF")

    override suspend fun onAppExplorationFinished(context: ExplorationContext) {
        val dataTable = ApiCountTable(context)
        val reportPath = context.getModel().config.baseDir.resolve("apiCount.txt")
        val report = TableDataFile(dataTable, reportPath)

        report.write()
        if (includePlots) {
            log.info("Writing out plot $report")
            report.writeOutPlot(context.getModel().config.baseDir)
        }
    }
}
