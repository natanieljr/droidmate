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

//region TCP server code

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

class MonitorTcpServer extends org.droidmate.runtime.TcpServerBase<String, LinkedList<ArrayList<String>>> {

	private static final Date startDate = new Date();
	private static final long startNanoTime = System.nanoTime();
	private static final Logger log = Logger.getLogger(MonitorTcpServer.class.getName());

	private static String srvCmd_close = "close";
	private static String monitor_time_formatter_pattern = "yyyy-MM-dd HH:mm:ss.SSS";
	private static Locale monitor_time_formatter_locale = Locale.US;
	private static final SimpleDateFormat monitor_time_formatter
			= new SimpleDateFormat(monitor_time_formatter_pattern, monitor_time_formatter_locale);

	private final List<ArrayList<String>> currentStatements;

	MonitorTcpServer(LinkedList<ArrayList<String>> currentStatements) {
		super();

		this.currentStatements = currentStatements;
	}

	static String getNowDate() {
		final Date nowDate = new Date(startDate.getTime() + (System.nanoTime() - startNanoTime) / 1000000);
		return monitor_time_formatter.format(nowDate);
	}

	@Override
	protected Logger getLogger() {
		return log;
	}

	@Override
	protected LinkedList<ArrayList<String>> OnServerRequest(String input) {
		log.info("Received command: " + input);
		synchronized (currentStatements) {
			// TODO maybe validate

			String srvCmd_connCheck = "connCheck";
			String srvCmd_get_statements = "getStatements";

			if (srvCmd_connCheck.equals(input)) {
				final ArrayList<String> payload = new ArrayList<>(Arrays.asList(""));
				log.info("connCheck: " + Arrays.toString(payload.toArray()));
				return new LinkedList<>(Collections.singletonList(payload));
			} else if (srvCmd_get_statements.equals(input)) {
				log.info("getStatements: " + currentStatements.size());
				LinkedList<ArrayList<String>> logsToSend = new LinkedList<>(currentStatements);
				currentStatements.clear();
				return logsToSend;
			} else if (srvCmd_close.equals(input)) {
				// In addition to the logic above, this command is handled in
				// org.droidmate.monitor.MonitorJavaTemplate.MonitorTcpServer.shouldCloseServerSocket
				log.info("closing");
				return new LinkedList<>();
			} else {
				log.warning("! Unexpected command from DroidMate TCP client. The command: " + input);
				return new LinkedList<>();
			}
		}
	}

	@Override
	protected boolean shouldCloseServerSocket(String serverInput) {
		return srvCmd_close.equals(serverInput);
	}
}

// endregion
