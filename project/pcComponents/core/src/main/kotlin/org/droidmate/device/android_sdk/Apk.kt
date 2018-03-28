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
package org.droidmate.device.android_sdk

import org.apache.commons.io.FilenameUtils
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// Suppresses warnings incorrectly caused by assertion checks in ctor.
class Apk constructor(internalPath: Path,
                      override val packageName: String,
                      override val launchableActivityName: String,
                      override val launchableActivityComponentName: String,
                      override val applicationLabel: String) : IApk {

	companion object {
		private const val serialVersionUID: Long = 1
		private val log = LoggerFactory.getLogger(Apk::class.java)

		private val dummyVal = "DUMMY"
		private val dummyApk = Apk(Paths.get("./dummy.apk"), dummyVal, dummyVal, dummyVal, dummyVal)

		@JvmStatic
		fun build(aapt: IAaptWrapper, path: Path): Apk {
			assert(Files.isRegularFile(path))

			val packageName: String
			val launchableActivityName: String
			val launchableActivityComponentName: String
			val applicationLabel: String
			try {
				val data = aapt.getMetadata(path)
				packageName = data[0]
				launchableActivityName = data[1]
				launchableActivityComponentName = data[2]
				applicationLabel = data[3]
			} catch (e: LaunchableActivityNameProblemException) {
				log.warn(Markers.appHealth, "! While getting metadata for $path, got an: $e Returning null apk.")
				assert(e.isFatal)
				return dummyApk
			} catch (e: NotEnoughDataToStartAppException) {
				log.warn(Markers.appHealth, "! While getting metadata for $path, got an: $e Returning null apk.")
				return dummyApk
			}

			if (arrayListOf(launchableActivityName, launchableActivityComponentName).any { it.isEmpty() }) {
				assert(arrayListOf(launchableActivityName, launchableActivityComponentName).all { it.isEmpty() })
				log.debug("$Apk.simpleName class instance for $path has null launchableActivityName and thus also " +
						"launchableActivityComponentName.")
			}

			return Apk(path, packageName, launchableActivityName, launchableActivityComponentName, applicationLabel)
		}
	}

	private val fileURI: URI

	override val fileName: String
	override val fileNameWithoutExtension: String
	override val absolutePath: String

	init {
		fileURI = internalPath.toUri()
		val fileName = path.fileName.toString()
		val absolutePath = path.toAbsolutePath().toString()

		assert(fileName.isNotEmpty(), fileName::toString)
		assert(fileName.endsWith(".apk"), fileName::toString)
		assert(absolutePath.isNotEmpty(), absolutePath::toString)
		assert(packageName.isNotEmpty(), packageName::toString)

		this.fileName = fileName
		this.fileNameWithoutExtension = FilenameUtils.getBaseName(path.fileName.toString())
		this.absolutePath = absolutePath

		assert(this.launchableActivityName.isNotEmpty() || this.applicationLabel.isNotEmpty())
	}

	override val path: Path
		get() = Paths.get(fileURI)

	override val inlined: Boolean
		get() = this.fileName.endsWith("-inlined.apk")

	override val isDummy: Boolean
		get() = this.packageName == Apk.dummyVal

	override fun toString(): String = this.fileName
}


