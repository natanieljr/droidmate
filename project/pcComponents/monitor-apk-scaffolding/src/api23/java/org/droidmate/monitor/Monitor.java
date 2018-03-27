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

package org.droidmate.monitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.droidmate.misc.MonitorConstants;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectOutput;

import java.io.*;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.*;

// org.droidmate.monitor.MonitorSrcTemplate:API_19_UNCOMMENT_LINES
// import de.uds.infsec.instrumentation.Instrumentation;
// import de.uds.infsec.instrumentation.annotation.Redirect;
// import de.uds.infsec.instrumentation.util.Signature;

import de.larma.arthook.*;

import org.droidmate.monitor.IMonitorHook;
import org.droidmate.monitor.MonitorHook;

/**
 * <p>
 * This class will be used by {@code MonitorGenerator} to create {@code Monitor.java} deployed on the device. This class will be
 * first copied by appropriate gradle task of monitor-generator project to its resources dir. Then it will be handled to
 * {@code org.droidmate.monitor.MonitorSrcTemplate} for further processing.
 *
 * </p><p>
 * Note that the final generated version of this file, after running {@code :project:pcComponents:monitor-generator:build}, will be placed in
 * <pre><code>
 *   [repo root]\dev\droidmate\projects\monitor-generator\monitor-apk-scaffolding\src\org\droidmate\monitor_generator\generated\Monitor.java
 * </code></pre>
 *
 * </p><p>
 * To check if the process of converting this file to a proper {@code Monitor.java} works correctly, see:
 * {@code org.droidmate.monitor.MonitorGeneratorFrontendTest#Generates DroidMate monitor()}.
 *
 * </p><p>
 * Note: The resulting class deployed to the device will be compiled with legacy ant script from Android SDK that supports only
 * Java 5.
 *
 * </p><p>
 * See also:<br/>
 * {@code org.droidmate.monitor.MonitorSrcTemplate}<br/>
 * {@code org.droidmate.monitor.RedirectionsGenerator}
 * </p>
 */
@SuppressLint("NewApi")
@SuppressWarnings("Convert2Diamond")
// !!! DUPLICATION WARNING !!! of class name and location with the build.gradle script of monitor-generator
public class Monitor
{
	/**
	 * <p> Contains API logs gathered by monitor, to be transferred to the host machine when appropriate command is read by the
	 * TCP server.
	 * <p>
	 * </p><p>
	 * Each log is a 3 element array obeying following contract:<br/>
	 * log[0]: process ID of the log<br/>
	 * log[1]: timestamp of the log<br/>
	 * log[2]: the payload of the log (method name, parameter values, stack trace, etc.)
	 * <p>
	 * </p>
	 *
	 * @see MonitorJavaTemplate#addCurrentLogs(java.lang.String)
	 */
	final static List<ArrayList<String>> currentLogs = new ArrayList<ArrayList<String>>();
	private final static String ESCAPE_CHAR = "\\";
	private final static String VALUESTRING_ENCLOSCHAR = "'";
	private static final String FORMAT_STRING = "TId:%s;objCls:'%s';mthd:'%s';retCls:'void';params:'java.lang.String' '%s' 'java.lang.Object[]' %s;stacktrace:'%s'";
	private static final SimpleDateFormat monitor_time_formatter = new SimpleDateFormat(MonitorConstants.Companion.getMonitor_time_formatter_pattern(), MonitorConstants.Companion.getMonitor_time_formatter_locale());
	//endregion

	//region TCP server code
	/**
	 * @see #getNowDate()
	 */
	private static final Date startDate = new Date();
	/**
	 * @see #getNowDate()
	 */
	private static final long startNanoTime = System.nanoTime();
	private final static HashMap<ApiPolicyId, ApiPolicy> apiPolicies = new HashMap<>();
	private static MonitorTcpServer server;

	//endregion

	//region Helper code
	private static Context context;
	//region Class init code
	public Monitor()
	{
		this(false);
	}
	//region Class init code
	public Monitor(boolean skip)
	{
		if (skip)
			return;

		Log.v(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsg_ctor_start());
		try {
			server = startMonitorTCPServer();
			Log.i(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsg_ctor_success() + server.port);

		} catch (Throwable e) {
			Log.e(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsg_ctor_failure(), e);
		}
	}

	@SuppressWarnings("ConstantConditions")
	private static MonitorTcpServer startMonitorTCPServer() throws Throwable {
		Log.v(MonitorConstants.Companion.getTag_mjt(), "startMonitorTCPServer(): entering");

		MonitorTcpServer tcpServer = new MonitorTcpServer();

		final int port = getPort();
		Thread serverThread = tcpServer.tryStart(port);

		if (serverThread == null) {
			throw new Exception("startMonitorTCPServer(): Port is not available.");
		}

		if (serverThread == null) throw new AssertionError();
		if (tcpServer.isClosed()) throw new AssertionError();

		Log.d(MonitorConstants.Companion.getTag_mjt(), "startMonitorTCPServer(): SUCCESS port: " + port + " PID: " + getPid());
		return tcpServer;
	}

	private static String escapeEnclosings(String paramString) {
		return paramString.replace(VALUESTRING_ENCLOSCHAR, ESCAPE_CHAR + VALUESTRING_ENCLOSCHAR);
	}

	private static String trimToLogSize(String paramString) {
        /*
        Logcat buffer size is 4096 [1]. I have encountered a case in which intent's string extra has eaten up entire log line,
        preventing the remaining parts of the log (in particular, stack trace) to be transferred to DroidMate,
        causing regex match fail. This is how the offending intent value looked like:

          intent:#Intent;action=com.picsart.studio.notification.action;S.extra.result.string=%7B%22response%22%3A%5B%7B%...
          ...<and_so_on_until_entire_line_buffer_was_eaten>

        [1] http://stackoverflow.com/questions/6321555/what-is-the-size-limit-for-logcat
        */
		if (paramString.length() > 1024) {
			return paramString.substring(0, 1024 - 24) + "_TRUNCATED_TO_1000_CHARS";
		}
		return paramString;
	}

	static String objectToString(Object param) {
		String result = "";
		if (param == null)
			result = "null";
		else if (param instanceof android.content.Intent) {
			String paramStr = ((android.content.Intent) param).toUri(1);
			if (!paramStr.endsWith("end")) throw new AssertionError();
			result = paramStr;
		} else if (param.getClass().isArray()) {
			result = Arrays.deepToString(convertToObjectArray(param));
		} else {
			result = param.toString();
		}

		//result = trimToLogSize(result);
		return escapeEnclosings(result);
	}

	// Copied from http://stackoverflow.com/a/16428065/986533
	private static Object[] convertToObjectArray(Object array) {
		Class ofArray = array.getClass().getComponentType();
		if (ofArray.isPrimitive()) {
			ArrayList<Object> ar = new ArrayList<>();
			int length = Array.getLength(array);
			for (int i = 0; i < length; i++) {
				ar.add(Array.get(array, i));
			}
			return ar.toArray();
		} else {
			return (Object[]) array;
		}
	}

