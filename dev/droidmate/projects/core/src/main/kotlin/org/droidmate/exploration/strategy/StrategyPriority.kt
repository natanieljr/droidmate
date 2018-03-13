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
package org.droidmate.exploration.strategy

/**
 * Defines the priority of the exploration strategy results. Currently the order is:
 * - First reset has maximum priority
 * - "Terminate exploration" (time, actions or all targets found)
 * - Specific widget (Permission dialog, playback, always first)
 * - Pressing back (according to probability)
 * - "Normal" Reset (cannot proceed)
 * - Biased Random widget (ex: model based)
 * - Purely Random widget
 * - None (when the strategy shouldn't do anything)
 */
enum class StrategyPriority(val value: Double) {
    PLAYBACK(0.95),
    FIRST_RESET(0.9),
    BACK_BEFORE_TERMINATE(0.81),
    TERMINATE(0.8),
    APP_CRASHED_RESET(0.75),
    SPECIFIC_WIDGET(0.7),
    BACK(0.6),
    RESET(0.5),
    BIASED_RANDOM_WIDGET(0.2),
    PURELY_RANDOM_WIDGET(0.1),
    NONE(0.0)
}