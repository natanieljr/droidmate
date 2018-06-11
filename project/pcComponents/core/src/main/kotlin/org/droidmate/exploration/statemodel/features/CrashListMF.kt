package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.Widget
import java.io.File
import kotlin.coroutines.experimental.CoroutineContext

class CrashListMF: WidgetCountingMF() {
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("CrashListMF"), parent = job)

	override suspend fun onNewInteracted(targetWidget: Widget?, prevState: StateData, newState: StateData) {
		if(newState.isAppHasStoppedDialogBox) incCnt(targetWidget!!.uid,prevState.uid)
	}

	override suspend fun dump(context: ExplorationContext) {
		dump(File(context.getModel().config.baseDir.toString() + "${File.separator}crashlist.txt"))
	}
}