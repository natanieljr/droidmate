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

package org.droidmate.command

import org.droidmate.configuration.ConfigProperties.ExecutionMode.coverage
import org.droidmate.configuration.ConfigProperties.ExecutionMode.inline
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.exploration.ExplorationContext
import org.droidmate.misc.ThrowablesCollection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

abstract class DroidmateCommand {

	@Throws(ThrowablesCollection::class)
	abstract suspend fun execute(cfg: ConfigurationWrapper): List<ExplorationContext>

	companion object {
		@JvmStatic
		protected val log: Logger by lazy { LoggerFactory.getLogger(DroidmateCommand::class.java) }

		@JvmStatic
		fun build(cfg: ConfigurationWrapper): DroidmateCommand {
			assert(arrayListOf(cfg[inline], cfg[coverage]).count { it } <= 1)

			return when {
				cfg[inline] -> InlineCommand(cfg)
				cfg[coverage] -> CoverageCommand(cfg)
				else -> ExploreCommand.build(cfg)
			}
		}

		@JvmStatic
		protected fun moveOriginal(apk: Apk, originalsDir: Path) {
			val original = originalsDir.resolve(apk.fileName)

			if (!Files.exists(original)) {
				Files.move(apk.path, original)
				log.info("Moved ${original.fileName} to '${originalsDir.fileName}' sub dir.")
			} else {
				log.info("Skipped moving ${original.fileName} to '${originalsDir.fileName}' sub dir: it already exists there.")
			}
		}
	}
}
