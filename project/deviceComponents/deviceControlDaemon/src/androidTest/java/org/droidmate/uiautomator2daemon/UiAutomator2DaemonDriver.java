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
package org.droidmate.uiautomator2daemon;

import android.annotation.TargetApi;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;

import android.os.RemoteException;
import android.view.accessibility.AccessibilityWindowInfo;

import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.*;
import android.util.Log;
import org.apache.commons.io.FileUtils;
import org.droidmate.uiautomator_daemon.DeviceCommand;
import org.droidmate.uiautomator_daemon.DeviceResponse;
import org.droidmate.uiautomator_daemon.UiAutomatorDaemonException;
import org.droidmate.uiautomator_daemon.GuiStatusResponse;

import java.io.File;
import java.io.IOException;

import static org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.*;

// WISH there is code duplication between uiad-1 and uiad-2. When DM no longer needs to work with Android 4, remove uiad-1. 
class UiAutomator2DaemonDriver implements IUiAutomator2DaemonDriver {
	// Has to be at least 5 to wait through main activity loading screen of de.mcdonalds.mcdonaldsinfoapp_v1.4.0.1-inlined.apk
	private static final int waitForGuiToStabilizeMaxIterations = 5;
	private final UiDevice device;
	/**
	 * Decides if UiAutomator2DaemonDriver should wait for the window to go to idle state after each click.
	 */
	private final boolean waitForGuiToStabilize;
	private final int waitForWindowUpdateTimeout;
	private final Context context;

