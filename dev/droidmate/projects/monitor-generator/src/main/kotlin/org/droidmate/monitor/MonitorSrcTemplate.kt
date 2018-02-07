// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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

package org.droidmate.monitor

import org.droidmate.misc.BuildConstants
import java.nio.file.Files
import java.nio.file.Path

class MonitorSrcTemplate constructor(monitorSrcTemplatePath: Path, androidApi: AndroidAPI) {

    companion object {
        @JvmStatic
        private val injectionPoints_methodTargets = "GENERATED_CODE_INJECTION_POINT:METHOD_REDIR_TARGETS"
    }

    private val monitorSrcTemplate: String

    init {
        val builder = StringBuilder()

        var remove = false
        var uncomment = false

        Files.readAllLines(monitorSrcTemplatePath).forEach { line ->

            if (line.contains("// org.droidmate.monitor.MonitorSrcTemplate:REMOVE_LINES")) {
                remove = true
                uncomment = false
            } else if (line.contains("// org.droidmate.monitor.MonitorSrcTemplate:UNCOMMENT_LINES") ||
                    (androidApi == AndroidAPI.API_23 && line.contains("// org.droidmate.monitor.MonitorSrcTemplate:API_23_UNCOMMENT_LINES"))
                           ) {
                remove = false
                uncomment = true
            } else if (line.contains("// org.droidmate.monitor.MonitorSrcTemplate:KEEP_LINES")) {
                remove = false
                uncomment = false
            } else {
                if (!remove && !uncomment)
                    builder.append(line + System.lineSeparator())
                else if (!remove && uncomment) {
                    if (!line.contains("KJA")) // To-do comments Konrad Jamrozik frequently uses. Doesn't want to have them copied.
                        builder.append(line.replace("// ", "") + System.lineSeparator())
                } else {
                    assert(remove)
                    // Do nothing.
                }
            }
        }

        this.monitorSrcTemplate = builder.toString()
    }

    fun injectGeneratedCode(genMethodsTargets: String): String {
        return monitorSrcTemplate.split(System.lineSeparator()).map {
            if (it.contains(injectionPoints_methodTargets))
                genMethodsTargets
            else {
                it.replace("#POLICIES_FILE_PATH",
                        BuildConstants.AVD_dir_for_temp_files + BuildConstants.api_policies_file_name)
                it.replace("#PORT_FILE_PATH",
                        BuildConstants.AVD_dir_for_temp_files + BuildConstants.port_file_name)
            }
        }
                .joinToString(System.lineSeparator())
    }
}
