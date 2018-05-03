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
package org.droidmate.report.misc

import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.AbstractContext
import org.droidmate.exploration.statemodel.emptyUUID
import java.util.*

val AbstractContext.uniqueActionableWidgets: Set<Widget>
	get() = mutableSetOf<Widget>().apply {	runBlocking {
		getModel().getWidgets().filter { it.canBeActedUpon() }.groupBy { it.uid } // TODO we would like a mechanism to identify which widget config was the (default)
				.forEach { add(it.value.first()) }
	} }

val AbstractContext.uniqueClickedWidgets: Set<Widget>
	get() = mutableSetOf<Widget>().apply {
		actionTrace.getActions().forEach { action -> action.targetWidget?.let { add(it) } }
	}

//TODO not sure about the original intention of this function
val AbstractContext.uniqueApis: Set<IApiLogcatMessage>
	get() = uniqueEventApiPairs.map { (_, api) -> api }.toSet()

val AbstractContext.uniqueEventApiPairs: Set<Pair<UUID, IApiLogcatMessage>>
	get() = mutableSetOf<Pair<UUID, IApiLogcatMessage>>().apply {
		actionTrace.getActions().forEach { action ->
			action.deviceLogs.apiLogs.forEach{ api ->
				add(Pair(action.targetWidget?.uid ?: emptyUUID, api))
			}
		}
	}

val AbstractContext.resetActionsCount: Int
	get() = actionTrace.getActions().count { it.actionType == ResetAppExplorationAction::class.simpleName }

val AbstractContext.apkFileNameWithUnderscoresForDots: String
	get() = apk.fileName.replace(".", "_")