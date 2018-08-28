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

import com.konradjamrozik.FirstMatchFirstGroup
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.misc.DroidmateException
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.SysCmdExecutorException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Wrapper for the {@code aapt} tool from Android SDK.
 */
class AaptWrapper constructor(private val cfg: ConfigurationWrapper,
                              private val sysCmdExecutor: ISysCmdExecutor) : IAaptWrapper {

	companion object {
		private val log by lazy { LoggerFactory.getLogger(AaptWrapper::class.java) }

		@JvmStatic
		@Throws(DroidmateException::class)
		private fun tryGetPackageNameFromBadgingDump(aaptBadgingDump: String): String {
			assert(aaptBadgingDump.isNotEmpty())

			val matcher = "(?:.*)package: name='(\\S*)'.*".toRegex().findAll(aaptBadgingDump).toList()

			return when {
				matcher.isEmpty() -> throw DroidmateException("No package name found in 'aapt dump badging'")
				matcher.size > 1 -> throw DroidmateException("More than one package name found in 'aapt dump badging'")
				else -> getAndValidateFirstMatch(matcher)
			}
		}

		@JvmStatic
		private fun getAndValidateFirstMatch(matches: List<MatchResult>): String {
			//Ex: package: name='org.droidmate.fixtures.apks.gui' versionCode='1' versionName='1.0' platformBuildVersionName='6.0-2704002'
			// Matcher 0 = all string
			// Matcher 1 = activity name
			assert(matches.isNotEmpty())
			val firstMatch = matches.first()
			val value = firstMatch.groupValues.last()
			assert(value.isNotEmpty())
			return value
		}

		@JvmStatic
		@Throws(DroidmateException::class)
		private fun tryGetApplicationLabelFromBadgingDump(aaptBadgingDump: String): String {
			assert(aaptBadgingDump.isNotEmpty())

			try {
				val labelMatch = FirstMatchFirstGroup(
						aaptBadgingDump,
						" application-label-en(?:.*):'(.*)'",
						"application-label-de(?:.*):'(.*)'",
						"application-label(?:.*):'(.*)'",
						".*launchable-activity: name = '(?:.*)'  label = '(.*)' .*"
				)
				return labelMatch.value
			} catch (e: Exception) {
				throw DroidmateException("No non-empty application label found in 'aapt dump badging'", e)
			}
		}
	}

	override fun getPackageName(apk: Path): String {
		assert(Files.isRegularFile(apk))

		val aaptBadgingDump = aaptDumpBadging(apk)
		val packageName = tryGetPackageNameFromBadgingDump(aaptBadgingDump)

		assert(packageName.isNotEmpty())
		return packageName
	}

	override fun getApplicationLabel(apk: Path): String {
		assert(Files.isRegularFile(apk))

		val aaptBadgingDump = aaptDumpBadging(apk)
		return AaptWrapper.tryGetApplicationLabelFromBadgingDump(aaptBadgingDump)
	}

	override fun getMetadata(apk: Path): List<String> {
		val applicationLabel = try {
			getApplicationLabel(apk)
		} catch (e: DroidmateException) {
			""
		}

		return arrayListOf(getPackageName(apk), applicationLabel)
	}

	var aaptDumpBadgingInstr: (Path) -> String = { instrumentedApk ->
		val commandDescription = "Executing aapt (Android Asset Packaging Tool) to extract package name of apk ${instrumentedApk.toAbsolutePath()}."

		try {
			val outputStreams = sysCmdExecutor.execute(
					commandDescription, cfg.aaptCommand, "dump badging", instrumentedApk.toAbsolutePath().toString())

			val aaptBadgingDump = outputStreams[0]

			assert(aaptBadgingDump.isNotEmpty())
			aaptBadgingDump
		} catch (e: SysCmdExecutorException) {
			throw DroidmateException(e)
		}
	}

	private fun aaptDumpBadging(instrumentedApk: Path): String {
		return aaptDumpBadgingInstr(instrumentedApk)
	}
}
