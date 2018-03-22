package org.droidmate.uiautomator_daemon

import org.nustaq.serialization.FSTConfiguration
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException


class SerializationHelper {
    companion object {
        private val serializationConfig = FSTConfiguration.createDefaultConfiguration()

        @Throws(IOException::class)
        fun writeObjectToStream(outputStream: DataOutputStream, toWrite: Any) {
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

        @Throws(IOException::class, ClassNotFoundException::class)
        fun readObjectFromStream(inputStream: DataInputStream): Any {
            var len = inputStream.readInt()
            val buffer = ByteArray(len) // this could be reused !
            while (len > 0)
                len -= inputStream.read(buffer, buffer.size - len, len)
            return serializationConfig.getObjectInput(buffer).readObject()
        }
    }
}