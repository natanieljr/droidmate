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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.AbstractMatcherFilter
import ch.qos.logback.core.spi.FilterReply

/**
 * <p>
 * Logback filter for matching logged message marker against any of the markers defined in {@link Markers}.
 *
 * </p><p>
 * Based on <a href="http://stackoverflow.com/a/8759210/986533">this stack overflow answer</a>.
 *
 * </p>
 */
class AllDroidmateMarkersFilter : AbstractMatcherFilter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent?): FilterReply {
        if (event == null)
            return onMismatch

        val marker = event.marker

        return if (!isStarted)
            FilterReply.NEUTRAL
        else if (marker == null)
            onMismatch
        else if (Markers.getAllMarkers().any { it.contains(marker) })
            onMatch
        else
            onMismatch
    }
}