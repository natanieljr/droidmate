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

package org.droidmate.api

import org.droidmate.command.InlineCommand
import org.droidmate.configuration.ConfigurationWrapper
import org.slf4j.LoggerFactory

@Suppress("unused")
object Instrumentation {
	private val log by lazy { LoggerFactory.getLogger(Instrumentation::class.java) }

	/****************************** Apk-Inline API methods *****************************/
	@JvmStatic
	@JvmOverloads
	suspend fun inline(args: Array<String> = emptyArray()) {
        Instrumentation.inline(setup(args))
	}

	@JvmStatic
	suspend fun inline(cfg: ConfigurationWrapper) {
		log.info("inline the apks if necessary")
		InlineCommand(cfg).execute(cfg)
	}
}