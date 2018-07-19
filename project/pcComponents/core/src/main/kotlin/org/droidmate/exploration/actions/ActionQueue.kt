package org.droidmate.exploration.actions

import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.IRobustDevice

class ActionQueue(val actions: List<AbstractExplorationAction>)
	: AbstractExplorationAction() {

	override fun toShortString(): String {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun performDeviceActions(app: IApk, device: IRobustDevice) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}
}