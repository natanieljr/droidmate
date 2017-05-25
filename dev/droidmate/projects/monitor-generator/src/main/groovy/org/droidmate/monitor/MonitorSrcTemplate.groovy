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

import java.nio.file.Path

class MonitorSrcTemplate
{

  private final static String injectionPoints_methodTargets = "GENERATED_CODE_INJECTION_POINT:METHOD_REDIR_TARGETS"

  private final String monitorSrcTemplate

  MonitorSrcTemplate(Path monitorSrcTemplatePath, AndroidAPI androidApi)
  {
    StringBuilder builder = new StringBuilder()

    boolean remove = false
    boolean uncomment = false

    //noinspection GroovyAssignabilityCheck // shouldn't be necessary, but IntelliJ is buggy.
    monitorSrcTemplatePath.eachLine {String line ->

      if (line.contains("// org.droidmate.monitor.MonitorSrcTemplate:REMOVE_LINES"))
      {
        remove = true
        uncomment = false
      } else if (line.contains("// org.droidmate.monitor.MonitorSrcTemplate:UNCOMMENT_LINES") ||
        (androidApi == AndroidAPI.API_23 && line.contains("// org.droidmate.monitor.MonitorSrcTemplate:API_23_UNCOMMENT_LINES"))
      )
      {
        remove = false
        uncomment = true
      } else if (line.contains("// org.droidmate.monitor.MonitorSrcTemplate:KEEP_LINES"))
      {
        remove = false
        uncomment = false
      } else
      {
        if (!remove && !uncomment)
          builder.append(line + "\n")
        else if (!remove && uncomment)
        {
          if (!line.contains("KJA")) // To-do comments Konrad Jamrozik frequently uses. Doesn't want to have them copied.
            builder.append(line.replace("// ", "") + "\n")
        } else
        {
          assert remove
          // Do nothing.
        }
      }
      return null
    }
    this.monitorSrcTemplate = builder.toString()
  }

  String injectGeneratedCode(String genMethodsTargets)
  {
    return monitorSrcTemplate
      .readLines().collect {
      it.contains(injectionPoints_methodTargets) ? genMethodsTargets : it.replace("#POLICIES_FILE_PATH",
              BuildConstants.getAVD_dir_for_temp_files() + BuildConstants.getApi_policies_file_name())
    }
    .join(System.lineSeparator())
  }
}
