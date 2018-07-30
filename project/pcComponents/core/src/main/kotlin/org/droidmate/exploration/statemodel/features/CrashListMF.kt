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

package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.joinChildren
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData
import java.nio.file.Files
import kotlin.coroutines.experimental.CoroutineContext

class CrashListMF : WidgetCountingMF() {
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("CrashListMF"), parent = job)

	private val crashes: MutableMap<ActionData, String> = mutableMapOf()

	override suspend fun onNewAction(deferredAction: Deferred<ActionData>, prevState: StateData, newState: StateData) {
		val actionData = deferredAction.await()

		if (newState.isAppHasStoppedDialogBox) {
			crashes[actionData] = actionData.exception
			if (actionData.targetWidget != null)
				incCnt(actionData.targetWidget.uid, prevState.uid)
		}
	}

	override suspend fun dump(context: ExplorationContext) {
		job.joinChildren() // wait that all updates are applied before changing the counter value
		val out = StringBuffer()
		out.appendln(header)
		crashes.toSortedMap(compareBy { it.prevState.first })
				.forEach { crash ->
					val actionData = crash.key
					val exception = crash.value

					out.appendln("${actionData.actionType};${actionData.targetWidget};\t${actionData.prevState};$exception")
			}

		val file = context.getModel().config.baseDir.resolve("crashlist.txt")
		Files.write(file, out.lines())
	}

	companion object {
		@JvmStatic
		val header = "ActionType;WidgetId".padEnd(38)+"; State-Context".padEnd(38)+";Exception"
	}
}