	private static String getStackTrace() {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < stackTrace.length; i++) {
			sb.append(stackTrace[i].toString());
			if (i < stackTrace.length - 1)
				sb.append("->");
		}
		return sb.toString();
	}

	private static long getThreadId() {
		return Thread.currentThread().getId();
	}

	/**
	 * <p>
	 * Called by monitor code to log Android API calls. Calls to this methods are generated in:
	 * <pre>
	 * org.droidmate.monitor.RedirectionsGenerator#generateCtorCallsAndTargets(java.util.List)
	 * org.droidmate.monitor.RedirectionsGenerator#generateMethodTargets(java.util.List)</pre>
	 * </p>
	 * This method has to be accessed in a synchronized manner to ensure proper access to the {@code currentLogs} list and also
	 * to ensure calls to {@code SimpleDateFormat.format(new Date())} return correct results.
	 * If there was interleaving between threads, the calls non-deterministically returned invalid dates,
	 * which caused {@code LocalDateTime.parse()} on the host machine, called by
	 * {@code org.droidmate.exploration.device.ApiLogsReader.extractLogcatMessagesFromTcpMessages()}
	 * to fail with exceptions like
	 * <pre>java.time.format.DateTimeParseException: Text '2015-08-21 019:15:43.607' could not be parsed at index 13</pre>
	 * <p>
	 * Examples of two different values returned by two consecutive calls to the faulty method,
	 * first bad, second good:
	 * <pre>
	 * 2015-0008-0021 0019:0015:43.809
	 * 2015-08-21 19:15:43.809
	 *
	 * 2015-08-21 19:015:43.804
	 * 2015-08-21 19:15:43.804</pre>
	 * More examples of faulty output:
	 * <pre>
	 *   2015-0008-05 09:24:12.163
	 *   2015-0008-19 22:49:50.492
	 *   2015-08-21 18:50:047.169
	 *   2015-08-21 19:03:25.24
	 *   2015-08-28 23:03:28.0453</pre>
	 */
	@SuppressWarnings("unused") // See javadoc
	private static void addCurrentLogs(String payload) {
		synchronized (currentLogs) {
//      Log.v(tag_mjt, "addCurrentLogs(" + payload + ")");
			String now = getNowDate();

//      Log.v(tag_mjt, "currentLogs.add(new ArrayList<String>(Arrays.asList(getPid(), now, payload)));");
			currentLogs.add(new ArrayList<String>(Arrays.asList(getPid(), now, payload)));

//      Log.v(tag_mjt, "addCurrentLogs(" + payload + "): DONE");
		}
	}

	/**
	 * <p>
	 * We use this more complex solution instead of simple {@code new Date()} because the simple solution uses
	 * {@code System.currentTimeMillis()} which is imprecise, as described here:
	 * http://stackoverflow.com/questions/2978598/will-sytem-currenttimemillis-always-return-a-value-previous-calls<br/>
	 * http://stackoverflow.com/a/2979239/986533
	 * <p>
	 * </p><p>
	 * Instead, we construct Date only once ({@link #startDate}), on startup, remembering also its time offset from last boot
	 * ({@link #startNanoTime}) and then we add offset to it in {@code System.nanoTime()},  which is precise.
	 * <p>
	 * </p>
	 */
	private static String getNowDate() {
//    Log.v(tag_mjt, "final Date nowDate = new Date(startDate.getTime() + (System.nanoTime() - startNanoTime) / 1000000);");
		final Date nowDate = new Date(startDate.getTime() + (System.nanoTime() - startNanoTime) / 1000000);

//    Log.v(tag_mjt, "final String formattedDate = monitor_time_formatter.format(nowDate);");
		final String formattedDate = monitor_time_formatter.format(nowDate);

//    Log.v(tag_mjt, "return formattedDate;");
		return formattedDate;
	}

	private static String getPid() {
		return String.valueOf(android.os.Process.myPid());
	}

	private static boolean skipLine(String line) {
		return (line.trim().length() == 0) ||
						!line.contains("\t") ||
						line.startsWith("#");
	}

	private static void processLine(String line) {
		if (skipLine(line))
			return;

		// first field is method signature
		// last field is policy
		// anything in between are URIs
		String[] lineData = line.split("\t");

		String methodName = lineData[0].replaceAll("\\s+", "");
		String policyStr = lineData[lineData.length - 1].trim();

		ApiPolicy policy = ApiPolicy.valueOf(policyStr);
		List<String> uriList = new ArrayList<>();
		uriList.addAll(Arrays.asList(lineData).subList(1, lineData.length - 1));

		apiPolicies.put(new Monitor(true).new ApiPolicyId(methodName, uriList.toArray(new String[0])), policy);
	}

	private static void initializeApiPolicies() throws Exception {
		// loads every time to allow restrictions to be dynamically changed
		apiPolicies.clear();

		File policiesFile = new File("#POLICIES_FILE_PATH");
		if (policiesFile.exists()) {
			try (BufferedReader reader = new BufferedReader(new FileReader(policiesFile))) {
				String line;
				while ((line = reader.readLine()) != null) {
					processLine(line);
				}
			}
		}
		//else
		//  Log.w(MonitorConstants.Companion.getTag_srv(), "Api policies file not found. Continuing with default behavior (Allow)");
	}

	/**
	 * Check is the API call should be allowed or not
	 *
	 * @param methodName Method that should have its policy checked
	 * @param uriList    List of resources being accessed by the method (if any)
	 * @return How how DroidMate behave regarding the policy. Default return is ApiPolicy.Allow
	 */
	@SuppressWarnings("unused")
	private static ApiPolicy getPolicy(String methodName, List<Uri> uriList) {
		try {
			initializeApiPolicies();

			for (ApiPolicyId apiId : apiPolicies.keySet()) {
				List<String> uriListStr = new ArrayList<>();
				for (Uri uri : uriList) {
					uriListStr.add(uri.toString());
				}

				if (apiId.affects(methodName, uriListStr))
					return apiPolicies.get(apiId);
			}
		} catch (Exception e) {
			// Default behavior is to allow
			return ApiPolicy.Allow;
		}

		return ApiPolicy.Allow;
	}

	private static int getPort() throws Exception {
		File file = new File("/data/local/tmp/port.tmp");
		FileInputStream fis = new FileInputStream(file);
		byte[] data = new byte[(int) file.length()];
		fis.read(data);
		fis.close();

		return Integer.parseInt(new String(data, "UTF-8"));
	}

	private static void redirectConstructors() {
		ClassLoader[] classLoaders = {Thread.currentThread().getContextClassLoader(), Monitor.class.getClassLoader()};
	}

	/**
	 * Called by the inlined Application class when the inlined AUE launches activity, as done by
	 * org.droidmate.exploration.device.IRobustDevice#launchApp(org.droidmate.android_sdk.IApk)
	 */
	@SuppressWarnings("unused")
	public void init(android.content.Context initContext) {
		Log.v(MonitorConstants.Companion.getTag_mjt(), "init(): entering");
		context = initContext;
		if (server == null) {
			Log.w(MonitorConstants.Companion.getTag_mjt(), "init(): didn't set context for MonitorTcpServer, as the server is null.");
		} else {
			server.context = context;
		}

		// org.droidmate.monitor.MonitorSrcTemplate:API_19_UNCOMMENT_LINES
		// Instrumentation.processClass(Monitor.class);

		ArtHook.hook(Monitor.class);

		redirectConstructors();

		monitorHook.init(context);

		Log.d(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsgPrefix_init_success() + context.getPackageName());
	}

	enum ApiPolicy {
		Allow,
		Deny,
		Mock
	}

	static class MonitorTcpServer extends TcpServerBase<String, ArrayList<ArrayList<String>>> {

		public Context context;

		protected MonitorTcpServer() {
			super();
		}

		@Override
		protected ArrayList<ArrayList<String>> OnServerRequest(String input) {
			synchronized (currentLogs) {
				validateLogsAreNotFromMonitor(currentLogs);

				if (MonitorConstants.Companion.getSrvCmd_connCheck().equals(input)) {
					final ArrayList<String> payload = new ArrayList<String>(Arrays.asList(getPid(), getPackageName(), ""));
					return new ArrayList<ArrayList<String>>(Collections.singletonList(payload));

				} else if (MonitorConstants.Companion.getSrvCmd_get_logs().equals(input)) {
					ArrayList<ArrayList<String>> logsToSend = new ArrayList<ArrayList<String>>(currentLogs);
					currentLogs.clear();

					return logsToSend;

				} else if (MonitorConstants.Companion.getSrvCmd_get_time().equals(input)) {
					final String time = getNowDate();

					final ArrayList<String> payload = new ArrayList<String>(Arrays.asList(time, null, null));

					Log.d(MonitorConstants.Companion.getTag_srv(), "getTime: " + time);
					return new ArrayList<ArrayList<String>>(Collections.singletonList(payload));

				} else if (MonitorConstants.Companion.getSrvCmd_close().equals(input)) {
					monitorHook.finalizeMonitorHook();

					// In addition to the logic above, this command is handled in
					// org.droidmate.monitor.MonitorJavaTemplate.MonitorTcpServer.shouldCloseServerSocket

					return new ArrayList<ArrayList<String>>();

				} else {
					Log.e(MonitorConstants.Companion.getTag_srv(), "! Unexpected command from DroidMate TCP client. The command: " + input);
					return new ArrayList<ArrayList<String>>();
				}
			}
		}

		private String getPackageName() {
			if (this.context != null)
				return this.context.getPackageName();
			else
				return "package name unavailable: context is null";
		}

		/**
		 * <p>
		 * This method ensures the logs do not come from messages logged by the MonitorTcpServer or
		 * MonitorJavaTemplate itself. This would be a bug and thus it will cause an assertion failure in this method.
		 * <p>
		 * </p>
		 *
		 * @param currentLogs Currently recorded set of monitored logs that will be validated, causing AssertionError if validation fails.
		 */
		private void validateLogsAreNotFromMonitor(List<ArrayList<String>> currentLogs) {
			for (ArrayList<String> log : currentLogs) {
				// ".get(2)" gets the payload. For details, see the doc of the param passed to this method.
				String msgPayload = log.get(2);
				failOnLogsFromMonitorTCPServerOrMonitorJavaTemplate(msgPayload);

			}
		}

		private void failOnLogsFromMonitorTCPServerOrMonitorJavaTemplate(String msgPayload) {
			if (msgPayload.contains(MonitorConstants.Companion.getTag_srv()) || msgPayload.contains(MonitorConstants.Companion.getTag_mjt()))
				throw new AssertionError(
								"Attempt to log a message whose payload contains " +
												MonitorConstants.Companion.getTag_srv() + " or " + MonitorConstants.Companion.getTag_mjt() + ". The message payload: " + msgPayload);
		}

		@Override
		protected boolean shouldCloseServerSocket(String serverInput) {
			return MonitorConstants.Companion.getSrvCmd_close().equals(serverInput);
		}
	}

	static class SerializationHelper {
		private static FSTConfiguration serializationConfig = FSTConfiguration.createDefaultConfiguration();

		static void writeObjectToStream(DataOutputStream outputStream, Object toWrite) throws IOException {
			// write object
			FSTObjectOutput objectOutput = serializationConfig.getObjectOutput(); // could also do new with minor perf impact
			// write object to internal buffer
			objectOutput.writeObject(toWrite);
			// write length
			outputStream.writeInt(objectOutput.getWritten());
			// write bytes
			outputStream.write(objectOutput.getBuffer(), 0, objectOutput.getWritten());

			objectOutput.flush(); // return for reuse to conf
		}

		static Object readObjectFromStream(DataInputStream inputStream) throws IOException, ClassNotFoundException {
			int len = inputStream.readInt();
			byte[] buffer = new byte[len]; // this could be reused !
			while (len > 0)
				len -= inputStream.read(buffer, buffer.length - len, len);
			return serializationConfig.getObjectInput(buffer).readObject();
		}
	}

	// !!! DUPLICATION WARNING !!! with org.droidmate.uiautomator_daemon.UiautomatorDaemonTcpServerBase
	static abstract class TcpServerBase<ServerInputT extends Serializable, ServerOutputT extends Serializable> {
		int port;
		private ServerSocket serverSocket = null;
		private SocketException serverSocketException = null;

		protected TcpServerBase() {
			super();
		}

		protected abstract ServerOutputT OnServerRequest(ServerInputT input);

		protected abstract boolean shouldCloseServerSocket(ServerInputT serverInput);

		public Thread tryStart(int port) throws Exception {
			Log.v(MonitorConstants.Companion.getTag_srv(), String.format("tryStart(port:%d): entering", port));
			this.serverSocket = null;
			this.serverSocketException = null;
			this.port = port;

			MonitorServerRunnable monitorServerRunnable = new MonitorServerRunnable();
			Thread serverThread = new Thread(monitorServerRunnable);
			// For explanation why this synchronization is necessary, see MonitorServerRunnable.run() method synchronized {} block.
			synchronized (monitorServerRunnable) {
				if (!(serverSocket == null && serverSocketException == null)) throw new AssertionError();
				serverThread.start();
				monitorServerRunnable.wait();
				// Either a serverSocket has been established, or an exception was thrown, but not both.
				//noinspection SimplifiableBooleanExpression
				if (!(serverSocket != null ^ serverSocketException != null)) throw new AssertionError();
			}
			if (serverSocketException != null) {

				String cause = (serverSocketException.getCause() != null) ? serverSocketException.getCause().getMessage() : serverSocketException.getMessage();
				if ("bind failed: EADDRINUSE (Address already in use)".equals(cause)) {
					Log.v(MonitorConstants.Companion.getTag_srv(), "tryStart(port:" + port + "): FAILURE Failed to start TCP server because " +
									"'bind failed: EADDRINUSE (Address already in use)'. " +
									"Returning null Thread.");

					return null;

				} else {
					throw new Exception(String.format("Failed to start monitor TCP server thread for port %s. " +
									"Cause of this exception is the one returned by the failed thread.", port),
									serverSocketException);
				}
			}

			Log.d(MonitorConstants.Companion.getTag_srv(), "tryStart(port:" + port + "): SUCCESS");
			return serverThread;
		}

		public void closeServerSocket() {
			try {
				serverSocket.close();
				Log.d(MonitorConstants.Companion.getTag_srv(), String.format("serverSocket.close(): SUCCESS port %s", port));

			} catch (IOException e) {
				Log.e(MonitorConstants.Companion.getTag_srv(), String.format("serverSocket.close(): FAILURE port %s", port));
			}
		}

		public boolean isClosed() {
			return serverSocket.isClosed();
		}

		private class MonitorServerRunnable implements Runnable {


			public void run() {

				Log.v(MonitorConstants.Companion.getTag_run(), String.format("run(): entering port:%d", port));
				try {

					// Synchronize to ensure the parent thread (the one which started this one) will continue only after one of these two
					// is true:
					// - serverSocket was successfully initialized
					// - exception was thrown and assigned to a field and  this thread exitted
					synchronized (this) {
						try {
							Log.v(MonitorConstants.Companion.getTag_run(), String.format("serverSocket = new ServerSocket(%d)", port));
							serverSocket = new ServerSocket(port);
							Log.v(MonitorConstants.Companion.getTag_run(), String.format("serverSocket = new ServerSocket(%d): SUCCESS", port));
						} catch (SocketException e) {
							serverSocketException = e;
						}

						if (serverSocketException != null) {
							Log.d(MonitorConstants.Companion.getTag_run(), "serverSocket = new ServerSocket(" + port + "): FAILURE " +
											"aborting further thread execution.");
							this.notify();
							return;
						} else {
							this.notify();
						}
					}

					if (serverSocket == null) throw new AssertionError();
					if (serverSocketException != null) throw new AssertionError();

					while (!serverSocket.isClosed()) {
						Log.v(MonitorConstants.Companion.getTag_run(), String.format("clientSocket = serverSocket.accept() / port:%d", port));
						Socket clientSocket = serverSocket.accept();
						Log.v(MonitorConstants.Companion.getTag_run(), String.format("clientSocket = serverSocket.accept(): SUCCESS / port:%d", port));

						////ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
						DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

						/*
						 * Flushing done to prevent client blocking on creation of input stream reading output from this stream. See:
						 * org.droidmate.device.SerializableTCPClient.queryServer
						 *
						 * References:
						 * 1. http://stackoverflow.com/questions/8088557/getinputstream-blocks
						 * 2. Search for: "Note - The ObjectInputStream constructor blocks until" in:
						 * http://docs.oracle.com/javase/7/docs/platform/serialization/spec/input.html
						 */
						////output.flush();

						////ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
						DataInputStream input = new DataInputStream(clientSocket.getInputStream());
						ServerInputT serverInput;

						try {
							@SuppressWarnings("unchecked") // Without this var here, there is no place to put the "unchecked" suppression warning.
											ServerInputT localVarForSuppressionAnnotation = (ServerInputT) SerializationHelper.readObjectFromStream(input);
							serverInput = localVarForSuppressionAnnotation;

						} catch (Exception e) {
							Log.e(MonitorConstants.Companion.getTag_run(), "! serverInput = input.readObject(): FAILURE " +
											"while reading from clientSocket on port " + port + ". Closing server socket.", e);
							closeServerSocket();
							break;
						}

						ServerOutputT serverOutput;
						Log.d(MonitorConstants.Companion.getTag_run(), String.format("OnServerRequest(%s) / port:%d", serverInput, port));
						serverOutput = OnServerRequest(serverInput);
						SerializationHelper.writeObjectToStream(output, serverOutput);
						clientSocket.close();

						if (shouldCloseServerSocket(serverInput)) {
							Log.v(MonitorConstants.Companion.getTag_run(), String.format("shouldCloseServerSocket(): true / port:%d", port));
							closeServerSocket();
						}
					}

					if (!serverSocket.isClosed()) throw new AssertionError();

					Log.v(MonitorConstants.Companion.getTag_run(), String.format("serverSocket.isClosed() / port:%d", port));

				} catch (SocketTimeoutException e) {
					Log.e(MonitorConstants.Companion.getTag_run(), "! Closing monitor TCP server due to a timeout.", e);
					closeServerSocket();
				} catch (IOException e) {
					Log.e(MonitorConstants.Companion.getTag_run(), "! Exception was thrown while operating monitor TCP server.", e);
				}
			}

		}
	}

	//endregion

	//region Hook code
	public static IMonitorHook monitorHook = new MonitorHook();
	//endregion

	//region Generated code

	private class ApiPolicyId {
		private String method;
		private List<String> uriList = new ArrayList<>();

		public ApiPolicyId(String method, String... uris) {
			this.method = method;
			this.uriList = Arrays.asList(uris);

			assert this.method != null;
		}

		boolean affects(String methodName, List<String> uriList) {
			boolean equal = this.method.equals(methodName.replaceAll("\\s+", ""));

			StringBuilder b = new StringBuilder();
			for (String uri : uriList)
				b.append(uri + "");
			String apiList = b.toString();

			for (String restrictedUri : this.uriList) {
				equal &= apiList.contains(restrictedUri);
			}

			return equal;
		}

		@Override
		public boolean equals(Object other) {
			return (other instanceof ApiPolicyId) &&
							((ApiPolicyId) other).method.equals(this.method) &&
							((ApiPolicyId) other).uriList.equals(this.uriList);
		}
	}

