package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.statemodel.features.misc.ApiCountTable
import org.droidmate.exploration.statemodel.features.misc.TableDataFile
import kotlin.coroutines.experimental.CoroutineContext

class ApiCountMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ApiCountMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {
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
