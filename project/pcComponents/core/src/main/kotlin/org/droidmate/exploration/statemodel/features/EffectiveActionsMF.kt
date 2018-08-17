package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.misc.withExtension
import org.droidmate.report.misc.plot
import org.droidmate.deviceInterface.guimodel.isClick
import java.nio.file.Files
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

class EffectiveActionsMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("EffectiveActionsMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {  /* do nothing [to be overwritten] */
        val sb = StringBuilder()
        val header = "Time_Seconds\tTotal_Actions\tTotal_Effective\n"
        sb.append(header)

        val reportData: HashMap<Long, Pair<Int, Int>> = HashMap()

        // Ignore app start
        val records = context.actionTrace.getActions().drop(1)
        val nrActions = records.size
        val startTimeStamp = records.first().startTimestamp

        var totalActions = 1
        var effectiveActions = 1

        for (i in 1 until nrActions) {
            val prevAction = records[i - 1]
            val currAction = records[i]
            val currTimestamp = currAction.startTimestamp
            val currTimeDiff = ChronoUnit.SECONDS.between(startTimeStamp, currTimestamp)

            if (actionWasEffective(prevAction, currAction))
                effectiveActions++

            totalActions++

            reportData[currTimeDiff] = Pair(totalActions, effectiveActions)

            if (i % 100 == 0)
                log.info("Processing $i")
        }

        reportData.keys.sorted().forEach { key ->
            val value = reportData[key]!!
            sb.appendln("$key\t${value.first}\t${value.second}")
        }

        val reportFile = context.getModel().config.baseDir.resolve("effective_actions.txt")
        Files.write(reportFile, sb.toString().toByteArray())

        if (includePlots) {
            log.info("Writing out plot $")
            this.writeOutPlot(reportFile, context.getModel().config.baseDir)
        }
    }

    // Currently used in child projects
    @Suppress("MemberVisibilityCanBePrivate")
    fun actionWasEffective(prevAction: ActionData, currAction: ActionData): Boolean {

        return if ((!prevAction.actionType.isClick()) ||
                (! currAction.actionType.isClick()))
            true
        else {
            currAction.prevState != currAction.resState
        }
    }

    private fun writeOutPlot(dataFile: Path, resourceDir: Path) {
        val fileName = dataFile.fileName.withExtension("pdf")
        val outFile = dataFile.resolveSibling(fileName)

        plot(dataFile.toAbsolutePath().toString(), outFile.toAbsolutePath().toString(), resourceDir)
    }
}