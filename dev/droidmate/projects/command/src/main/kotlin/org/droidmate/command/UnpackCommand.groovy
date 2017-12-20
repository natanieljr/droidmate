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

import groovy.io.FileType
import groovy.util.logging.Slf4j
import org.droidmate.configuration.Configuration
import org.droidmate.exploration.data_aggregators.IApkExplorationOutput2
import org.droidmate.misc.ThrowablesCollection
import org.droidmate.storage.Storage2

import java.nio.file.FileSystems
import java.nio.file.Files


@Slf4j
class UnpackCommand extends DroidmateCommand {
    static UnpackCommand build(){
        return new UnpackCommand()
    }

    @Override
    void execute(Configuration cfg) throws ThrowablesCollection {
        // Parameters
        def dirStr = cfg.apksDirName
        def outputStr = 'raw_data'
        def fs = FileSystems.default

        // Setup output dir
        def outputDir = fs.getPath(dirStr, outputStr)
        outputDir.createDirIfNotExists()

        // Initialize storage
        def droidmateOutputDirPath = fs.getPath(dirStr)
        def storage2 = new Storage2(droidmateOutputDirPath)

        // Process files
        droidmateOutputDirPath.eachFileRecurse(FileType.FILES) { file ->
            if (((String) (file + "")).contains('.ser2')) {
                outputDir = fs.getPath((String) file.getParent(), outputStr)
                outputDir.createDirIfNotExists()

                // Get data
                IApkExplorationOutput2 obj = storage2.deserialize(file)

                for (int i = 0; i < obj.actRess.size(); ++i) {
                    def newActionFile = fs.getPath((String) file.getParent(), outputStr, "action" + i + ".txt")
                    def action = obj.actRess[i].action.toString()
                    Files.write(newActionFile, action.getBytes())

                    def newResultFile = fs.getPath((String) file.getParent(), outputStr, "windowHierarchyDump" + i + ".xml")
                    String result
                    if (obj.actRess[i].result.successful)
                        result = obj.actRess[i].result.guiSnapshot.windowHierarchyDump
                    else
                        result = ""

                    Files.write(newResultFile, result.getBytes());
                }
            }
        }
    }
}
