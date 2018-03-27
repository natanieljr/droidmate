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
package org.droidmate.storage

import org.droidmate.exploration.actions.DeviceExceptionMissing
import org.nustaq.serialization.FSTBasicObjectSerializer
import org.nustaq.serialization.FSTClazzInfo
import org.nustaq.serialization.FSTObjectInput
import org.nustaq.serialization.FSTObjectOutput
import java.io.IOException

class FSTDeviceExceptionMissingSerializer : FSTBasicObjectSerializer() {

	@Throws(IOException::class)
	override fun writeObject(out: FSTObjectOutput, toWrite: Any, clzInfo: FSTClazzInfo, referencedBy: FSTClazzInfo.FSTFieldInfo, streamPosition: Int) {
		out.writeStringUTF((toWrite as DeviceExceptionMissing).message ?: "")
	}

	@Throws(Exception::class)
	override fun instantiate(objectClass: Class<*>?, input: FSTObjectInput?, serializationInfo: FSTClazzInfo?, referencee: FSTClazzInfo.FSTFieldInfo?, streamPosition: Int): Any {
		val s = DeviceExceptionMissing(input!!.readStringUTF() ?: "")
		input.registerObject(s, streamPosition, serializationInfo, referencee)
		return s
	}

	override fun writeTupleEnd(): Boolean {
		return false
	}

	companion object {

		var Instance = FSTDeviceExceptionMissingSerializer() // used directly
	}
}