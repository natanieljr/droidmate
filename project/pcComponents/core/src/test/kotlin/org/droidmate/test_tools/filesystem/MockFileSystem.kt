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
package org.droidmate.test_tools.filesystem

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.droidmate.android_sdk.IApk
import org.droidmate.configuration.Configuration.Companion.defaultApksDir
import org.droidmate.test_tools.android_sdk.ApkTestHelper

import java.nio.file.FileSystem
import java.nio.file.Files

class MockFileSystem constructor(appNames: List<String>) {

	val fs: FileSystem
	val apks: List<IApk>

	init {
		appNames.forEach { assert(!it.endsWith(".apk")) }

		val apkNames = appNames.map { it + ".apk" }

		val res = this.build(apkNames)
		this.fs = res.first
		val filePaths = res.second

		this.apks = filePaths.map {
			ApkTestHelper.build(this.fs.getPath(it))
		}
	}

	private fun build(apkNames: List<String>): Pair<FileSystem, List<String>> {
		val fs = Jimfs.newFileSystem(Configuration.unix())

		val apksDir = fs.getPath(defaultApksDir)

		Files.createDirectories(apksDir)

		val apkFilePaths = apkNames.map {
			val apkFilePath = Files.createFile(apksDir.resolve(it))
			apkFilePath.toAbsolutePath().toString()
		}
		return Pair(fs, apkFilePaths)
	}
}
