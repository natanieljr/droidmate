package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.TableDataFile
import org.droidmate.exploration.modelFeatures.misc.WidgetSeenClickedTable
import kotlin.coroutines.experimental.CoroutineContext

class WidgetSeenClickedCountMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("WidgetApiTraceMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {
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