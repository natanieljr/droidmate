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

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Manuel Benz
 * @author Timo Gühring
 */
public class Runtime {

	// Determines whether every statement should be logged only distinct statements
	private static final boolean logEveryStatement = false;

	private static boolean initialized = false;
	private static final Logger log = Logger.getLogger(Runtime.class.getName());

	private static void initialize(String portFilePath) {
		String msg_ctor_start = "ctor(): entering";
		log.info(msg_ctor_start);
		try {
			org.droidmate.runtime.MonitorTcpServer server = startMonitorTCPServer(portFilePath);
			log.log(Level.INFO, "ctor(): startMonitorTCPServer(): SUCCESS port: " + server.port);
		} catch (Throwable e) {
			log.log(Level.WARNING, "! ctor(): startMonitorTCPServer(): FAILURE", e);
		}

		initialized = true;
		count = 0;
	}

	@SuppressWarnings("unused")
	public static void statementPoint(String method, String portFilePath) {
		// Initialize monitor served on the first usage
		synchronized (currentStatements) {
			if (!initialized)
				initialize(portFilePath);

//			log.info("Monitor_API_method_call: " + method);
			addCurrentStatements(method);
		}
	}

	// !!! DUPLICATION WARNING !!! EXTRACTED FROM DROIDMATE Monitor.java

	private final static LinkedList<ArrayList<String>> currentStatements = new LinkedList<>();
	private final static Set<String> allStatements = new HashSet<>();
	private static long count;

	private static void addCurrentStatements(String payload) {
		//synchronized (currentStatements) {
		if (logEveryStatement || !allStatements.contains(payload)) {
			log.info("addCurrentStatements(" + count++ + "/" + payload + ")");
			String now = org.droidmate.runtime.MonitorTcpServer.getNowDate();
			currentStatements.add(new ArrayList<>(Arrays.asList(now, payload)));
			allStatements.add(payload);
		}
		//}
	}
	// endregion

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static int getPort(String portFilePath) throws Exception {
		File file = new File(portFilePath);
		FileInputStream fis = new FileInputStream(file);
		byte[] data = new byte[(int) file.length()];
		fis.read(data);
		fis.close();

		return Integer.parseInt(new String(data, StandardCharsets.UTF_8));
	}

	@SuppressWarnings("ConstantConditions")
	private static org.droidmate.runtime.MonitorTcpServer startMonitorTCPServer(String portFilePath) throws Throwable {
		log.info("startMonitorTCPServer(): entering");
		org.droidmate.runtime.MonitorTcpServer tcpServer
				= new org.droidmate.runtime.MonitorTcpServer(currentStatements);

		Thread serverThread;
		Integer portUsed = null;

		final int port = getPort(portFilePath);
		serverThread = tcpServer.tryStart(port);
		if (serverThread != null)
			portUsed = port;

		if (serverThread == null)
			throw new Exception("startMonitorTCPServer(): no available ports.");
		else if (portUsed == null)
			throw new AssertionError();
		else if (tcpServer.isClosed()) throw new AssertionError();

		log.info("startMonitorTCPServer(): SUCCESS portUsed: " + portUsed);
		return tcpServer;
	}
}
