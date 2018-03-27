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

package org.droidmate.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.AbstractMatcherFilter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.Marker
import org.slf4j.MarkerFactory

/**
 * <p>
 * Logback filter for matching logged message marker against given marker.
 *
 * </p><p>
 * Based on <a href="http://stackoverflow.com/a/8759210/986533">stack overflow answer</a>.
 *
 * </p>
 */
@Suppress("RedundantVisibilityModifier")
public class MarkerFilter : AbstractMatcherFilter<ILoggingEvent>() {
	private var markerToMatch: Marker? = null

	override fun start() {
		if (this.markerToMatch != null)
			super.start()
		else
			addError("Marker to match doesn't exist yet.")
	}

	override fun decide(event: ILoggingEvent?): FilterReply {
		return if (event == null)
			onMismatch
		else {
			val marker = event.marker
			if (!isStarted)
				return FilterReply.NEUTRAL
			else if (marker == null)
				return onMismatch
			else if (markerToMatch == null)
				return onMismatch
			else if (markerToMatch!!.contains(marker))
				return onMatch
			onMismatch
		}
	}

	// SuppressWarnings reason: used in logback.groovy, but not recognized by IntelliJ IDEA.
	@Suppress("unused")
	public fun setMarker(markerStr: String) {
		markerToMatch = MarkerFactory.getMarker(markerStr)
	}
}