@Hook("android.app.ActivityThread->installContentProviders")
public static void redir_android_app_ActivityThread_installContentProviders_53(Object _this , android.content.Context p0, java.util.List p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.app.ActivityThread';mthd: 'installContentProviders';retCls: 'void';params: 'android.content.Context' '" +objectToString(p0)+ "' 'java.util.List' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.app.ActivityThread.installContentProviders(android.content.Context, java.util.List)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.app.ActivityThread->installContentProviders was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.app.ActivityThread->installContentProviders cannot be determined.");
    }
}
    
@Hook("android.app.ActivityManager->getRecentTasks")
public static java.util.List redir_android_app_ActivityManager_getRecentTasks_54(Object _this , int p0, int p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.app.ActivityManager';mthd: 'getRecentTasks';retCls: 'java.util.List';params: 'int' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.app.ActivityManager.getRecentTasks(int, int)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (java.util.List) monitorHook.hookAfterApiCall(logSignature, (java.util.List) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.app.ActivityManager->getRecentTasks was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.app.ActivityManager->getRecentTasks cannot be determined.");
    }
}
    
@Hook("android.app.ActivityManager->getRunningTasks")
public static java.util.List redir_android_app_ActivityManager_getRunningTasks_55(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.app.ActivityManager';mthd: 'getRunningTasks';retCls: 'java.util.List';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.app.ActivityManager.getRunningTasks(int)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (java.util.List) monitorHook.hookAfterApiCall(logSignature, (java.util.List) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.app.ActivityManager->getRunningTasks was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.app.ActivityManager->getRunningTasks cannot be determined.");
    }
}
    
@Hook("android.bluetooth.BluetoothHeadset->startVoiceRecognition")
public static boolean redir_android_bluetooth_BluetoothHeadset_startVoiceRecognition_74(Object _this , android.bluetooth.BluetoothDevice p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.bluetooth.BluetoothHeadset';mthd: 'startVoiceRecognition';retCls: 'boolean';params: 'android.bluetooth.BluetoothDevice' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.bluetooth.BluetoothHeadset.startVoiceRecognition(android.bluetooth.BluetoothDevice)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.bluetooth.BluetoothHeadset->startVoiceRecognition was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.bluetooth.BluetoothHeadset->startVoiceRecognition cannot be determined.");
    }
}
    
@Hook("android.bluetooth.BluetoothHeadset->stopVoiceRecognition")
public static boolean redir_android_bluetooth_BluetoothHeadset_stopVoiceRecognition_75(Object _this , android.bluetooth.BluetoothDevice p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.bluetooth.BluetoothHeadset';mthd: 'stopVoiceRecognition';retCls: 'boolean';params: 'android.bluetooth.BluetoothDevice' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.bluetooth.BluetoothHeadset.stopVoiceRecognition(android.bluetooth.BluetoothDevice)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.bluetooth.BluetoothHeadset->stopVoiceRecognition was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.bluetooth.BluetoothHeadset->stopVoiceRecognition cannot be determined.");
    }
}
    
