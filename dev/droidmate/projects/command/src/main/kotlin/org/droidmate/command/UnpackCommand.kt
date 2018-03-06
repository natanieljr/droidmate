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
package org.droidmate.command

import com.konradjamrozik.createDirIfNotExists
import com.konradjamrozik.isRegularFile
import org.droidmate.configuration.Configuration
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.misc.ThrowablesCollection
import org.droidmate.storage.Storage2
import java.nio.file.FileSystems

import java.nio.file.Files


class UnpackCommand : DroidmateCommand() {
    companion object {
        @JvmStatic
        fun build(): UnpackCommand = UnpackCommand()
    }

    @Throws(ThrowablesCollection::class)
    override fun execute(cfg: Configuration) {
        // Parameters
        val dirStr = cfg.apksDirName
        val outputStr = "raw_data"
        val fs = FileSystems.getDefault()

        // Setup output dir
        var outputDir = fs.getPath(dirStr, outputStr)
        outputDir.createDirIfNotExists()

        // Initialize storage
        val droidmateOutputDirPath = fs.getPath(dirStr)
        val storage2 = Storage2(droidmateOutputDirPath)

        // Process files
        Files.walk(droidmateOutputDirPath)
                .filter { it.isRegularFile }
                .forEach { file ->

                    if (file.toString().endsWith(".ser2")) {
                        outputDir = fs.getPath(file.parent.toString(), outputStr)
                outputDir.createDirIfNotExists()

                        val obj = storage2.deserialize(file) as IExplorationLog

                        for (i in 0 until obj.logRecords.size) {
                            val newActionFile = fs.getPath(file.parent.toString(), outputStr, "action$i.txt")
                            val action = obj.logRecords[i].getAction().toString()
                            Files.write(newActionFile, action.toByteArray())

                            val newResultFile = fs.getPath(file.parent.toString(), outputStr, "windowHierarchyDump$i.xml")

                            val result = if (obj.logRecords[i].getResult().successful)
                                obj.logRecords[i].getResult().guiSnapshot.windowHierarchyDump
                    else
                                ""

                            Files.write(newResultFile, result.toByteArray())
                }
            }
        }
    }
}
