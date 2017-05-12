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

import groovy.util.logging.Slf4j
import org.droidmate.configuration.Configuration
import org.droidmate.misc.BuildConstants
import org.droidmate.misc.ThrowablesCollection
import org.droidmate.monitor.MonitorGeneratorFrontend

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@Slf4j
class GenerateMonitorCommand extends DroidmateCommand{
    static GenerateMonitorCommand build()
    {
        return new GenerateMonitorCommand()
    }

    @Override
    void execute(Configuration cfg) throws ThrowablesCollection
    {
        Path actualMonitorJava = Paths.get(BuildConstants.monitor_generator_output_relative_path_api23)
        assert Files.notExists(actualMonitorJava) || Files.isWritable(actualMonitorJava)

        MonitorGeneratorFrontend.handleException = { Exception e -> throw e }

        // Act
        MonitorGeneratorFrontend.main(["api23"] as String[])

        assert Files.isRegularFile(actualMonitorJava)
        String actualText = actualMonitorJava.text
        assert !actualText.contains("public class MonitorJavaTemplate")
        assert actualText.contains("public class Monitor")
    }
}
