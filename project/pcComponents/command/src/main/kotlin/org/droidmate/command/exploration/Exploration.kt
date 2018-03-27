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

package org.droidmate.command.exploration

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IApk
import org.droidmate.configuration.Configuration
import org.droidmate.debug.debugT
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.device.datatypes.statemodel.ActionResult
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.exploration.actions.RunnableExplorationAction
import org.droidmate.exploration.actions.RunnableTerminateExplorationAction
import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.exploration.data_aggregators.ExplorationContext
import org.droidmate.exploration.device.IRobustDevice
import org.droidmate.exploration.strategy.EmptyActionResult
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.exploration.strategy.IExplorationStrategy
import org.droidmate.logging.Markers
import org.droidmate.misc.Failable
import org.droidmate.misc.ITimeProvider
import org.droidmate.misc.TimeProvider
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

class Exploration constructor(private val cfg: Configuration,
                              private val timeProvider: ITimeProvider,
                              private val strategyProvider: (AbstractContext) -> IExplorationStrategy) : IExploration {
	companion object {
		private val log = LoggerFactory.getLogger(Exploration::class.java)


		@JvmStatic
		fun build(cfg: Configuration,
		          timeProvider: ITimeProvider = TimeProvider(),
		          strategyProvider: (AbstractContext) -> IExplorationStrategy = { ExplorationStrategyPool.build(it, cfg) }): Exploration = Exploration(cfg, timeProvider, strategyProvider)
	}

	override fun run(app: IApk, device: IRobustDevice): Failable<AbstractContext, DeviceException> {
		log.info("run(${app.packageName}, device)")

		device.resetTimeSync()

		try {
			tryDeviceHasPackageInstalled(device, app.packageName)
			tryWarnDeviceDisplaysHomeScreen(device, app.fileName)
		} catch (e: DeviceException) {
			return Failable<AbstractContext, DeviceException>(null, e)
		}

		val output = explorationLoop(app, device)

		output.verify()

		if (output.exceptionIsPresent)
			log.warn(Markers.appHealth, "! Encountered ${output.exception.javaClass.simpleName} during the exploration of ${app.packageName} " +
					"after already obtaining some exploration output.")

		return Failable<AbstractContext, DeviceException>(output, if (output.exceptionIsPresent) output.exception else null)
	}

	private var actionT: Long = 0
	private var nActions = 0
	private fun explorationLoop(app: IApk, device: IRobustDevice): AbstractContext {
		log.debug("explorationLoop(app=${app.fileName}, device)")

		// Construct the object that will hold the exploration output and that will be returned from this method.
		val output = ExplorationContext(app)

		output.explorationStartTime = timeProvider.getNow()
		log.debug("Exploration start time: " + output.explorationStartTime)

		// Construct initial action and run it on the device to obtain initial result.
		var action: IRunnableExplorationAction? = null
		var result: ActionResult = EmptyActionResult

		var isFirst = true
		val strategy: IExplorationStrategy = strategyProvider.invoke(output)

		// Execute the exploration loop proper, starting with the values of initial reset action and its result.
		while (isFirst || (result.successful && !(action is RunnableTerminateExplorationAction))) {
			// decide for an action
			action = debugT("strategy decition time", { RunnableExplorationAction.from(strategy.decide(result), timeProvider.getNow(), cfg.takeScreenshots) })
			// execute action
			measureTimeMillis { result = action.run(app, device) }.let {
				actionT += it
				nActions += 1
				println("$it millis elapsed until result was available on average ${actionT / nActions}")
			}
			output.add(action, result)
			// update strategy
			strategy.update(result)

			if (isFirst) {
				log.info("Initial action: ${action.base}")
				isFirst = false
			}
		}

		assert(!result.successful || action is RunnableTerminateExplorationAction)

		// Propagate exception if there was any
		if (!result.successful)
			output.exception = result.exception

		output.explorationEndTime = timeProvider.getNow()

		return output
	}

	@Throws(DeviceException::class)
	private fun tryDeviceHasPackageInstalled(device: IExplorableAndroidDevice, packageName: String) {
		log.trace("tryDeviceHasPackageInstalled(device, $packageName)")

		if (!device.hasPackageInstalled(packageName))
			throw DeviceException()
	}

	@Throws(DeviceException::class)
	private fun tryWarnDeviceDisplaysHomeScreen(device: IExplorableAndroidDevice, fileName: String) {
		log.trace("tryWarnDeviceDisplaysHomeScreen(device, $fileName)")

		val initialGuiSnapshot = device.getGuiSnapshot()

		if (!initialGuiSnapshot.isHomeScreen)
			log.warn(Markers.appHealth,
					"An exploration process for $fileName is about to start but the device doesn't display home screen. " +
							"Instead, its GUI state is: $initialGuiSnapshot.guiStatus. " +
							"Continuing the exploration nevertheless, hoping that the first \"reset app\" " +
							"exploration action will force the device into the home screen.")
	}
}