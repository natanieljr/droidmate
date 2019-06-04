package org.droidmate.exploration.modelFeatures.reporter

import org.droidmate.exploration.ExplorationContext
import java.nio.file.Path

abstract class AggregateReporterMF(reportDir: Path, resourceDir: Path) : ReporterMF(reportDir, resourceDir) {

    protected val eContexts: MutableList<ExplorationContext<*, *, *>> = mutableListOf()

    override suspend fun onAppExplorationFinished(context: ExplorationContext<*, *, *>) {
        eContexts.add(context)
        reset()
    }

    override suspend fun onFinalFinished() {
        safeWrite(eContexts)
    }

    protected abstract fun safeWrite(eContexts: List<ExplorationContext<*, *, *>>)

    override fun reset() {
        // Aggregate reporters do not need to reset anything after an app exploration
        // finished.
    }

}