	UiAutomator2DaemonDriver(boolean waitForGuiToStabilize, int waitForWindowUpdateTimeout) {
		Log.d(uiaDaemon_logcatTag, "XXX");
		// Disabling waiting for selector implicit timeout
		Configurator.getInstance().setWaitForSelectorTimeout(0L);


		// The instrumentation required to run uiautomator2-daemon is
		// provided by the command: adb shell instrument <PACKAGE>/<RUNNER>
		Instrumentation instr = InstrumentationRegistry.getInstrumentation();
		if (instr == null) throw new AssertionError();

		this.context = InstrumentationRegistry.getTargetContext();
		if (context == null) throw new AssertionError();

		this.device = UiDevice.getInstance(instr);
		if (device == null) throw new AssertionError();
		try {
			device.setOrientationNatural();
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		this.waitForGuiToStabilize = waitForGuiToStabilize;
		this.waitForWindowUpdateTimeout = waitForWindowUpdateTimeout;
		Log.d(uiaDaemon_logcatTag, "YYY");
	}


	@Override
	public DeviceResponse executeCommand(DeviceCommand deviceCommand) throws UiAutomatorDaemonException { //TODO can use UiAutomator to create screenshot bitmap instead of adb
		Log.v(uiaDaemon_logcatTag, "Executing device command: " + deviceCommand.command);

		DeviceResponse response = new DeviceResponse();

		try {

			switch (deviceCommand.command) {
				case DEVICE_COMMAND_STOP_UIADAEMON:
					// The server will be closed after this response is sent, because the given deviceCommand.command will be interpreted
					// in the caller, i.e. Uiautomator2DaemonTcpServerBase.
					break;
				case DEVICE_COMMAND_GET_UIAUTOMATOR_WINDOW_HIERARCHY_DUMP:
					response = getGuiStatus();
					break;
				case DEVICE_COMMAND_PERFORM_ACTION:
					response = performAction(deviceCommand);
					break;
				case DEVICE_COMMAND_GET_IS_ORIENTATION_LANDSCAPE:
					response = getIsNaturalOrientation();
					break;
				default:
					throw new UiAutomatorDaemonException(String.format("The command %s is not implemented yet!", deviceCommand.command));
			}

		} catch (Throwable e) {
			Log.e(uiaDaemon_logcatTag, "Error: " + e.getMessage());
			Log.e(uiaDaemon_logcatTag, "Printing stack trace for debug");
			e.printStackTrace();

			response.throwable = e;
		}

		return response;
	}

	private String getDeviceModel() {
		Log.d(uiaDaemon_logcatTag, "getDeviceModel()");
		String model = Build.MODEL;
		String manufacturer = Build.MANUFACTURER;
		String fullModelName = manufacturer + "-" + model;
		Log.d(uiaDaemon_logcatTag, "Device model: " + fullModelName);
		return fullModelName;
	}

	private DeviceResponse getIsNaturalOrientation() {
		Log.d(uiaDaemon_logcatTag, "Getting 'isNaturalOrientation'");
		this.device.waitForIdle();
		DeviceResponse deviceResponse = new DeviceResponse();
		deviceResponse.isNaturalOrientation = device.isNaturalOrientation();
		return deviceResponse;
	}


	@TargetApi(Build.VERSION_CODES.FROYO)
	private DeviceResponse performAction(DeviceCommand deviceCommand) throws UiAutomatorDaemonException {
		Log.v(uiaDaemon_logcatTag, "Performing GUI action");

		DeviceAction action = DeviceAction.Companion.fromAction(deviceCommand.guiAction);

		if (action != null)
			action.execute(device, context);

		return new DeviceResponse();
	}

  /*boolean isKeyboardOpened() {
    for (AccessibilityWindowInfo window : InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows()) {
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        return true;
      }
    }
    return false;
  }*/


	// WISH maybe waitForIdle can be set by http://developer.android.com/tools/help/uiautomator/Configurator.html#setWaitForIdleTimeout%28long%29

	/**
	 * <p>
	 * Waits until GUI gets into a state in which it is reasonable to expect it won't change, so DroidMate's exploration can
	 * proceed, by analyzing the GUI etc.
	 *
	 * </p><p>
	 * The method first waits for idle [wfi], then repeatedly both waits for window update [wfwu] and waits for idle, until the
	 * window update times out and waits for idle returns immediately.
	 *
	 * </p><p>
	 * The first call to 'wait for idle' is made to catch any ongoing GUI changes. A call to 'wait for window update' is made
	 * to wait for the GUI to react to any click [clck] that was potentially made just before this method was called.
	 * The next call to 'wait for idle' is made to wait for the GUI to receive any pending ongoing events coming after
	 * the window update. The process is then looped starting from 'wait for window update' to double-ensure the method didn't
	 * considered the GUI stable while there were some events incoming.
	 * <p>
	 * <br/>
	 * -----<br/>
	 * </p><p>
	 * 'Wait for idle' will return as soon as at least 500 ms have passed since the GUI received last accessibility event,
	 * because 'wait for idle' [wfi] will call [wfi2] which will call [wfi3] with 500ms [qti].
	 *
	 * </p><p>
	 * If the GUI was already idle at the call to 'wait for idle', it will return immediately or after 1 or 2 ms, which is caused
	 * by clock imprecision.
	 * <p>
	 * <br/>
	 * -----<br/>
	 * </p>
	 */
	private void waitForGuiToStabilize() {
		if (waitForGuiToStabilize) {
			Log.v(uiaDaemon_logcatTag, "Waiting for GUI to stabilize.");

      /* If we would like to extends wait for idle time to more than 500 ms, here are possible ways to do it:

         - http://developer.android.com/tools/help/uiautomator/Configurator.html

         - Use android.app.UiAutomation.waitForIdle but getting instance of UiAutomation requires instrumenting
         the app under exploration:
         https://developer.android.com/about/versions/android-4.3.html#Testing

         Use reflection to get com.android.uiautomator.core.UiDevice#getAutomatorBridge and call the internal waitForIdle.
         - http://stackoverflow.com/questions/880365/any-way-to-invoke-a-private-method

       */

			long initialWaitForIdleStartTime = System.currentTimeMillis();
			this.device.waitForIdle();
			long initialWaitForIdleWaitTime = System.currentTimeMillis() - initialWaitForIdleStartTime;
			Log.v(uiaDaemon_logcatTag, "waitForGuiToStabilize: initial waitForIdle took " + initialWaitForIdleWaitTime + "ms");

			boolean wfwuReachedTimeout;
			boolean wfiReturnedImmediately;
			int iteration = 0;
			do {
				iteration++;
				wfwuReachedTimeout = waitForWindowUpdate(iteration);
				wfiReturnedImmediately = waitForIdle(iteration);
			} while
							(!guiStabilized(wfwuReachedTimeout, wfiReturnedImmediately)
							&& !guiStabilizationAttemptsExhausted(iteration, waitForGuiToStabilizeMaxIterations));

			if (guiStabilizationAttemptsExhausted(iteration, waitForGuiToStabilizeMaxIterations))
				Log.w(uiaDaemon_logcatTag, "GUI failed to stabilize. Continuing nonetheless.");
			else
				Log.d(uiaDaemon_logcatTag, "GUI stabilized after " + iteration + " iterations / " + (System.currentTimeMillis() - initialWaitForIdleStartTime) + "ms");

		} else {
			Log.v(uiaDaemon_logcatTag, "Skipped waiting for GUI to stabilize.");
		}

	}

	private boolean guiStabilizationAttemptsExhausted(int waitForStabilizationLoopIteration, int maxWaitForStabilizationLoopIterations) {
		return waitForStabilizationLoopIteration >= maxWaitForStabilizationLoopIterations;
	}

	private boolean guiStabilized(boolean waitForWindowUpdateReachedTimeout, boolean waitForIdleReturnedImmediately) {
		return waitForWindowUpdateReachedTimeout && waitForIdleReturnedImmediately;
	}

	private boolean waitForWindowUpdate(int i) {
		boolean waitForWindowUpdateReachedTimeout;
		long waitForWindowUpdateStartTime = System.currentTimeMillis();
		this.device.waitForWindowUpdate(null, waitForWindowUpdateTimeout);
		long waitForWindowUpdateWaitTime = System.currentTimeMillis() - waitForWindowUpdateStartTime;
		Log.v(uiaDaemon_logcatTag, "waitForGuiToStabilize: iteration " + i + " waitForWindowUpdate took " + waitForWindowUpdateWaitTime + "ms");

		waitForWindowUpdateReachedTimeout = waitForWindowUpdateWaitTime >= waitForWindowUpdateTimeout;
		return waitForWindowUpdateReachedTimeout;
	}

	private boolean waitForIdle(int i) {
		boolean waitForIdleReturnedImmediately;
		long waitForIdleStartTime = System.currentTimeMillis();
		this.device.waitForIdle();
		long waitForIdleWaitTime = System.currentTimeMillis() - waitForIdleStartTime;
		Log.v(uiaDaemon_logcatTag, "waitForGuiToStabilize: iteration " + i + " waitForIdle took " + waitForIdleWaitTime + "ms");

		waitForIdleReturnedImmediately = waitForIdleWaitTime <= 2;
		return waitForIdleReturnedImmediately;
	}

	/*
		Possible programmatic alternatives to getting GUI dump from XML:

		- inherit from http://developer.android.com/reference/android/accessibilityservice/AccessibilityService.html
		- modify appguard loader to insert our custom code to the apk under exploration and obtain the window hierarchy from
		Window Manager Service (or something like that, legacy aut-addon instrumentation buried in the repo has p.o.co code for that)
	 */
	private GuiStatusResponse getGuiStatus() throws UiAutomatorDaemonException {
		Log.d(uiaDaemon_logcatTag, "Getting window hierarchy dump");

		String windowDumpFileName = "window_hierarchy_dump.xml";
		File windowDumpFile = prepareWindowDumpFile(windowDumpFileName);

		dumpWindowHierarchyProtectingAgainstException(windowDumpFile);

		String windowHierarchyDump;
		try {
			windowHierarchyDump = FileUtils.readFileToString(windowDumpFile, "UTF-8");
		} catch (IOException e) {
			throw new UiAutomatorDaemonException(e);
		}

		int width = this.device.getDisplayWidth();
		int height = this.device.getDisplayHeight();
		String model = this.getDeviceModel();
    /* We don't make calls to:
     ui.getUiDevice().getCurrentActivityName();
     ui.getUiDevice().getCurrentPackageName();
     due to the performance reasons.
     Instead, package name is taken from the window hierarchy XML dump and activity is ignored as of now. Later on
     the activity will be taken programmatically from AutAddon.
     See: http://stackoverflow.com/questions/3873659/android-how-can-i-get-the-current-foreground-activity-from-a-service
     */

		Log.d(uiaDaemon_logcatTag, "Creating response");

		GuiStatusResponse response = GuiStatusResponse.fromUIDump(windowHierarchyDump, model, width, height);

		Log.d(uiaDaemon_logcatTag, "Sending response");

		return response;
	}

	/**
	 * <p>
	 * There is a bug in com.android.uiautomator.core.UiDevice#dumpWindowHierarchy(java.lang.String)
	 * that sometimes manifest itself with an Exception. This method  protects against it, making a couple of
	 * attempts at getting the dump and if all of them fail, throwing an
	 * UiAutomatorDaemonException.
	 *
	 * </p><p>
	 * Example stack trace of possible NPE:<br/>
	 * <code>
	 * java.lang.NullPointerException: null<br/>
	 * at com.android.uiautomator.core.AccessibilityNodeInfoDumper.childNafCheck(AccessibilityNodeInfoDumper.java:200)<br/>
	 * at com.android.uiautomator.core.AccessibilityNodeInfoDumper.nafCheck(AccessibilityNodeInfoDumper.java:180)<br/>
	 * at com.android.uiautomator.core.AccessibilityNodeInfoDumper.dumpNodeRec(AccessibilityNodeInfoDumper.java:104)<br/>
	 * at com.android.uiautomator.core.AccessibilityNodeInfoDumper.dumpNodeRec(AccessibilityNodeInfoDumper.java:129)<br/>
	 * (...)<br/>
	 * at com.android.uiautomator.core.AccessibilityNodeInfoDumper.dumpWindowToFile(AccessibilityNodeInfoDumper.java:89)<br/>
	 * at com.android.uiautomator.core.UiDevice.dumpWindowHierarchy(UiDevice.java:<obsolete line number here>)
	 * </code>
	 * </p><p>
	 * Example stack trace of possible IllegalArgumentException: of an exception that occurred on Snapchat 5.0.27.3 (July 3, 2014):
	 *
	 * </p><p>
	 *
	 * <code>
	 * java.lang.IllegalArgumentException: Illegal character (d83d)<br/>
	 * at org.kxml2.io.KXmlSerializer.reportInvalidCharacter(KXmlSerializer.java:144) ~[na:na]<br/>
	 * at org.kxml2.io.KXmlSerializer.writeEscaped(KXmlSerializer.java:130) ~[na:na]<br/>
	 * at org.kxml2.io.KXmlSerializer.attribute(KXmlSerializer.java:465) ~[na:na]<br/>
	 * at com.android.uiautomator.core.AccessibilityNodeInfoDumper.dumpNodeRec(AccessibilityNodeInfoDumper.java:111) ~[na:na]<br/>
	 * (...)<br/>
	 * at com.android.uiautomator.core.AccessibilityNodeInfoDumper.dumpWindowToFile(AccessibilityNodeInfoDumper.java:89) ~[na:na]<br/>
	 * at com.android.uiautomator.core.UiDevice.dumpWindowHierarchy(UiDevice.java:768) ~[na:na]<br/>
	 * at org.droidmate.uiautomatordaemon.UiAutomator2DaemonDriver.tryDumpWindowHierarchy(UiAutomator2DaemonDriver.java:420) ~[na:na]<br/>
	 * (...)<br/>
	 * </code>
	 * </p><p>
	 * <p>
	 * Exploration log snippet of the IllegalArgumentException:
	 *
	 * </p><p>
	 * <code>
	 * 2015-02-20 19:44:21.113 DEBUG o.d.e.VerifiableDeviceActionsExecutor    - Performing verifiable device action: <click on LC? 0 Wdgt:View/""/""/[570,233], no expectations><br/>
	 * 2015-02-20 19:44:23.958 DEBUG o.d.exploration.ApiLogcatLogsReader      - Current API logs read count: 0<br/>
	 * 2015-02-20 19:44:24.040 ERROR o.d.e.ExplorationOutputCollector         - Abrupt exploration end. Caught exception thrown during exploration of com.snapchat.android. Exception message: Device returned DeviceResponse with non-null throwable, indicating something exploded on the A(V)D. The exception is given as a cause of this one. If it doesn't have enough information, try inspecting the logcat output of the A(V)D.
	 * </code>
	 * </p><p>
	 * Discussion: https://groups.google.com/forum/#!topic/appium-discuss/pkDcLx0LyWQ
	 *
	 * </p><p>
	 * Issue tracker: https://code.google.com/p/android/issues/detail?id=68419
	 *
	 * </p>
	 */
	private void dumpWindowHierarchyProtectingAgainstException(File windowDumpFile) throws UiAutomatorDaemonException {
		int dumpAttempts = 5;
		int dumpAttemptsLeft = dumpAttempts;
		boolean dumpSucceeded;
		do {
			dumpSucceeded = tryDumpWindowHierarchy(windowDumpFile);
			dumpAttemptsLeft--;

			if (!dumpSucceeded) {
				if (dumpAttemptsLeft == 1) {
					Log.w(uiaDaemon_logcatTag, "UiDevice.dumpWindowHierarchy() failed. Attempts left: 1. Pressing home screen button.");
					// Countermeasure for "Illegal character (d83d)". See the doc of this method and
					// https://hg.st.cs.uni-saarland.de/issues/981
					this.device.pressHome();
				} else {
					Log.w(uiaDaemon_logcatTag, "UiDevice.dumpWindowHierarchy() failed. Attempts left: " + dumpAttemptsLeft);
				}
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					Log.e(uiaDaemon_logcatTag, "Sleeping between tryDumpWindowHierarchy attempts was interrupted!");
				}
			}

		} while (!dumpSucceeded && dumpAttemptsLeft > 0);

		if (dumpAttemptsLeft <= 0) {
			Log.w(uiaDaemon_logcatTag, "UiDevice.dumpWindowHierarchy() failed. No attempts left. Throwing UiAutomatorDaemonException.");
			throw new UiAutomatorDaemonException(String.format("All %d tryDumpWindowHierarchy(%s) attempts exhausted.", dumpAttempts, windowDumpFile));
		}
	}

