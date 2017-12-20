// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
package org.droidmate.exploration.data_aggregators

import org.droidmate.storage.IStorage2
import org.slf4j.LoggerFactory

class ExplorationOutput2(private val list: MutableList<IApkExplorationOutput2> = ArrayList()) : MutableList<IApkExplorationOutput2> by list {
    companion object {
        private val log = LoggerFactory.getLogger(ExplorationOutput2::class.java)
        private const val serialVersionUID: Long = 1

        @JvmStatic
        fun from(storage: IStorage2): List<IApkExplorationOutput2> {
            return storage.getSerializedRuns2().map {
                val apkout2 = storage.deserialize(it) as IApkExplorationOutput2
                log.info("Deserialized exploration output of $apkout2.packageName from $it")
                apkout2.verify()
                apkout2
            }
        }
    }
}