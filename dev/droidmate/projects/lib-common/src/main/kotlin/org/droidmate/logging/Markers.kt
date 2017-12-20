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

package org.droidmate.logging

import org.slf4j.Marker
import org.slf4j.MarkerFactory

/**
 * <p>
 * Markers used by slf4j+logback loggers to log specific information.
 * </p><p>
 *
 * See {@code logback.groovy} for details on logging.
 *
 * </p>
 */
class Markers {
    companion object {
        /**
         * Marker for logging detailed exception information.
         */
        val exceptions = MarkerFactory.getMarker("MARKER_EXCEPTIONS")

        /**
         * Marker for logging command line executions. Such logs can be copy-pasted to console and executed for
         * quick ad-hoc debugging.
         */
        val osCmd = MarkerFactory.getMarker("MARKER_OS_CMD")

        /**
         * Denotes logs that output data about DroidMate run: input files, configuration, run time + run timestamps, etc.
         */
        val runData = MarkerFactory.getMarker("MARKER_RUN_DATA")

        val appHealth = MarkerFactory.getMarker("MARKER_HEALTH")

        fun getAllMarkers(): List<Marker>
                = arrayListOf(exceptions, osCmd, runData, appHealth)
    }
}