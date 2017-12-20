// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
package org.droidmate.command

import org.droidmate.configuration.Configuration
import org.droidmate.misc.ThrowablesCollection

abstract class DroidmateCommand {

    @Throws(ThrowablesCollection::class)
    abstract fun execute(cfg: Configuration)

    companion object {
        @JvmStatic
        fun build(report: Boolean, inline: Boolean, unpack: Boolean, cfg: Configuration): DroidmateCommand {
            assert(arrayListOf(report, inline, unpack).count { it } <= 1)

            return when {
                report -> ReportCommand()
                inline -> InlineCommand.build()
                unpack -> UnpackCommand.build()
                else -> ExploreCommand.build(cfg)
            }
        }
    }
}
