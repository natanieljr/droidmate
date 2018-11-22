package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.ExplorationContext
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

class ApiActionTraceMF : ModelFeature() {

    override val coroutineContext: CoroutineContext = CoroutineName("ApiActionTraceMF")

    override suspend fun onAppExplorationFinished(context: ExplorationContext) {
        val sb = StringBuilder()
        val header = "actionNr\tactivity\taction\tapi\tuniqueStr\n"
        sb.append(header)

        var lastActivity = ""
        var currActivity = context.apk.launchableMainActivityName

        context.explorationTrace.P_getActions().forEachIndexed { actionNr, record ->

            if (record.actionType.isPressBack())
                currActivity = lastActivity
            else if (record.actionType.isLaunchApp())
                currActivity = context.apk.launchableMainActivityName

            val logs = record.deviceLogs

            logs.forEach { ApiLogcatMessage.from(it).let { log ->
                if (log.methodName.toLowerCase().startsWith("startactivit")) {
                    val intent = log.getIntents()
                    // format is: [ '[data=, component=<HERE>]', 'package ]
                    if (intent.isNotEmpty()) {
                        lastActivity = currActivity
                        currActivity = intent[0].substring(intent[0].indexOf("component=") + 10).replace("]", "")
                    }
                }

                sb.appendln("$actionNr\t$currActivity\t${record.actionType}\t${log.objectClass}->${log.methodName}\t${log.uniqueString}")
            }}
        }

        val reportFile = context.getModel().config.baseDir.resolve("apiActionTrace.txt")
        Files.write(reportFile, sb.toString().toByteArray())
    }
}
