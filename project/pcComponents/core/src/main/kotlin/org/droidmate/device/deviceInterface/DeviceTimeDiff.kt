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
package org.droidmate.device.deviceInterface

import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.device.logcat.TimeFormattedLogcatMessage
import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.misc.MonitorConstants
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * <p>
 * The device has different internal clock than the host machine. This class represents the time diff between the clocks.
 *
 * </p><p>
 * Use {@link DeviceTimeDiff#sync} on a device clock time to make it in sync with the host machine clock time.
 *
 * </p><p>
 * For example, if the device clock is 3 seconds into the future as compared to the host machine clock,
 * 3 seconds will be subtracted from the sync() input, {@code deviceTime}.
 *
 * </p>
 */
class DeviceTimeDiff(private val device: IExplorableAndroidDevice) : IDeviceTimeDiff {
	companion object {
		private val log by lazy { LoggerFactory.getLogger(DeviceTimeDiff::class.java) }
	}

	private var diff: Duration? = null

	override fun sync(deviceTime: LocalDateTime): LocalDateTime {
		if (diff == null)
			diff = computeDiff(device)

		assert(diff != null)

		return deviceTime.minus(diff)
	}

	@Throws(DeviceException::class)
	private fun computeDiff(device: IExplorableAndroidDevice): Duration {
		val deviceTime = device.getCurrentTime()
		val now = LocalDateTime.now()
		val diff = Duration.between(now, deviceTime)

		val formatter = DateTimeFormatter.ofPattern(
				MonitorConstants.monitor_time_formatter_pattern, MonitorConstants.monitor_time_formatter_locale)
		val msg = "computeDiff(device) result: " +
				"Current time: ${now.format(formatter)} " +
				"Device time: ${deviceTime.format(formatter)} " +
				"Resulting diff: $diff"

		log.trace(msg)

		assert(diff != null)
		return diff
	}

	override fun syncMessages(messages: List<TimeFormattedLogMessageI>): List<TimeFormattedLogMessageI> {
		return messages.map {

			//      logcat.trace("syncing: curr diff: ${this.diff} logcat dev. time: $it.time tag: $it.tag pid: $it.pidString, payload first 200 chars: ${it.messagePayload.take(200)}")

			TimeFormattedLogcatMessage.from(
					this.sync(it.time),
					it.level, it.tag, it.pidString, it.messagePayload)
		}
	}

	override fun reset() {
		diff = null
	}
}