@Hook("android.hardware.Camera->open")
public static android.hardware.Camera redir_android_hardware_Camera_open_97(int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.hardware.Camera';mthd: 'open';retCls: 'android.hardware.Camera';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.hardware.Camera.open(int)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invokeStatic (p0);
        return (android.hardware.Camera) monitorHook.hookAfterApiCall(logSignature, (android.hardware.Camera) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.hardware.Camera->open was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.hardware.Camera->open cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->addGpsStatusListener")
public static boolean redir_android_location_LocationManager_addGpsStatusListener_99(Object _this , android.location.GpsStatus.Listener p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'addGpsStatusListener';retCls: 'boolean';params: 'android.location.GpsStatus.Listener' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.addGpsStatusListener(android.location.GpsStatus.Listener)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->addGpsStatusListener was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->addGpsStatusListener cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->addNmeaListener")
public static boolean redir_android_location_LocationManager_addNmeaListener_100(Object _this , android.location.GpsStatus.NmeaListener p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'addNmeaListener';retCls: 'boolean';params: 'android.location.GpsStatus.NmeaListener' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.addNmeaListener(android.location.GpsStatus.NmeaListener)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->addNmeaListener was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->addNmeaListener cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->addProximityAlert")
public static void redir_android_location_LocationManager_addProximityAlert_101(Object _this , double p0, double p1, float p2, long p3, android.app.PendingIntent p4)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'addProximityAlert';retCls: 'void';params: 'double' '" +objectToString(p0)+ "' 'double' '" +objectToString(p1)+ "' 'float' '" +objectToString(p2)+ "' 'long' '" +objectToString(p3)+ "' 'android.app.PendingIntent' '" +objectToString(p4)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.addProximityAlert(double, double, float, long, android.app.PendingIntent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->addProximityAlert was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->addProximityAlert cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->addTestProvider")
public static void redir_android_location_LocationManager_addTestProvider_102(Object _this , java.lang.String p0, boolean p1, boolean p2, boolean p3, boolean p4, boolean p5, boolean p6, boolean p7, int p8, int p9)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'addTestProvider';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'boolean' '" +objectToString(p1)+ "' 'boolean' '" +objectToString(p2)+ "' 'boolean' '" +objectToString(p3)+ "' 'boolean' '" +objectToString(p4)+ "' 'boolean' '" +objectToString(p5)+ "' 'boolean' '" +objectToString(p6)+ "' 'boolean' '" +objectToString(p7)+ "' 'int' '" +objectToString(p8)+ "' 'int' '" +objectToString(p9)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.addTestProvider(java.lang.String, boolean, boolean, boolean, boolean, boolean, boolean, boolean, int, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->addTestProvider was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->addTestProvider cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->clearTestProviderEnabled")
public static void redir_android_location_LocationManager_clearTestProviderEnabled_103(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'clearTestProviderEnabled';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.clearTestProviderEnabled(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->clearTestProviderEnabled was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->clearTestProviderEnabled cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->clearTestProviderLocation")
public static void redir_android_location_LocationManager_clearTestProviderLocation_104(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'clearTestProviderLocation';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.clearTestProviderLocation(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->clearTestProviderLocation was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->clearTestProviderLocation cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->clearTestProviderStatus")
public static void redir_android_location_LocationManager_clearTestProviderStatus_105(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'clearTestProviderStatus';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.clearTestProviderStatus(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->clearTestProviderStatus was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->clearTestProviderStatus cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->getBestProvider")
public static java.lang.String redir_android_location_LocationManager_getBestProvider_106(Object _this , android.location.Criteria p0, boolean p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'getBestProvider';retCls: 'java.lang.String';params: 'android.location.Criteria' '" +objectToString(p0)+ "' 'boolean' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.getBestProvider(android.location.Criteria, boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->getBestProvider was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->getBestProvider cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->getLastKnownLocation")
public static android.location.Location redir_android_location_LocationManager_getLastKnownLocation_107(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'getLastKnownLocation';retCls: 'android.location.Location';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.getLastKnownLocation(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (android.location.Location) monitorHook.hookAfterApiCall(logSignature, (android.location.Location) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->getLastKnownLocation was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->getLastKnownLocation cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->getProvider")
public static android.location.LocationProvider redir_android_location_LocationManager_getProvider_108(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'getProvider';retCls: 'android.location.LocationProvider';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.getProvider(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (android.location.LocationProvider) monitorHook.hookAfterApiCall(logSignature, (android.location.LocationProvider) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->getProvider was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->getProvider cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->getProviders")
public static java.util.List redir_android_location_LocationManager_getProviders_109(Object _this , android.location.Criteria p0, boolean p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'getProviders';retCls: 'java.util.List';params: 'android.location.Criteria' '" +objectToString(p0)+ "' 'boolean' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.getProviders(android.location.Criteria, boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (java.util.List) monitorHook.hookAfterApiCall(logSignature, (java.util.List) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->getProviders was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->getProviders cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->getProviders")
public static java.util.List redir_android_location_LocationManager_getProviders_110(Object _this , boolean p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'getProviders';retCls: 'java.util.List';params: 'boolean' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.getProviders(boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (java.util.List) monitorHook.hookAfterApiCall(logSignature, (java.util.List) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->getProviders was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->getProviders cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->isProviderEnabled")
public static boolean redir_android_location_LocationManager_isProviderEnabled_111(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'isProviderEnabled';retCls: 'boolean';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.isProviderEnabled(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->isProviderEnabled was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->isProviderEnabled cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->removeTestProvider")
public static void redir_android_location_LocationManager_removeTestProvider_112(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'removeTestProvider';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.removeTestProvider(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->removeTestProvider was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->removeTestProvider cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestLocationUpdates")
public static void redir_android_location_LocationManager_requestLocationUpdates_113(Object _this , long p0, float p1, android.location.Criteria p2, android.app.PendingIntent p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestLocationUpdates';retCls: 'void';params: 'long' '" +objectToString(p0)+ "' 'float' '" +objectToString(p1)+ "' 'android.location.Criteria' '" +objectToString(p2)+ "' 'android.app.PendingIntent' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestLocationUpdates(long, float, android.location.Criteria, android.app.PendingIntent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestLocationUpdates was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestLocationUpdates cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestLocationUpdates")
public static void redir_android_location_LocationManager_requestLocationUpdates_114(Object _this , long p0, float p1, android.location.Criteria p2, android.location.LocationListener p3, android.os.Looper p4)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestLocationUpdates';retCls: 'void';params: 'long' '" +objectToString(p0)+ "' 'float' '" +objectToString(p1)+ "' 'android.location.Criteria' '" +objectToString(p2)+ "' 'android.location.LocationListener' '" +objectToString(p3)+ "' 'android.os.Looper' '" +objectToString(p4)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestLocationUpdates(long, float, android.location.Criteria, android.location.LocationListener, android.os.Looper)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestLocationUpdates was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestLocationUpdates cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestLocationUpdates")
public static void redir_android_location_LocationManager_requestLocationUpdates_115(Object _this , java.lang.String p0, long p1, float p2, android.app.PendingIntent p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestLocationUpdates';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'long' '" +objectToString(p1)+ "' 'float' '" +objectToString(p2)+ "' 'android.app.PendingIntent' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestLocationUpdates(java.lang.String, long, float, android.app.PendingIntent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestLocationUpdates was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestLocationUpdates cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestLocationUpdates")
public static void redir_android_location_LocationManager_requestLocationUpdates_116(Object _this , java.lang.String p0, long p1, float p2, android.location.LocationListener p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestLocationUpdates';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'long' '" +objectToString(p1)+ "' 'float' '" +objectToString(p2)+ "' 'android.location.LocationListener' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestLocationUpdates(java.lang.String, long, float, android.location.LocationListener)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestLocationUpdates was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestLocationUpdates cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestLocationUpdates")
public static void redir_android_location_LocationManager_requestLocationUpdates_117(Object _this , java.lang.String p0, long p1, float p2, android.location.LocationListener p3, android.os.Looper p4)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestLocationUpdates';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'long' '" +objectToString(p1)+ "' 'float' '" +objectToString(p2)+ "' 'android.location.LocationListener' '" +objectToString(p3)+ "' 'android.os.Looper' '" +objectToString(p4)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestLocationUpdates(java.lang.String, long, float, android.location.LocationListener, android.os.Looper)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestLocationUpdates was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestLocationUpdates cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestSingleUpdate")
public static void redir_android_location_LocationManager_requestSingleUpdate_118(Object _this , android.location.Criteria p0, android.app.PendingIntent p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestSingleUpdate';retCls: 'void';params: 'android.location.Criteria' '" +objectToString(p0)+ "' 'android.app.PendingIntent' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestSingleUpdate(android.location.Criteria, android.app.PendingIntent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestSingleUpdate was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestSingleUpdate cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestSingleUpdate")
public static void redir_android_location_LocationManager_requestSingleUpdate_119(Object _this , android.location.Criteria p0, android.location.LocationListener p1, android.os.Looper p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestSingleUpdate';retCls: 'void';params: 'android.location.Criteria' '" +objectToString(p0)+ "' 'android.location.LocationListener' '" +objectToString(p1)+ "' 'android.os.Looper' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestSingleUpdate(android.location.Criteria, android.location.LocationListener, android.os.Looper)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestSingleUpdate was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestSingleUpdate cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestSingleUpdate")
public static void redir_android_location_LocationManager_requestSingleUpdate_120(Object _this , java.lang.String p0, android.app.PendingIntent p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestSingleUpdate';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'android.app.PendingIntent' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestSingleUpdate(java.lang.String, android.app.PendingIntent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestSingleUpdate was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestSingleUpdate cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->requestSingleUpdate")
public static void redir_android_location_LocationManager_requestSingleUpdate_121(Object _this , java.lang.String p0, android.location.LocationListener p1, android.os.Looper p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'requestSingleUpdate';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'android.location.LocationListener' '" +objectToString(p1)+ "' 'android.os.Looper' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.requestSingleUpdate(java.lang.String, android.location.LocationListener, android.os.Looper)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->requestSingleUpdate was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->requestSingleUpdate cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->sendExtraCommand")
public static boolean redir_android_location_LocationManager_sendExtraCommand_122(Object _this , java.lang.String p0, java.lang.String p1, android.os.Bundle p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'sendExtraCommand';retCls: 'boolean';params: 'java.lang.String' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'android.os.Bundle' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.sendExtraCommand(java.lang.String, java.lang.String, android.os.Bundle)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->sendExtraCommand was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->sendExtraCommand cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->setTestProviderEnabled")
public static void redir_android_location_LocationManager_setTestProviderEnabled_123(Object _this , java.lang.String p0, boolean p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'setTestProviderEnabled';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'boolean' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.setTestProviderEnabled(java.lang.String, boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->setTestProviderEnabled was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->setTestProviderEnabled cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->setTestProviderLocation")
public static void redir_android_location_LocationManager_setTestProviderLocation_124(Object _this , java.lang.String p0, android.location.Location p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'setTestProviderLocation';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'android.location.Location' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.setTestProviderLocation(java.lang.String, android.location.Location)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->setTestProviderLocation was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->setTestProviderLocation cannot be determined.");
    }
}
    
@Hook("android.location.LocationManager->setTestProviderStatus")
public static void redir_android_location_LocationManager_setTestProviderStatus_125(Object _this , java.lang.String p0, int p1, android.os.Bundle p2, long p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.location.LocationManager';mthd: 'setTestProviderStatus';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "' 'android.os.Bundle' '" +objectToString(p2)+ "' 'long' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.location.LocationManager.setTestProviderStatus(java.lang.String, int, android.os.Bundle, long)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.location.LocationManager->setTestProviderStatus was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.location.LocationManager->setTestProviderStatus cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->isBluetoothA2dpOn")
public static boolean redir_android_media_AudioManager_isBluetoothA2dpOn_126(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'isBluetoothA2dpOn';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.isBluetoothA2dpOn()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->isBluetoothA2dpOn was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->isBluetoothA2dpOn cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->isWiredHeadsetOn")
public static boolean redir_android_media_AudioManager_isWiredHeadsetOn_127(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'isWiredHeadsetOn';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.isWiredHeadsetOn()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->isWiredHeadsetOn was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->isWiredHeadsetOn cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->setBluetoothScoOn")
public static void redir_android_media_AudioManager_setBluetoothScoOn_128(Object _this , boolean p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'setBluetoothScoOn';retCls: 'void';params: 'boolean' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.setBluetoothScoOn(boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->setBluetoothScoOn was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->setBluetoothScoOn cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->setMicrophoneMute")
public static void redir_android_media_AudioManager_setMicrophoneMute_129(Object _this , boolean p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'setMicrophoneMute';retCls: 'void';params: 'boolean' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.setMicrophoneMute(boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->setMicrophoneMute was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->setMicrophoneMute cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->setMode")
public static void redir_android_media_AudioManager_setMode_130(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'setMode';retCls: 'void';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.setMode(int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->setMode was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->setMode cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->setParameter")
public static void redir_android_media_AudioManager_setParameter_131(Object _this , java.lang.String p0, java.lang.String p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'setParameter';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.setParameter(java.lang.String, java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->setParameter was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->setParameter cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->setParameters")
public static void redir_android_media_AudioManager_setParameters_132(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'setParameters';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.setParameters(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->setParameters was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->setParameters cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->setSpeakerphoneOn")
public static void redir_android_media_AudioManager_setSpeakerphoneOn_133(Object _this , boolean p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'setSpeakerphoneOn';retCls: 'void';params: 'boolean' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.setSpeakerphoneOn(boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->setSpeakerphoneOn was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->setSpeakerphoneOn cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->startBluetoothSco")
public static void redir_android_media_AudioManager_startBluetoothSco_134(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'startBluetoothSco';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.startBluetoothSco()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->startBluetoothSco was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->startBluetoothSco cannot be determined.");
    }
}
    
@Hook("android.media.AudioManager->stopBluetoothSco")
public static void redir_android_media_AudioManager_stopBluetoothSco_135(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioManager';mthd: 'stopBluetoothSco';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioManager.stopBluetoothSco()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioManager->stopBluetoothSco was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioManager->stopBluetoothSco cannot be determined.");
    }
}
    
@Hook("android.media.AudioRecord-><init>")
public static void redir_android_media_AudioRecord__ctor_136(Object _this , int p0, int p1, int p2, int p3, int p4)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.AudioRecord';mthd: '<init>';retCls: 'void';params: 'int' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "' 'int' '" +objectToString(p2)+ "' 'int' '" +objectToString(p3)+ "' 'int' '" +objectToString(p4)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.AudioRecord.<init>(int, int, int, int, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.AudioRecord-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.AudioRecord-><init> cannot be determined.");
    }
}
    
@Hook("android.media.MediaPlayer->setWakeMode")
public static void redir_android_media_MediaPlayer_setWakeMode_137(Object _this , android.content.Context p0, int p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.MediaPlayer';mthd: 'setWakeMode';retCls: 'void';params: 'android.content.Context' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.MediaPlayer.setWakeMode(android.content.Context, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.MediaPlayer->setWakeMode was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.MediaPlayer->setWakeMode cannot be determined.");
    }
}
    
@Hook("android.media.MediaRecorder->setAudioSource")
public static void redir_android_media_MediaRecorder_setAudioSource_138(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.MediaRecorder';mthd: 'setAudioSource';retCls: 'void';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.MediaRecorder.setAudioSource(int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.MediaRecorder->setAudioSource was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.MediaRecorder->setAudioSource cannot be determined.");
    }
}
    
@Hook("android.media.MediaRecorder->setVideoSource")
public static void redir_android_media_MediaRecorder_setVideoSource_139(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.media.MediaRecorder';mthd: 'setVideoSource';retCls: 'void';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.media.MediaRecorder.setVideoSource(int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.media.MediaRecorder->setVideoSource was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.media.MediaRecorder->setVideoSource cannot be determined.");
    }
}
    
@Hook("android.net.ConnectivityManager->requestRouteToHost")
public static boolean redir_android_net_ConnectivityManager_requestRouteToHost_140(Object _this , int p0, int p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.ConnectivityManager';mthd: 'requestRouteToHost';retCls: 'boolean';params: 'int' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.ConnectivityManager.requestRouteToHost(int, int)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.ConnectivityManager->requestRouteToHost was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.ConnectivityManager->requestRouteToHost cannot be determined.");
    }
}
    
@Hook("android.net.ConnectivityManager->setNetworkPreference")
public static void redir_android_net_ConnectivityManager_setNetworkPreference_148(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.ConnectivityManager';mthd: 'setNetworkPreference';retCls: 'void';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.ConnectivityManager.setNetworkPreference(int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.ConnectivityManager->setNetworkPreference was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.ConnectivityManager->setNetworkPreference cannot be determined.");
    }
}
    
@Hook("android.net.ConnectivityManager->startUsingNetworkFeature")
public static int redir_android_net_ConnectivityManager_startUsingNetworkFeature_180(Object _this , int p0, java.lang.String p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.ConnectivityManager';mthd: 'startUsingNetworkFeature';retCls: 'int';params: 'int' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.ConnectivityManager.startUsingNetworkFeature(int, java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.ConnectivityManager->startUsingNetworkFeature was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.ConnectivityManager->startUsingNetworkFeature cannot be determined.");
    }
}
    
@Hook("android.net.ConnectivityManager->stopUsingNetworkFeature")
public static int redir_android_net_ConnectivityManager_stopUsingNetworkFeature_181(Object _this , int p0, java.lang.String p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.ConnectivityManager';mthd: 'stopUsingNetworkFeature';retCls: 'int';params: 'int' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.ConnectivityManager.stopUsingNetworkFeature(int, java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.ConnectivityManager->stopUsingNetworkFeature was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.ConnectivityManager->stopUsingNetworkFeature cannot be determined.");
    }
}
    
@Hook("android.net.ConnectivityManager->tether")
public static int redir_android_net_ConnectivityManager_tether_182(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.ConnectivityManager';mthd: 'tether';retCls: 'int';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.ConnectivityManager.tether(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.ConnectivityManager->tether was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.ConnectivityManager->tether cannot be determined.");
    }
}
    
@Hook("android.net.ConnectivityManager->untether")
public static int redir_android_net_ConnectivityManager_untether_183(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.ConnectivityManager';mthd: 'untether';retCls: 'int';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.ConnectivityManager.untether(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.ConnectivityManager->untether was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.ConnectivityManager->untether cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager$MulticastLock->acquire")
public static void redir_android_net_wifi_WifiManager$MulticastLock_acquire_206(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager$MulticastLock';mthd: 'acquire';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager$MulticastLock.acquire()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager$MulticastLock->acquire was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager$MulticastLock->acquire cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager$MulticastLock->release")
public static void redir_android_net_wifi_WifiManager$MulticastLock_release_207(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager$MulticastLock';mthd: 'release';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager$MulticastLock.release()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager$MulticastLock->release was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager$MulticastLock->release cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager$WifiLock->acquire")
public static void redir_android_net_wifi_WifiManager$WifiLock_acquire_208(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager$WifiLock';mthd: 'acquire';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager$WifiLock.acquire()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager$WifiLock->acquire was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager$WifiLock->acquire cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager$WifiLock->release")
public static void redir_android_net_wifi_WifiManager$WifiLock_release_209(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager$WifiLock';mthd: 'release';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager$WifiLock.release()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager$WifiLock->release was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager$WifiLock->release cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->addNetwork")
public static int redir_android_net_wifi_WifiManager_addNetwork_210(Object _this , android.net.wifi.WifiConfiguration p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'addNetwork';retCls: 'int';params: 'android.net.wifi.WifiConfiguration' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->addNetwork was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->addNetwork cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->disableNetwork")
public static boolean redir_android_net_wifi_WifiManager_disableNetwork_211(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'disableNetwork';retCls: 'boolean';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.disableNetwork(int)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->disableNetwork was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->disableNetwork cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->disconnect")
public static boolean redir_android_net_wifi_WifiManager_disconnect_212(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'disconnect';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.disconnect()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->disconnect was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->disconnect cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->enableNetwork")
public static boolean redir_android_net_wifi_WifiManager_enableNetwork_213(Object _this , int p0, boolean p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'enableNetwork';retCls: 'boolean';params: 'int' '" +objectToString(p0)+ "' 'boolean' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.enableNetwork(int, boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->enableNetwork was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->enableNetwork cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->initializeMulticastFiltering")
public static boolean redir_android_net_wifi_WifiManager_initializeMulticastFiltering_214(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'initializeMulticastFiltering';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.initializeMulticastFiltering()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->initializeMulticastFiltering was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->initializeMulticastFiltering cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->pingSupplicant")
public static boolean redir_android_net_wifi_WifiManager_pingSupplicant_215(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'pingSupplicant';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.pingSupplicant()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->pingSupplicant was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->pingSupplicant cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->reassociate")
public static boolean redir_android_net_wifi_WifiManager_reassociate_216(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'reassociate';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.reassociate()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->reassociate was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->reassociate cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->reconnect")
public static boolean redir_android_net_wifi_WifiManager_reconnect_217(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'reconnect';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.reconnect()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->reconnect was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->reconnect cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->removeNetwork")
public static boolean redir_android_net_wifi_WifiManager_removeNetwork_218(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'removeNetwork';retCls: 'boolean';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.removeNetwork(int)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->removeNetwork was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->removeNetwork cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->saveConfiguration")
public static boolean redir_android_net_wifi_WifiManager_saveConfiguration_219(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'saveConfiguration';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.saveConfiguration()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->saveConfiguration was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->saveConfiguration cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->setWifiApEnabled")
public static boolean redir_android_net_wifi_WifiManager_setWifiApEnabled_220(Object _this , android.net.wifi.WifiConfiguration p0, boolean p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'setWifiApEnabled';retCls: 'boolean';params: 'android.net.wifi.WifiConfiguration' '" +objectToString(p0)+ "' 'boolean' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.setWifiApEnabled(android.net.wifi.WifiConfiguration, boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->setWifiApEnabled was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->setWifiApEnabled cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->setWifiEnabled")
public static boolean redir_android_net_wifi_WifiManager_setWifiEnabled_221(Object _this , boolean p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'setWifiEnabled';retCls: 'boolean';params: 'boolean' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.setWifiEnabled(boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->setWifiEnabled was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->setWifiEnabled cannot be determined.");
    }
}
    
@Hook("android.net.wifi.WifiManager->startScan")
public static boolean redir_android_net_wifi_WifiManager_startScan_222(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.net.wifi.WifiManager';mthd: 'startScan';retCls: 'boolean';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.net.wifi.WifiManager.startScan()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (Boolean) monitorHook.hookAfterApiCall(logSignature, (Boolean) returnVal);
    
        case Mock: 
            return false;
        case Deny: 
            SecurityException e = new SecurityException("API android.net.wifi.WifiManager->startScan was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.net.wifi.WifiManager->startScan cannot be determined.");
    }
}
    
@Hook("android.os.PowerManager$WakeLock->acquire")
public static void redir_android_os_PowerManager$WakeLock_acquire_232(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.os.PowerManager$WakeLock';mthd: 'acquire';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.os.PowerManager$WakeLock.acquire()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.os.PowerManager$WakeLock->acquire was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.os.PowerManager$WakeLock->acquire cannot be determined.");
    }
}
    
@Hook("android.os.PowerManager$WakeLock->acquire")
public static void redir_android_os_PowerManager$WakeLock_acquire_233(Object _this , long p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.os.PowerManager$WakeLock';mthd: 'acquire';retCls: 'void';params: 'long' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.os.PowerManager$WakeLock.acquire(long)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.os.PowerManager$WakeLock->acquire was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.os.PowerManager$WakeLock->acquire cannot be determined.");
    }
}
    
@Hook("android.os.PowerManager$WakeLock->release")
public static void redir_android_os_PowerManager$WakeLock_release_239(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.os.PowerManager$WakeLock';mthd: 'release';retCls: 'void';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.os.PowerManager$WakeLock.release(int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.os.PowerManager$WakeLock->release was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.os.PowerManager$WakeLock->release cannot be determined.");
    }
}
    
@Hook("android.speech.SpeechRecognizer->cancel")
public static void redir_android_speech_SpeechRecognizer_cancel_250(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.speech.SpeechRecognizer';mthd: 'cancel';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.speech.SpeechRecognizer.cancel()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.speech.SpeechRecognizer->cancel was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.speech.SpeechRecognizer->cancel cannot be determined.");
    }
}
    
@Hook("android.speech.SpeechRecognizer->handleCancelMessage")
public static void redir_android_speech_SpeechRecognizer_handleCancelMessage_251(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.speech.SpeechRecognizer';mthd: 'handleCancelMessage';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.speech.SpeechRecognizer.handleCancelMessage()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.speech.SpeechRecognizer->handleCancelMessage was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.speech.SpeechRecognizer->handleCancelMessage cannot be determined.");
    }
}
    
@Hook("android.speech.SpeechRecognizer->handleStartListening")
public static void redir_android_speech_SpeechRecognizer_handleStartListening_252(Object _this , android.content.Intent p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.speech.SpeechRecognizer';mthd: 'handleStartListening';retCls: 'void';params: 'android.content.Intent' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.speech.SpeechRecognizer.handleStartListening(android.content.Intent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.speech.SpeechRecognizer->handleStartListening was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.speech.SpeechRecognizer->handleStartListening cannot be determined.");
    }
}
    
@Hook("android.speech.SpeechRecognizer->handleStopMessage")
public static void redir_android_speech_SpeechRecognizer_handleStopMessage_253(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.speech.SpeechRecognizer';mthd: 'handleStopMessage';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.speech.SpeechRecognizer.handleStopMessage()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.speech.SpeechRecognizer->handleStopMessage was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.speech.SpeechRecognizer->handleStopMessage cannot be determined.");
    }
}
    
@Hook("android.speech.SpeechRecognizer->startListening")
public static void redir_android_speech_SpeechRecognizer_startListening_254(Object _this , android.content.Intent p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.speech.SpeechRecognizer';mthd: 'startListening';retCls: 'void';params: 'android.content.Intent' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.speech.SpeechRecognizer.startListening(android.content.Intent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.speech.SpeechRecognizer->startListening was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.speech.SpeechRecognizer->startListening cannot be determined.");
    }
}
    
@Hook("android.speech.SpeechRecognizer->stopListening")
public static void redir_android_speech_SpeechRecognizer_stopListening_255(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.speech.SpeechRecognizer';mthd: 'stopListening';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.speech.SpeechRecognizer.stopListening()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.speech.SpeechRecognizer->stopListening was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.speech.SpeechRecognizer->stopListening cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getCellLocation")
public static android.telephony.CellLocation redir_android_telephony_TelephonyManager_getCellLocation_291(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getCellLocation';retCls: 'android.telephony.CellLocation';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getCellLocation()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (android.telephony.CellLocation) monitorHook.hookAfterApiCall(logSignature, (android.telephony.CellLocation) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getCellLocation was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getCellLocation cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getDeviceId")
public static java.lang.String redir_android_telephony_TelephonyManager_getDeviceId_292(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getDeviceId';retCls: 'java.lang.String';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getDeviceId()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getDeviceId was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getDeviceId cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getDeviceSoftwareVersion")
public static java.lang.String redir_android_telephony_TelephonyManager_getDeviceSoftwareVersion_293(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getDeviceSoftwareVersion';retCls: 'java.lang.String';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getDeviceSoftwareVersion()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getDeviceSoftwareVersion was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getDeviceSoftwareVersion cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getLine1Number")
public static java.lang.String redir_android_telephony_TelephonyManager_getLine1Number_294(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getLine1Number';retCls: 'java.lang.String';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getLine1Number()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getLine1Number was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getLine1Number cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getNeighboringCellInfo")
public static java.util.List redir_android_telephony_TelephonyManager_getNeighboringCellInfo_295(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getNeighboringCellInfo';retCls: 'java.util.List';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getNeighboringCellInfo()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.util.List) monitorHook.hookAfterApiCall(logSignature, (java.util.List) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getNeighboringCellInfo was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getNeighboringCellInfo cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getSimSerialNumber")
public static java.lang.String redir_android_telephony_TelephonyManager_getSimSerialNumber_296(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getSimSerialNumber';retCls: 'java.lang.String';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getSimSerialNumber()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getSimSerialNumber was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getSimSerialNumber cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getSubscriberId")
public static java.lang.String redir_android_telephony_TelephonyManager_getSubscriberId_297(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getSubscriberId';retCls: 'java.lang.String';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getSubscriberId()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getSubscriberId was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getSubscriberId cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getVoiceMailAlphaTag")
public static java.lang.String redir_android_telephony_TelephonyManager_getVoiceMailAlphaTag_298(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getVoiceMailAlphaTag';retCls: 'java.lang.String';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getVoiceMailAlphaTag()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getVoiceMailAlphaTag was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getVoiceMailAlphaTag cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->getVoiceMailNumber")
public static java.lang.String redir_android_telephony_TelephonyManager_getVoiceMailNumber_299(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'getVoiceMailNumber';retCls: 'java.lang.String';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.getVoiceMailNumber()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.lang.String) monitorHook.hookAfterApiCall(logSignature, (java.lang.String) returnVal);
    
        case Mock: 
            return "";
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->getVoiceMailNumber was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->getVoiceMailNumber cannot be determined.");
    }
}
    
@Hook("android.telephony.TelephonyManager->listen")
public static void redir_android_telephony_TelephonyManager_listen_300(Object _this , android.telephony.PhoneStateListener p0, int p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.TelephonyManager';mthd: 'listen';retCls: 'void';params: 'android.telephony.PhoneStateListener' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.TelephonyManager.listen(android.telephony.PhoneStateListener, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.TelephonyManager->listen was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.TelephonyManager->listen cannot be determined.");
    }
}
    
@Hook("android.webkit.WebView->loadDataWithBaseURL")
public static void redir_android_webkit_WebView_loadDataWithBaseURL_336(Object _this , java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3, java.lang.String p4)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.webkit.WebView';mthd: 'loadDataWithBaseURL';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'java.lang.String' '" +objectToString(p2)+ "' 'java.lang.String' '" +objectToString(p3)+ "' 'java.lang.String' '" +objectToString(p4)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.webkit.WebView.loadDataWithBaseURL(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.webkit.WebView->loadDataWithBaseURL was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.webkit.WebView->loadDataWithBaseURL cannot be determined.");
    }
}
    
@Hook("android.webkit.WebView->loadUrl")
public static void redir_android_webkit_WebView_loadUrl_337(Object _this , java.lang.String p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.webkit.WebView';mthd: 'loadUrl';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.webkit.WebView.loadUrl(java.lang.String)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.webkit.WebView->loadUrl was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.webkit.WebView->loadUrl cannot be determined.");
    }
}
    
@Hook("android.webkit.WebView->loadUrl")
public static void redir_android_webkit_WebView_loadUrl_338(Object _this , java.lang.String p0, java.util.Map p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.webkit.WebView';mthd: 'loadUrl';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'java.util.Map' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.webkit.WebView.loadUrl(java.lang.String, java.util.Map)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.webkit.WebView->loadUrl was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.webkit.WebView->loadUrl cannot be determined.");
    }
}
    
@Hook("android.telephony.SmsManager->sendTextMessage")
public static void redir_android_telephony_SmsManager_sendTextMessage_343(Object _this , java.lang.String p0, java.lang.String p1, java.lang.String p2, android.app.PendingIntent p3, android.app.PendingIntent p4)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.telephony.SmsManager';mthd: 'sendTextMessage';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'java.lang.String' '" +objectToString(p2)+ "' 'android.app.PendingIntent' '" +objectToString(p3)+ "' 'android.app.PendingIntent' '" +objectToString(p4)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("android.telephony.SmsManager.sendTextMessage(java.lang.String, java.lang.String, java.lang.String, android.app.PendingIntent, android.app.PendingIntent)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.telephony.SmsManager->sendTextMessage was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.telephony.SmsManager->sendTextMessage cannot be determined.");
    }
}
    
@Hook("java.net.Socket-><init>")
public static void redir_java_net_Socket__ctor_384(Object _this , java.net.Proxy p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.Socket';mthd: '<init>';retCls: 'void';params: 'java.net.Proxy' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.Socket.<init>(java.net.Proxy)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.Socket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.Socket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.Socket-><init>")
public static void redir_java_net_Socket__ctor_390(Object _this , java.lang.String p0, int p1, java.net.InetAddress p2, int p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.Socket';mthd: '<init>';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "' 'java.net.InetAddress' '" +objectToString(p2)+ "' 'int' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.Socket.<init>(java.lang.String, int, java.net.InetAddress, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.Socket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.Socket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.Socket-><init>")
public static void redir_java_net_Socket__ctor_393(Object _this , java.lang.String p0, int p1, boolean p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.Socket';mthd: '<init>';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "' 'boolean' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.Socket.<init>(java.lang.String, int, boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.Socket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.Socket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.Socket-><init>")
public static void redir_java_net_Socket__ctor_396(Object _this , java.net.InetAddress p0, int p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.Socket';mthd: '<init>';retCls: 'void';params: 'java.net.InetAddress' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.Socket.<init>(java.net.InetAddress, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.Socket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.Socket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.Socket-><init>")
public static void redir_java_net_Socket__ctor_399(Object _this , java.net.InetAddress p0, int p1, java.net.InetAddress p2, int p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.Socket';mthd: '<init>';retCls: 'void';params: 'java.net.InetAddress' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "' 'java.net.InetAddress' '" +objectToString(p2)+ "' 'int' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.Socket.<init>(java.net.InetAddress, int, java.net.InetAddress, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.Socket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.Socket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.Socket-><init>")
public static void redir_java_net_Socket__ctor_402(Object _this , java.net.InetAddress p0, int p1, boolean p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.Socket';mthd: '<init>';retCls: 'void';params: 'java.net.InetAddress' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "' 'boolean' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.Socket.<init>(java.net.InetAddress, int, boolean)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.Socket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.Socket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.Socket->connect")
public static void redir_java_net_Socket_connect_412(Object _this , java.net.SocketAddress p0, int p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.Socket';mthd: 'connect';retCls: 'void';params: 'java.net.SocketAddress' '" +objectToString(p0)+ "' 'int' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.Socket.connect(java.net.SocketAddress, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.Socket->connect was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.Socket->connect cannot be determined.");
    }
}
    
@Hook("java.net.DatagramSocket-><init>")
public static void redir_java_net_DatagramSocket__ctor_417(Object _this , int p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.DatagramSocket';mthd: '<init>';retCls: 'void';params: 'int' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.DatagramSocket.<init>(int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.DatagramSocket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.DatagramSocket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.DatagramSocket-><init>")
public static void redir_java_net_DatagramSocket__ctor_420(Object _this , int p0, java.net.InetAddress p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.DatagramSocket';mthd: '<init>';retCls: 'void';params: 'int' '" +objectToString(p0)+ "' 'java.net.InetAddress' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.DatagramSocket.<init>(int, java.net.InetAddress)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.DatagramSocket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.DatagramSocket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.DatagramSocket-><init>")
public static void redir_java_net_DatagramSocket__ctor_422(Object _this , java.net.SocketAddress p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.DatagramSocket';mthd: '<init>';retCls: 'void';params: 'java.net.SocketAddress' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.DatagramSocket.<init>(java.net.SocketAddress)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.DatagramSocket-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.DatagramSocket-><init> cannot be determined.");
    }
}
    
@Hook("java.net.DatagramSocket->connect")
public static void redir_java_net_DatagramSocket_connect_428(Object _this , java.net.SocketAddress p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.DatagramSocket';mthd: 'connect';retCls: 'void';params: 'java.net.SocketAddress' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.DatagramSocket.connect(java.net.SocketAddress)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.DatagramSocket->connect was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.DatagramSocket->connect cannot be determined.");
    }
}
    
@Hook("java.net.MulticastSocket->joinGroup")
public static void redir_java_net_MulticastSocket_joinGroup_440(Object _this , java.net.InetAddress p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.MulticastSocket';mthd: 'joinGroup';retCls: 'void';params: 'java.net.InetAddress' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.MulticastSocket.joinGroup(java.net.InetAddress)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.MulticastSocket->joinGroup was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.MulticastSocket->joinGroup cannot be determined.");
    }
}
    
@Hook("java.net.MulticastSocket->joinGroup")
public static void redir_java_net_MulticastSocket_joinGroup_441(Object _this , java.net.SocketAddress p0, java.net.NetworkInterface p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.MulticastSocket';mthd: 'joinGroup';retCls: 'void';params: 'java.net.SocketAddress' '" +objectToString(p0)+ "' 'java.net.NetworkInterface' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.MulticastSocket.joinGroup(java.net.SocketAddress, java.net.NetworkInterface)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.MulticastSocket->joinGroup was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.MulticastSocket->joinGroup cannot be determined.");
    }
}
    
@Hook("java.net.URL-><init>")
public static void redir_java_net_URL__ctor_454(Object _this , java.net.URL p0, java.lang.String p1, java.net.URLStreamHandler p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.URL';mthd: '<init>';retCls: 'void';params: 'java.net.URL' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'java.net.URLStreamHandler' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.URL.<init>(java.net.URL, java.lang.String, java.net.URLStreamHandler)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.URL-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.URL-><init> cannot be determined.");
    }
}
    
@Hook("java.net.URL-><init>")
public static void redir_java_net_URL__ctor_462(Object _this , java.lang.String p0, java.lang.String p1, int p2, java.lang.String p3, java.net.URLStreamHandler p4)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.URL';mthd: '<init>';retCls: 'void';params: 'java.lang.String' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'int' '" +objectToString(p2)+ "' 'java.lang.String' '" +objectToString(p3)+ "' 'java.net.URLStreamHandler' '" +objectToString(p4)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.URL.<init>(java.lang.String, java.lang.String, int, java.lang.String, java.net.URLStreamHandler)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.URL-><init> was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.URL-><init> cannot be determined.");
    }
}
    
@Hook("java.net.URL->openConnection")
public static java.net.URLConnection redir_java_net_URL_openConnection_467(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.URL';mthd: 'openConnection';retCls: 'java.net.URLConnection';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.URL.openConnection()", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this);
        return (java.net.URLConnection) monitorHook.hookAfterApiCall(logSignature, (java.net.URLConnection) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.URL->openConnection was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.URL->openConnection cannot be determined.");
    }
}
    
@Hook("java.net.URL->openConnection")
public static java.net.URLConnection redir_java_net_URL_openConnection_468(Object _this , java.net.Proxy p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.URL';mthd: 'openConnection';retCls: 'java.net.URLConnection';params: 'java.net.Proxy' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.URL.openConnection(java.net.Proxy)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (java.net.URLConnection) monitorHook.hookAfterApiCall(logSignature, (java.net.URLConnection) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.URL->openConnection was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.URL->openConnection cannot be determined.");
    }
}
    
@Hook("java.net.URLConnection->connect")
public static void redir_java_net_URLConnection_connect_481(Object _this )
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'java.net.URLConnection';mthd: 'connect';retCls: 'void';params: ;stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("java.net.URLConnection.connect()", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API java.net.URLConnection->connect was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api java.net.URLConnection->connect cannot be determined.");
    }
}
    
@Hook("org.apache.http.impl.client.AbstractHttpClient->execute")
public static org.apache.http.HttpResponse redir_org_apache_http_impl_client_AbstractHttpClient_execute_521(Object _this , org.apache.http.HttpHost p0, org.apache.http.HttpRequest p1, org.apache.http.protocol.HttpContext p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'org.apache.http.impl.client.AbstractHttpClient';mthd: 'execute';retCls: 'org.apache.http.HttpResponse';params: 'org.apache.http.HttpHost' '" +objectToString(p0)+ "' 'org.apache.http.HttpRequest' '" +objectToString(p1)+ "' 'org.apache.http.protocol.HttpContext' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    ApiPolicy policy = getPolicy("org.apache.http.impl.client.AbstractHttpClient.execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.protocol.HttpContext)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
        return (org.apache.http.HttpResponse) monitorHook.hookAfterApiCall(logSignature, (org.apache.http.HttpResponse) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API org.apache.http.impl.client.AbstractHttpClient->execute was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api org.apache.http.impl.client.AbstractHttpClient->execute cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->bulkInsert")
public static int redir_android_content_ContentResolver_bulkInsert_548(Object _this , android.net.Uri p0, android.content.ContentValues[] p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'bulkInsert';retCls: 'int';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'android.content.ContentValues[]' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.bulkInsert(android.net.Uri, android.content.ContentValues[])", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->bulkInsert was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->bulkInsert cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->delete")
public static int redir_android_content_ContentResolver_delete_549(Object _this , android.net.Uri p0, java.lang.String p1, java.lang.String[] p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'delete';retCls: 'int';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'java.lang.String[]' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.delete(android.net.Uri, java.lang.String, java.lang.String[])", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->delete was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->delete cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->insert")
public static android.net.Uri redir_android_content_ContentResolver_insert_550(Object _this , android.net.Uri p0, android.content.ContentValues p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'insert';retCls: 'android.net.Uri';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'android.content.ContentValues' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.insert(android.net.Uri, android.content.ContentValues)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (android.net.Uri) monitorHook.hookAfterApiCall(logSignature, (android.net.Uri) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->insert was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->insert cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->update")
public static int redir_android_content_ContentResolver_update_551(Object _this , android.net.Uri p0, android.content.ContentValues p1, java.lang.String p2, java.lang.String[] p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'update';retCls: 'int';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'android.content.ContentValues' '" +objectToString(p1)+ "' 'java.lang.String' '" +objectToString(p2)+ "' 'java.lang.String[]' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->update was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->update cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->openInputStream")
public static java.io.InputStream redir_android_content_ContentResolver_openInputStream_552(Object _this , android.net.Uri p0)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'openInputStream';retCls: 'java.io.InputStream';params: 'android.net.Uri' '" +objectToString(p0)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.openInputStream(android.net.Uri)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0);
        return (java.io.InputStream) monitorHook.hookAfterApiCall(logSignature, (java.io.InputStream) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->openInputStream was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->openInputStream cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->openAssetFileDescriptor")
public static android.content.res.AssetFileDescriptor redir_android_content_ContentResolver_openAssetFileDescriptor_560(Object _this , android.net.Uri p0, java.lang.String p1, android.os.CancellationSignal p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'openAssetFileDescriptor';retCls: 'android.content.res.AssetFileDescriptor';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'android.os.CancellationSignal' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.openAssetFileDescriptor(android.net.Uri, java.lang.String, android.os.CancellationSignal)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
        return (android.content.res.AssetFileDescriptor) monitorHook.hookAfterApiCall(logSignature, (android.content.res.AssetFileDescriptor) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->openAssetFileDescriptor was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->openAssetFileDescriptor cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->query")
public static android.database.Cursor redir_android_content_ContentResolver_query_563(Object _this , android.net.Uri p0, java.lang.String[] p1, java.lang.String p2, java.lang.String[] p3, java.lang.String p4, android.os.CancellationSignal p5)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'query';retCls: 'android.database.Cursor';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String[]' '" +objectToString(p1)+ "' 'java.lang.String' '" +objectToString(p2)+ "' 'java.lang.String[]' '" +objectToString(p3)+ "' 'java.lang.String' '" +objectToString(p4)+ "' 'android.os.CancellationSignal' '" +objectToString(p5)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String, android.os.CancellationSignal)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4, p5);
        return (android.database.Cursor) monitorHook.hookAfterApiCall(logSignature, (android.database.Cursor) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->query was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->query cannot be determined.");
    }
}
    
@Hook("android.content.ContentResolver->registerContentObserver")
public static void redir_android_content_ContentResolver_registerContentObserver_566(Object _this , android.net.Uri p0, boolean p1, android.database.ContentObserver p2, int p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentResolver';mthd: 'registerContentObserver';retCls: 'void';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'boolean' '" +objectToString(p1)+ "' 'android.database.ContentObserver' '" +objectToString(p2)+ "' 'int' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentResolver.registerContentObserver(android.net.Uri, boolean, android.database.ContentObserver, int)", uriList);
    switch (policy){ 
        case Allow: 
            
         OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
          monitorHook.hookAfterApiCall(logSignature,  null);
    
        case Mock: 
            return ;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentResolver->registerContentObserver was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentResolver->registerContentObserver cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->bulkInsert")
public static int redir_android_content_ContentProviderClient_bulkInsert_571(Object _this , android.net.Uri p0, android.content.ContentValues[] p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'bulkInsert';retCls: 'int';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'android.content.ContentValues[]' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.bulkInsert(android.net.Uri, android.content.ContentValues[])", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->bulkInsert was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->bulkInsert cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->delete")
public static int redir_android_content_ContentProviderClient_delete_572(Object _this , android.net.Uri p0, java.lang.String p1, java.lang.String[] p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'delete';retCls: 'int';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'java.lang.String[]' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.delete(android.net.Uri, java.lang.String, java.lang.String[])", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->delete was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->delete cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->insert")
public static android.net.Uri redir_android_content_ContentProviderClient_insert_573(Object _this , android.net.Uri p0, android.content.ContentValues p1)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'insert';retCls: 'android.net.Uri';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'android.content.ContentValues' '" +objectToString(p1)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.insert(android.net.Uri, android.content.ContentValues)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1);
        return (android.net.Uri) monitorHook.hookAfterApiCall(logSignature, (android.net.Uri) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->insert was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->insert cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->update")
public static int redir_android_content_ContentProviderClient_update_574(Object _this , android.net.Uri p0, android.content.ContentValues p1, java.lang.String p2, java.lang.String[] p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'update';retCls: 'int';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'android.content.ContentValues' '" +objectToString(p1)+ "' 'java.lang.String' '" +objectToString(p2)+ "' 'java.lang.String[]' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
        return (Integer) monitorHook.hookAfterApiCall(logSignature, (Integer) returnVal);
    
        case Mock: 
            return 0;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->update was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->update cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->openFile")
public static android.os.ParcelFileDescriptor redir_android_content_ContentProviderClient_openFile_578(Object _this , android.net.Uri p0, java.lang.String p1, android.os.CancellationSignal p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'openFile';retCls: 'android.os.ParcelFileDescriptor';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'android.os.CancellationSignal' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.openFile(android.net.Uri, java.lang.String, android.os.CancellationSignal)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
        return (android.os.ParcelFileDescriptor) monitorHook.hookAfterApiCall(logSignature, (android.os.ParcelFileDescriptor) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->openFile was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->openFile cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->openAssetFile")
public static android.content.res.AssetFileDescriptor redir_android_content_ContentProviderClient_openAssetFile_580(Object _this , android.net.Uri p0, java.lang.String p1, android.os.CancellationSignal p2)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'openAssetFile';retCls: 'android.content.res.AssetFileDescriptor';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'android.os.CancellationSignal' '" +objectToString(p2)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.openAssetFile(android.net.Uri, java.lang.String, android.os.CancellationSignal)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2);
        return (android.content.res.AssetFileDescriptor) monitorHook.hookAfterApiCall(logSignature, (android.content.res.AssetFileDescriptor) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->openAssetFile was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->openAssetFile cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->openTypedAssetFileDescriptor")
public static android.content.res.AssetFileDescriptor redir_android_content_ContentProviderClient_openTypedAssetFileDescriptor_581(Object _this , android.net.Uri p0, java.lang.String p1, android.os.Bundle p2, android.os.CancellationSignal p3)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'openTypedAssetFileDescriptor';retCls: 'android.content.res.AssetFileDescriptor';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String' '" +objectToString(p1)+ "' 'android.os.Bundle' '" +objectToString(p2)+ "' 'android.os.CancellationSignal' '" +objectToString(p3)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.openTypedAssetFileDescriptor(android.net.Uri, java.lang.String, android.os.Bundle, android.os.CancellationSignal)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3);
        return (android.content.res.AssetFileDescriptor) monitorHook.hookAfterApiCall(logSignature, (android.content.res.AssetFileDescriptor) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->openTypedAssetFileDescriptor was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->openTypedAssetFileDescriptor cannot be determined.");
    }
}
    
@Hook("android.content.ContentProviderClient->query")
public static android.database.Cursor redir_android_content_ContentProviderClient_query_585(Object _this , android.net.Uri p0, java.lang.String[] p1, java.lang.String p2, java.lang.String[] p3, java.lang.String p4, android.os.CancellationSignal p5)
{
    String stackTrace = getStackTrace();
    long threadId = getThreadId();
    String logSignature =  "TId: "+threadId+";objCls: 'android.content.ContentProviderClient';mthd: 'query';retCls: 'android.database.Cursor';params: 'android.net.Uri' '" +objectToString(p0)+ "' 'java.lang.String[]' '" +objectToString(p1)+ "' 'java.lang.String' '" +objectToString(p2)+ "' 'java.lang.String[]' '" +objectToString(p3)+ "' 'java.lang.String' '" +objectToString(p4)+ "' 'android.os.CancellationSignal' '" +objectToString(p5)+ "';stacktrace: '"+stackTrace+"\'"  ;
    monitorHook.hookBeforeApiCall(logSignature);
    Log.i("Monitor_API_method_call", logSignature);
    addCurrentLogs(logSignature);
    List<Uri> uriList = new ArrayList<>();
    uriList.add(p0);
    ApiPolicy policy = getPolicy("android.content.ContentProviderClient.query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String, android.os.CancellationSignal)", uriList);
    switch (policy){ 
        case Allow: 
            
        Object returnVal =  OriginalMethod.by(new $() {}).invoke (_this, p0, p1, p2, p3, p4, p5);
        return (android.database.Cursor) monitorHook.hookAfterApiCall(logSignature, (android.database.Cursor) returnVal);
    
        case Mock: 
            return null;
        case Deny: 
            SecurityException e = new SecurityException("API android.content.ContentProviderClient->query was blocked by DroidMate");
            Log.e("Monitor_API_method_call", e.getMessage());
            throw e;
        default:
            throw new RuntimeException("Policy for api android.content.ContentProviderClient->query cannot be determined.");
    }
}
    


	//endregion
}

