package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.deviceInterface.exploration.isClick
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.misc.withExtension
import org.droidmate.report.misc.plot
import org.droidmate.explorationModel.interaction.StateData
import org.droidmate.explorationModel.interaction.Widget
import java.nio.file.Files
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

class EffectiveActionsMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("EffectiveActionsMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
    }

    /**
     * Keep track of effective actions during exploration
     * This is not used to dump the report at the end
     */
    private var totalActions = 0
    private var effectiveActions = 0

    override suspend fun onNewInteracted(traceId: UUID, targetWidgets: List<Widget>, prevState: StateData, newState: StateData) {
        totalActions++
        if (prevState != newState)
            effectiveActions++
    }

    fun getTotalActions(): Int {
        return totalActions
    }

    fun getEffectiveActions(): Int {
        return effectiveActions
    }

    override suspend fun dump(context: ExplorationContext) {  /* do nothing [to be overwritten] */
        val sb = StringBuilder()
        val header = "Time_Seconds\tTotal_Actions\tTotal_Effective\n"
        sb.append(header)

        val reportData: HashMap<Long, Pair<Int, Int>> = HashMap()

        // Ignore app start
        val records = context.explorationTrace.getActions().drop(1)
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
    fun actionWasEffective(prevAction: Interaction, currAction: Interaction): Boolean {

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