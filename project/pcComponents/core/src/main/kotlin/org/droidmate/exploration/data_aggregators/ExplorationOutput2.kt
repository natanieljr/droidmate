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
package org.droidmate.exploration.data_aggregators

import org.droidmate.exploration.ExplorationContext
import org.droidmate.storage.IStorage2
import org.slf4j.LoggerFactory

@Deprecated("this should go away! simply store a list for all exploration contexts")
class ExplorationOutput2(private val list: MutableList<ExplorationContext> = ArrayList()) : MutableList<ExplorationContext> by list {
	companion object {
		private val log by lazy { LoggerFactory.getLogger(ExplorationOutput2::class.java) }
		private const val serialVersionUID: Long = 1

		@JvmStatic
		fun from(storage: IStorage2): List<ExplorationContext> {
			return storage.getSerializedRuns2().map {
				val apkout2 = storage.deserialize(it)
				log.info("Deserialized exploration output of $apkout2.packageName from $it")
				apkout2.verify()
				apkout2
			}
		}
	}
}