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

package org.droidmate.apk_inliner

import org.droidmate.misc.DroidmateException
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.SysCmdExecutorException
import java.nio.file.Files

import java.nio.file.Path

class JarsignerWrapper(private val sysCmdExecutor: ISysCmdExecutor,
                       private val jarsignerPath: Path,
                       private val debugKeystore: Path) : IJarsignerWrapper {
	init {
		assert(Files.isRegularFile(this.jarsignerPath))
		assert(Files.isRegularFile(this.debugKeystore))
	}

	@SuppressWarnings("SpellCheckingInspection")
	@Throws(DroidmateException::class)
	override fun signWithDebugKey(apk: Path): Path {
		val commandDescription = "Executing jarsigner to sign apk ${apk.toRealPath()}"

		try {

			// this command is based on:
			// http://developer.android.com/tools/publishing/app-signing.html#debugmode
			// http://developer.android.com/tools/publishing/app-signing.html#signapp
			sysCmdExecutor.execute(commandDescription, jarsignerPath.toRealPath().toString(),
					"-sigalg MD5withRSA -digestalg SHA1",
					"-keystore", debugKeystore.toRealPath().toString(),
					"-storepass android -keypass android ",
					apk.toRealPath().toString(),
					"androiddebugkey")

		} catch (e: SysCmdExecutorException) {
			throw DroidmateException(e)
		}

		assert(Files.isRegularFile(apk))
		return apk
	}
}