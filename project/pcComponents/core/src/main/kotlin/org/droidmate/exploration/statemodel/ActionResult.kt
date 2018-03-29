// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
package org.droidmate.exploration.statemodel

import com.google.common.base.MoreObjects
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.debug.debugT
import org.droidmate.exploration.actions.DeviceExceptionMissing
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.device.deviceInterface.IDeviceLogs
import org.droidmate.device.deviceInterface.MissingDeviceLogs
import org.droidmate.exploration.statemodel.config.ModelConfig
import org.droidmate.uiautomator_daemon.GuiStatusResponse
import java.io.ByteArrayInputStream
import java.io.Serializable
import java.net.URI
import java.time.LocalDateTime
import javax.imageio.ImageIO

/**
 * Interface for a context record which stores the performed action, alongside the GUI state before the action
 *
 * this should be only used for state model instantiation and not for exploration strategies
 *
 * @param action Action which was sent (by the ExplorationStrategy) to DroidMate
 * @param startTimestamp Time the action selection started (used to sync logcat)
 * @param endTimestamp Time the action selection started (used to sync logcat)
 * @param deviceLogs APIs triggered by this action
 * @param guiSnapshot Device snapshot after executing the action
 * @param exception Exception during execution which crashed the action (if any), or MissingDeviceException (otherwise)
 * @param screenshot Path to the screenshot (taken after the action was executed)
 *
 * @author Nataniel P. Borges Jr.
 */
open class ActionResult(val action: ExplorationAction,
                        val startTimestamp: LocalDateTime,
                        val endTimestamp: LocalDateTime,
                        val deviceLogs: IDeviceLogs = MissingDeviceLogs,
                        val guiSnapshot: GuiStatusResponse = GuiStatusResponse.empty,
                        val screenshot: ByteArray = ByteArray(0),
                        val exception: DeviceException = DeviceExceptionMissing()) : Serializable {
	companion object {
		private const val serialVersionUID: Long = 1
	}

	/**
	 * Identifies if the action was successful or crashed
	 */
	val successful: Boolean
		get() = exception is DeviceExceptionMissing


	override fun equals(other: Any?): Boolean {
		if (other !is ActionResult)
			return false

		return this.action.toString() == other.action.toString()
		// TODO Add check on exploration state as well
	}

	override fun hashCode(): Int {
		return this.action.hashCode()
	}

	override fun toString(): String {
		return MoreObjects.toStringHelper(this)
				.add("action", action)
				.add("successful", successful)
				.add("snapshot", guiSnapshot)
				.addValue(deviceLogs)
				.add("exception", exception)
				.toString()
	}

	/** this method should be exclusively used for StateData generation */
	fun getWidgets(config: ModelConfig): List<Widget> {
		val deviceObjects = setOf("//android.widget.FrameLayout[1]", "//android.widget.FrameLayout[1]/android.widget.FrameLayout[1]")

//		val img:BufferedImage? =
		debugT("img file read", { ImageIO.read(ByteArrayInputStream(this.screenshot)) },inMillis = true)
		.let { img ->
					guiSnapshot.let { g ->
						debugT(" \n filter device objects",
								{ g.widgets.filterNot { deviceObjects.contains(it.xpath) } } // ignore the overall layout containing the Android Status-bar
						).let {
							//					debugT(" widgets sequential", { it.map{ Widget.fromWidgetData(it,img,config)}} ,{ timeS += it })

							return debugT("create all widgets unconfined", {
								// some times much faster then sequential but some timesl a few fousand ns slower but in average seams faster
								it.map { async(Unconfined) { Widget.fromWidgetData(it, img, config) } } // iterates over each WidgetData and creates Widget object collect all these elements as set
										.map { runBlocking { it.await() } }
							}, { timeP += it })

									.also {
										println("===> sumS=${timeS / 1000000.0} \t sumP=${timeP / 1000000.0}")
									}

							// funny sequential seams faster than parallel approach
//					debugT("create all widgets default dispatch",{
//					it.map { async{Widget.fromWidgetData(it, img, config)} } // iterates over each WidgetData and creates Widget object collect all these elements as set
//						.map { it.await() }
//					})
						}
					}
				}
	}

	fun resultState(widgets: List<Widget>): StateData = resultState(lazyOf(widgets))
	fun resultState(widgets: Lazy<List<Widget>>): StateData {
		return guiSnapshot.let { g ->
			StateData(widgets, g.topNodePackageName, g.androidLauncherPackageName, g.isHomeScreen, g.isAppHasStoppedDialogBox,
					g.isRequestRuntimePermissionDialogBox)
		}
	}
}

private var timeS: Long = 0
private var timeP: Long = 0
private fun getPathOrNull(uri:URI):java.nio.file.Path? = if(uri.toASCIIString() != "test://empty") java.nio.file.Paths.get(uri) else null
