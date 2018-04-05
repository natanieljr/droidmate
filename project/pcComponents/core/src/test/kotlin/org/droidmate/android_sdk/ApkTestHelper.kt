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
package org.droidmate.android_sdk

import org.apache.commons.io.FilenameUtils
import org.droidmate.device.android_sdk.Apk

import java.nio.file.Path
import java.nio.file.Paths

class ApkTestHelper {
	companion object {
		@JvmStatic
		fun build(name: String): Apk {
			assert(name.isNotEmpty())
			assert(!name.endsWith(".apk"))

			return Apk(
					Paths.get("/path/to/$name.apk"),
					"$name.pkg_name",
					"${name}_lActName",
					"${name}_lActCompName",
					"${name}_applicationLabel")
		}

		@JvmStatic
		fun build(packageName: String, launchableActivityName: String, launchableActivityComponentName: String, applicationLabel: String): Apk {
			val path = Paths.get("/path/to/$packageName.apk")
			return Apk(
					path,
					packageName,
					launchableActivityName,
					launchableActivityComponentName,
					applicationLabel)
		}

		@JvmStatic
		fun build(path: Path): Apk {
			assert(path.toString().isNotEmpty())
			val name = FilenameUtils.getBaseName(path.fileName.toString())

			return Apk(
					path,
					"$name.pkg_name",
					"${name}_lActName",
					"${name}_lActCompName",
					"${name}_applicationLabel")
		}
	}
}
