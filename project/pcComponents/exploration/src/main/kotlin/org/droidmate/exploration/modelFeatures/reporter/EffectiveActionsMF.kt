// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration.modelFeatures.reporter

import kotlinx.coroutines.CoroutineName
import org.droidmate.deviceInterface.exploration.isClick
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.misc.withExtension
import org.droidmate.exploration.modelFeatures.misc.plot
import java.nio.file.Files
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.CoroutineContext

class EffectiveActionsMF(private val includePlots: Boolean = true) : ModelFeature() {

    override val coroutineContext: CoroutineContext = CoroutineName("EffectiveActionsMF")

    /**
     * Keep track of effective actions during exploration
     * This is not used to dump the report at the end
     */
    private var totalActions = 0
    private var effectiveActions = 0

    override suspend fun onNewInteracted(traceId: UUID, targetWidgets: List<Widget>, prevState: State<*>, newState: State<*>) {
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


    override suspend fun onAppExplorationFinished(context: ExplorationContext<*, *, *>) {
        val sb = StringBuilder()
        val header = "Time_Seconds\tTotal_Actions\tTotal_Effective\n"
        sb.append(header)

        val reportData: HashMap<Long, Pair<Int, Int>> = HashMap()

        // Ignore app start
        val records = context.explorationTrace.P_getActions().drop(1)
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

        val reportFile = context.model.config.baseDir.resolve("effective_actions.txt")
        Files.write(reportFile, sb.toString().toByteArray())

        if (includePlots) {
            log.info("Writing out plot $")
            this.writeOutPlot(reportFile, context.model.config.baseDir)
        }
    }

    // Currently used in child projects
    @Suppress("MemberVisibilityCanBePrivate")
    fun actionWasEffective(prevAction: Interaction<*>, currAction: Interaction<*>): Boolean {

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