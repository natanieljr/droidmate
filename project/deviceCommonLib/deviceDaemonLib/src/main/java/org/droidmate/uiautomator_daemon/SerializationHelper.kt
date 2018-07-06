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
package org.droidmate.uiautomator_daemon

import java.io.*


object SerializationHelper {
	private val serializationConfig by lazy { org.nustaq.serialization.FSTConfiguration.createDefaultConfiguration() }

	@JvmStatic
	@JvmOverloads
	@Throws(IOException::class)
	fun writeObjectToStream(outputStream: DataOutputStream, toWrite: Any, useLegacySerialization: Boolean = false) {

		if (useLegacySerialization) {
			val objectOutput = ObjectOutputStream(outputStream)
			objectOutput.writeObject(toWrite)
			objectOutput.flush()
		} else {
			// write object
			val objectOutput = serializationConfig.objectOutput // could also do new with minor perf impact
			// write object to internal buffer
			objectOutput.writeObject(toWrite)
			// write length
			outputStream.writeInt(objectOutput.written)
			// write bytes
			outputStream.write(objectOutput.buffer, 0, objectOutput.written)

			objectOutput.flush() // return for reuse to conf
		}
	}

	@JvmStatic
	@JvmOverloads
	@Throws(IOException::class, ClassNotFoundException::class)
	fun readObjectFromStream(inputStream: DataInputStream, useLegacySerialization: Boolean = false): Any {

		return if (useLegacySerialization) {
			ObjectInputStream(inputStream).readObject()
		} else {
			var len = inputStream.readInt()
			val buffer = ByteArray(len) // this could be reused !
			while (len > 0)
				len -= inputStream.read(buffer, buffer.size - len, len)

			serializationConfig.getObjectInput(buffer).readObject()
		}
	}
}