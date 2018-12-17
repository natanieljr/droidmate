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

package org.droidmate.runtime;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

// !!! DUPLICATION WARNING !!! with org.droidmate.uiautomator_daemon.UiautomatorDaemonTcpServerBase
abstract class TcpServerBase<ServerInputT extends Serializable, ServerOutputT extends Serializable> {

	int port;
	private ServerSocket serverSocket = null;
	private SocketException serverSocketException = null;

	protected TcpServerBase() {
		super();
	}

	protected abstract ServerOutputT OnServerRequest(ServerInputT input);

	protected abstract boolean shouldCloseServerSocket(ServerInputT serverInput);

	protected abstract Logger getLogger();

	public Thread tryStart(int port) throws Exception {
		getLogger().info(String.format("tryStart(port:%d): entering", port));
		this.serverSocket = null;
		this.serverSocketException = null;
		this.port = port;

		MonitorServerRunnable monitorServerRunnable = new MonitorServerRunnable();
		Thread serverThread = new Thread(monitorServerRunnable);
		serverThread.setDaemon(true); // ensure termination if the main thread dies
		// For explanation why this synchronization is necessary, see MonitorServerRunnable.run() method synchronized {} block.
		synchronized (monitorServerRunnable) {
			if (!(serverSocket == null && serverSocketException == null))
				throw new AssertionError();
			serverThread.start();
			monitorServerRunnable.wait();
			// Either a serverSocket has been established, or an exception was thrown, but not both.
			// noinspection SimplifiableBooleanExpression
			if (!(serverSocket != null ^ serverSocketException != null))
				throw new AssertionError();
		}
		if (serverSocketException != null) {

			String cause = (serverSocketException.getCause() != null) ? serverSocketException.getCause().getMessage()
					: serverSocketException.getMessage();
			if ("bind failed: EADDRINUSE (Address already in use)".equals(cause)) {
				getLogger().info("tryStart(port:" + port + "): FAILURE Failed to start TCP server because "
						+ "'bind failed: EADDRINUSE (Address already in use)'. " + "Returning null Thread.");
				return null;
			} else {
				throw new Exception(String.format("Failed to start monitor TCP server thread for port %s. "
						+ "Cause of this exception is the one returned by the failed thread.", port), serverSocketException);
			}
		}

		getLogger().info("tryStart(port:" + port + "): SUCCESS");
		return serverThread;
	}

	public void closeServerSocket() {
		try {
			serverSocket.close();
			getLogger().info(String.format("serverSocket.close(): SUCCESS port %s", port));
		} catch (IOException e) {
			getLogger().warning(String.format("serverSocket.close(): FAILURE port %s", port));
		}
	}

	public boolean isClosed() {
		return serverSocket.isClosed();
	}

	private class MonitorServerRunnable implements Runnable {

		public void run() {
			getLogger().info(String.format("run(): entering port:%d", port));

			try {

				// Synchronize to ensure the parent thread (the one which started this one) will continue only after one of these two
				// is true:
				// - serverSocket was successfully initialized
				// - exception was thrown and assigned to a field and this thread exitted
				synchronized (this) {
					try {
						getLogger().info(String.format("serverSocket = new ServerSocket(%d)", port));
						serverSocket = new ServerSocket(port);
						getLogger().info(String.format("serverSocket = new ServerSocket(%d): SUCCESS", port));
					} catch (SocketException e) {
						serverSocketException = e;
					}

					if (serverSocketException != null) {
						getLogger().info("serverSocket = new ServerSocket(" + port + "): FAILURE " + "aborting further thread execution.");
						this.notify();
						return;
					} else {
						this.notify();
					}
				}

				if (serverSocket == null)
					throw new AssertionError();
				if (serverSocketException != null)
					throw new AssertionError();

				while (!serverSocket.isClosed()) {
					getLogger().info(String.format("clientSocket = serverSocket.accept() / port:%d", port));
					Socket clientSocket = serverSocket.accept();
					getLogger().info(String.format("clientSocket = serverSocket.accept(): SUCCESS / port:%d", port));

					//// ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
					DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

					/*
					 * Flushing done to prevent client blocking on creation of input stream reading output from this stream. See:
					 * org.droidmate.device.SerializableTCPClient.queryServer
					 *
					 * References: 1. http://stackoverflow.com/questions/8088557/getinputstream-blocks 2. Search for:
					 * "Note - The ObjectInputStream constructor blocks until" in:
					 * http://docs.oracle.com/javase/7/docs/platform/serialization/spec/input.html
					 */
					//// output.flush();

					//// ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
					DataInputStream input = new DataInputStream(clientSocket.getInputStream());
					ServerInputT serverInput;

					try {
						@SuppressWarnings("unchecked") // Without this var here, there is no place to put the "unchecked" suppression
								// warning.
								ServerInputT localVarForSuppressionAnnotation = (ServerInputT) SerializationHelper.readObjectFromStream(input);
						serverInput = localVarForSuppressionAnnotation;

					} catch (Exception e) {
						getLogger().warning("! serverInput = input.readObject(): FAILURE "
								+ "while reading from clientSocket on port " + port + ". Closing server socket.");
						closeServerSocket();
						break;
					}

					ServerOutputT serverOutput;
					getLogger().info(String.format("OnServerRequest(%s) / port:%d", serverInput, port));
					serverOutput = OnServerRequest(serverInput);
					SerializationHelper.writeObjectToStream(output, serverOutput);
					clientSocket.close();

					if (shouldCloseServerSocket(serverInput)) {
						getLogger().info(String.format("shouldCloseServerSocket(): true / port:%d", port));
						closeServerSocket();
					}
				}

				if (!serverSocket.isClosed())
					throw new AssertionError();

				getLogger().info(String.format("serverSocket.isClosed() / port:%d", port));
			} catch (SocketTimeoutException e) {
				getLogger().warning("! Closing monitor TCP server due to a timeout.");
				closeServerSocket();
			} catch (IOException e) {
				getLogger().warning("! Exception was thrown while operating monitor TCP server.");
			}
		}

	}
}
