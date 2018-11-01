package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.ClickFrequencyTable
import org.droidmate.exploration.modelFeatures.misc.TableDataFile
import kotlin.coroutines.experimental.CoroutineContext

class ClickFrequencyMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ClickFrequencyMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {  /* do nothing [to be overwritten] */
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