	/**
	 * @see #dumpWindowHierarchyProtectingAgainstException
	 */
	// Note on read/write permissions:
	//   http://stackoverflow.com/questions/23527767/open-failed-eacces-permission-denied
	private boolean tryDumpWindowHierarchy(File windowDumpFile) {
		try {
			Log.v(uiaDaemon_logcatTag, String.format("Trying to create dump file '%s'", windowDumpFile.toString()));
			Log.v(uiaDaemon_logcatTag, "Executing dump");
			this.device.dumpWindowHierarchy(windowDumpFile);
			Log.v(uiaDaemon_logcatTag, "Dump executed");

			if (windowDumpFile.exists()) {
				return true;
			} else {
				Log.w(uiaDaemon_logcatTag, ".dumpWindowHierarchy returned, but the dumped file doesn't exist!");
				return false;
			}

		} catch (NullPointerException e) {
			Log.w(uiaDaemon_logcatTag, "Caught NPE while dumping window hierarchy. Msg: " + e.getMessage());
			return false;
		} catch (IllegalArgumentException e) {
			Log.w(uiaDaemon_logcatTag, "Caught IllegalArgumentException while dumping window hierarchy. Msg: " + e.getMessage());
			return false;
		} catch (IOException e) {
			Log.w(uiaDaemon_logcatTag, "Caught IOException while dumping window hierarchy. Msg: " + e.getMessage());
			return false;
		}
	}

