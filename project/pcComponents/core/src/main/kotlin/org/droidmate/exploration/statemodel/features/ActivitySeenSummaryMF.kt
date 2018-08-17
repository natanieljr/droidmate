package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.deviceInterface.guimodel.isLaunchApp
import org.droidmate.deviceInterface.guimodel.isPressBack
import java.nio.file.Files
import kotlin.coroutines.experimental.CoroutineContext


class ActivitySeenSummaryMF : ModelFeature() {

    override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ActivitySeenSummaryMF"), parent = job)
    init{
        job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
    }

    override suspend fun dump(context: ExplorationContext) {  /* do nothing [to be overwritten] */
        val sb = StringBuilder()
        val header = "activity\tcount\n"
        sb.append(header)

        val activitySeenMap = HashMap<String, Int>()
        var lastActivity = ""
        var currActivity = context.apk.launchableActivityName

        // Always see the main activity
        activitySeenMap.put(currActivity, 1)

        context.actionTrace.getActions().forEach { record ->

            if (record.actionType.isPressBack())
                currActivity = lastActivity
            else if (record.actionType.isLaunchApp())
                currActivity = context.apk.launchableActivityName

            if (currActivity == "")
                currActivity = "<DEVICE HOME>"

            val logs = record.deviceLogs.apiLogs

            logs.filter { it.methodName.toLowerCase().startsWith("startactivit") }
                    .forEach { log ->
                        val intent = log.getIntents()
                        // format is: [ '[data=, component=<HERE>]', 'package ]
                        if (intent.isNotEmpty()) {
                            lastActivity = currActivity
                            currActivity = intent[0].substring(intent[0].indexOf("component=") + 10).replace("]", "")
                        }

                        val count = if (activitySeenMap.containsKey(currActivity))
                            activitySeenMap[currActivity]!!
                        else
                            0

                        activitySeenMap[currActivity] = count + 1
                    }
        }

        activitySeenMap.forEach { activity, count ->
            sb.appendln("$activity\t$count")
        }

        val reportFile = context.getModel().config.baseDir.resolve("activitiesSeen.txt")
        Files.write(reportFile, sb.toString().toByteArray())
    }
}