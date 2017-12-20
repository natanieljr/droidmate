// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.logging

import com.konradjamrozik.isDirectory
import com.konradjamrozik.isRegularFile
import com.konradjamrozik.toList
import com.konradjamrozik.writeText
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LogbackUtilsRequiringLogbackLog {
    companion object {
        private val log = LoggerFactory.getLogger(LogbackUtilsRequiringLogbackLog::class.java)


        @JvmStatic
        fun cleanLogsDir() {
            val logsDir = Paths.get(LogbackConstants.LOGS_DIR_PATH)
            if (!logsDir.isDirectory)
                Files.createDirectories(logsDir)

            var msgNotDeletedLogFileNames = ""

            val invalidNames = mutableListOf(".", "..").apply { addAll(LogbackConstants.fileAppendersUsedBeforeCleanLogsDir()) }
            Files.walk(logsDir).toList()
                    .filter { it.isRegularFile }
                    .forEach { logFile ->
                        if (logFile.fileName.toString() !in invalidNames) {

                            if (!Files.deleteIfExists(logFile)) {
                                msgNotDeletedLogFileNames += " $logFile.name,"
                                logFile.writeText("")
                            }
                        }
                    }

            log(logsDir, msgNotDeletedLogFileNames)
        }

        @JvmStatic
        private fun log(logsDir: Path, logNotDeleted: String) {
            var newLogNotDeleted = logNotDeleted
            var logMsgPrefix = "Deleted old logs in directory $logsDir."

            if (!newLogNotDeleted.isEmpty()) {
                newLogNotDeleted = newLogNotDeleted.dropLast(2) // Remove trailing comma.
                logMsgPrefix += " Files that couldn't be deleted and thus had their content was instead erased:"
            }
            log.trace(logMsgPrefix + newLogNotDeleted)
        }
    }
}