	private File prepareWindowDumpFile(String fileName) throws UiAutomatorDaemonException {
		// Replaced original location for application directory due to the following access denied exception:
		//    Caught IOException while dumping window hierarchy.
		//       Msg: /data/local/tmp/window_hierarchy_dump.xml: open failed: EACCES (Permission denied)
		// More information in: http://stackoverflow.com/questions/23424602/android-permission-denied-for-data-local-tmp

		final File dir = context.getFilesDir();
		File file = new File(dir, fileName);

		Log.v(uiaDaemon_logcatTag, String.format("Dump data directory: %s", dir.toString()));
		Log.v(uiaDaemon_logcatTag, String.format("Dump data file: %s", file.toString()));

		// Here we ensure the directory of the target file exists.
		if (!dir.isDirectory())
			if (!dir.mkdirs())
				throw new UiAutomatorDaemonException("!windowDumpDir.isDirectory() && !windowDumpDir.mkdirs()");

		// Here we ensure the target file doesn't exist.
		if (file.isDirectory())
			throw new UiAutomatorDaemonException("windowDumpFile.isDirectory()");
		if (file.exists())
			if (!file.delete())
				throw new UiAutomatorDaemonException("windowDump.exists() && !windowDump.delete()");

		// Here we check if we ensured things correctly.
		if (file.exists()) {
			throw new AssertionError("Following assertion failed: !windowDump.exists()");
		}
		if (!(file.getParentFile().isDirectory())) {
			throw new AssertionError("Following assertion failed: windowDump.getParentFile().isDirectory()");
		}

		return file;
	}
}