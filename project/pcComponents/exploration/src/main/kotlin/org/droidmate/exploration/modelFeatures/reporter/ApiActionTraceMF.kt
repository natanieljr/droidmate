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
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.ExplorationContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

class ApiActionTraceMF(reportDir: Path,
                       resourceDir: Path,
                       private val fileName: String = "apiActionTrace.txt") : ApkReporterMF(reportDir, resourceDir) {

    override val coroutineContext: CoroutineContext = CoroutineName("ApiActionTraceMF")

    override fun safeWriteApkReport(context: ExplorationContext, apkReportDir: Path, resourceDir: Path) {
        val sb = StringBuilder()
        val header = "actionNr\tactivity\taction\tapi\tuniqueStr\n"
        sb.append(header)

        var lastActivity = ""
        var currActivity = context.apk.launchableMainActivityName

        context.explorationTrace.getActions().forEachIndexed { actionNr, record ->

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

        val reportFile = apkReportDir.resolve(fileName)
        Files.write(reportFile, sb.toString().toByteArray())
    }

    override fun reset() {
        // Do nothing
        // Nothing to reset here
    }
}
