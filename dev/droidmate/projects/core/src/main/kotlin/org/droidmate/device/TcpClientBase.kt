// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.device

import org.droidmate.android_sdk.DeviceException
import org.droidmate.uiautomator_daemon.SerializationHelper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.Serializable
import java.net.ConnectException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

class TcpClientBase<in InputToServerT : Serializable, out OutputFromServerT : Serializable> constructor(private val socketTimeout: Int)
    : ITcpClientBase<InputToServerT, OutputFromServerT> {
    /*companion object {
        private val log = LoggerFactory.getLogger(TcpClientBase::class.java)
    }*/

    private val serverAddress = "localhost"

    @Suppress("UNCHECKED_CAST")
    @Throws(TcpServerUnreachableException::class, DeviceException::class)
    override fun queryServer(input: InputToServerT, port: Int): OutputFromServerT {
        try {
            //log.trace("Socket socket = this.tryGetSocket($serverAddress, $port)")
            val socket = this.tryGetSocket(serverAddress, port)
//      log.trace("Got socket: $serverAddress:$port timeout: ${this.socketTimeout}")

            socket.soTimeout = this.socketTimeout

            // This will block until corresponding socket output stream (located on server) is flushed.
            //
            // Reference:
            // 1. the ObjectInputStream constructor comment.
            // 2. search for: "Note - The ObjectInputStream constructor blocks until" in:
            // http://docs.oracle.com/javase/7/docs/platform/serialization/spec/input.html
            //
//        log.trace("inputStream = new ObjectInputStream(socket<$serverAddress:$port>.inputStream)")
            val inputStream = DataInputStream(socket.inputStream)
//        log.trace("Got input stream")

            val outputStream = DataOutputStream(socket.outputStream)
//        log.trace("got outputStream")

            SerializationHelper.writeObjectToStream(outputStream, input)
            outputStream.flush()
            val output = SerializationHelper.readObjectFromStream(inputStream) as OutputFromServerT

//      log.trace("socket.close()")
            socket.close()

            return output

        } catch (e: EOFException) {
            throw TcpServerUnreachableException(e)
        } catch (e: SocketTimeoutException) {
            throw TcpServerUnreachableException(e)
        } catch (e: SocketException) {
            throw TcpServerUnreachableException(e)
        } catch (e: TcpServerUnreachableException) {
            throw e
        } catch (t: Throwable) {
            throw DeviceException("TcpClientBase has thrown a ${t.javaClass.simpleName} while querying server. " +
                    "Requesting to stop further apk explorations.", t, true)
        }
    }

    @Throws(TcpServerUnreachableException::class)
    private fun tryGetSocket(serverAddress: String, port: Int): Socket {
        try {
            val socket = Socket(serverAddress, port)
            assert(socket.isConnected)

            return socket

        } catch (e: ConnectException) {
            throw TcpServerUnreachableException(e)
        }
    }
}