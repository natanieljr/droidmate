// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Saarland University
// All rights reserved.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// This file is part of the "DroidMate" project.
//
// www.droidmate.org
package org.droidmate_usage_examples;

import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import org.droidmate.android_sdk.DeviceException;
import org.droidmate.android_sdk.IApk;
import org.droidmate.apis.IApiLogcatMessage;
import org.droidmate.command.DroidmateCommand;
import org.droidmate.configuration.Configuration;
import org.droidmate.configuration.ConfigurationBuilder;
import org.droidmate.configuration.ConfigurationException;
import org.droidmate.device.datatypes.IDeviceGuiSnapshot;
import org.droidmate.device.datatypes.IWidget;
import org.droidmate.exploration.actions.ExplorationAction;
import org.droidmate.exploration.actions.ExplorationRecord;
import org.droidmate.exploration.actions.WidgetExplorationAction;
import org.droidmate.exploration.data_aggregators.IExplorationLog;
import org.droidmate.exploration.strategy.IMemoryRecord;
import org.droidmate.frontend.DroidmateFrontend;
import org.droidmate.report.OutputDir;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class contains tests showing example use cases of DroidMate API. To understand better how to work with DroidMate API,
 * please explore the source code of the DroidMate classes called by the examples here. For where to find the sources and how to
 * navigate them, please read <pre>https://github.com/konrad-jamrozik/droidmate/blob/master/README.md</pre>.
 */
public class MainTest {

	private static Configuration buildConfiguration() {
		String[] args = new String[]{
						Configuration.pn_resetEveryNthExplorationForward + "=30",
						Configuration.pn_actionsLimit + "=100",
						Configuration.pn_randomSeed + "=0",
						Configuration.pn_device + "=0",
						Configuration.pn_launchActivityDelay + "=7000",
						Configuration.pn_monitorSocketTimeout + "=7000",
						Configuration.pn_takeScreenshots + "=false",
						Configuration.pn_runOnNotInlined};

		try {
			return new ConfigurationBuilder().build(args, FileSystems.getDefault());
		} catch (ConfigurationException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * <p>
	 * This test shows how to access DroidMate API with default settings. If you run it right off the bat, DroidMate will inform
	 * you there are no input apks and then it will terminate. It will also tell you into which dir to put apks.
	 * If you put apks there, DroidMate will inform you why and how you should inline them. You can do it by running test
	 * {@link #inline_apks()}.
	 *
	 * </p><p>
	 * DroidMate will also tell you where to look for its run output. Both the .txt files and serialized results. To see how
	 * to access serialized results, see {@link #deserialize_and_work_with_exploration_result()}
	 *
	 * </p><p>
	 * If you feel lost, please read the README.md mentioned in {@link MainTest}.
	 *
	 * </p>
	 */
	@Test
	public void explore_with_default_settings() {
		callMainThenAssertExitStatusIs0(new String[]{});
	}

	/**
	 * <p>
	 * This test will make DroidMate inline all the apks present in the default input directory, which should be
	 * {@code dev/droidmate_usage_examples/apks}.
	 * </p><p>
	 * <p>
	 * You can find an apk to inline in {@code dev/droidmate_usage_examples/apks/originals}
	 * </p>
	 */
	@Test
	public void inline_apks() {
		callMainThenAssertExitStatusIs0(new String[]{Configuration.pn_inline});
	}

	/**
	 * <p>
	 * This test shows how to access various part of the data structure serialized by DroidMate to file system, containing all the
	 * results from the exploration. Note that the methods used are not exhaustive. Explore the sources
	 * of the used types to find out more.
	 *
	 * </p><p>
	 * To obtain the serialized results from the fixture again, you can run {@link #explore_with_common_settings_changed()}.
	 *
	 * </p><p>
	 * For details of such run (used to obtain the fixture for this test),
	 * please see {@code dev/droidmate_usage_examples/src/test/resources}.
	 * The apk used to obtain the fixture is located in {@code dev/droidmate_usage_examples/apks/inlined}.
	 *
	 * </p>
	 */
	@Test
	public void deserialize_fixture_and_work_with_exploration_result() throws IOException, URISyntaxException {
		workWithDroidmateOutput(copyDroidmateOutputFixtureToDir().getParent());
	}

	/**
	 * <p>
	 * This test is like {@link #deserialize_fixture_and_work_with_exploration_result}, but it doesn't work on a fixture, instead
	 * it works on default DroidMate output dir.
	 *
	 * </p><p>
	 * To get any meaningful output to stdout from this test, first run DroidMate on an inlined apk. To understand how to obtain
	 * an inlined apk, please read the doc mentioned in {@link MainTest}.
	 *
	 * </p>
	 */
	@Test
	public void deserialize_and_work_with_exploration_result() {
		workWithDroidmateOutput(Configuration.defaultDroidmateOutputDir);
	}

	/**
	 * <p>
	 * This test shows some common settings you would wish to override when running DroidMate. In any case, you can always consult
	 * source of Configuration class for more settings.
	 *
	 * </p><p>
	 * This test has been used to obtain fixture for {@link #deserialize_fixture_and_work_with_exploration_result()}.
	 *
	 * </p><p>
	 * To ensure this test does meaningful work, copy an apk file from {@code dev/droidmate_usage_examples/apks/inlined}
	 * to {@code dev/droidmate_usage_examples/apks}. By default this dir is empty, so it won't require any actual Android device
	 * presence. This is needed because we want to be able to run all tests (e.g. as part of "gradlew build") without access to
	 * an android device.
	 *
	 * </p>
	 */
	@Test
	public void explore_with_common_settings_changed() {
		List<String> args = new ArrayList<>();

		// "pn" stands for "parameter name"
		Collections.addAll(args, Configuration.pn_apksDir, Configuration.defaultApksDir);
		Collections.addAll(args, Configuration.pn_timeLimit, "10");
		Collections.addAll(args, Configuration.pn_resetEveryNthExplorationForward, String.valueOf(Configuration.defaultResetEveryNthExplorationForward));
		Collections.addAll(args, Configuration.pn_randomSeed, "43");
		Collections.addAll(args, Configuration.pn_androidApi, Configuration.api23);

		callMainThenAssertExitStatusIs0(args.toArray(new String[args.size()]));
	}

	/**
	 * This test shows how to make DroidMate run with your custom exploration strategy and termination criterion. Right now there
	 * is no base ExplorationStrategy from which you can inherit and the ITerminationCriterion interface is a bit rough. To help
	 * yourself, see how the actual DroidMate exploration strategy is implemented an its components
	 * <a href="https://github.com/konrad-jamrozik/droidmate/blob/ffd6da96e16978418d34b7f186699423d548e1f3/dev/droidmate/projects/core/src/main/groovy/org/droidmate/exploration/strategy/ExplorationStrategy.groovy#L90">on GitHub</a>
	 */
	@Test
	public void explore_with_custom_exploration_strategy_and_termination_criterion() {
		Configuration cfg = buildConfiguration();
		final DroidmateCommand commandProvider = ExampleCommandProvider.buildCommand(cfg);
		callMainThenAssertExitStatusIs0(new String[]{}, commandProvider);
	}

	private void callMainThenAssertExitStatusIs0(String[] args) {
		// null command means "do not override DroidMate command (and thus: any components) with custom implementation"
		final DroidmateCommand command = null;
		callMainThenAssertExitStatusIs0(args, command);
	}

	private void callMainThenAssertExitStatusIs0(String[] args, DroidmateCommand command) {
		int exitStatus;

		if (command != null)
			exitStatus = DroidmateFrontend.execute(args,
							configuration -> command);
		else
			exitStatus = DroidmateFrontend.execute(args);

		Assert.assertEquals(0, exitStatus);
	}

	private File copyDroidmateOutputFixtureToDir() throws IOException, URISyntaxException {
		File targetDir = new File("mock_droidmate_output_dir");
		//noinspection ResultOfMethodCallIgnored
		targetDir.mkdir();
		if (!targetDir.exists())
			throw new IllegalStateException();

		final URL fixtureURL = Iterables.getOnlyElement(
						Collections.list(
										ClassLoader.getSystemResources("fixture_out/stored.ser2")));
		final File fixtureFile = new File(fixtureURL.toURI());


		final File targetFile = new File("mock_droidmate_output_dir", fixtureFile.getName());
		Files.copy(fixtureFile, targetFile);

		if (!targetFile.exists())
			throw new IllegalStateException();

		return targetFile;
	}


	private void workWithDroidmateOutput(String outputDirPath) {
		final List<IExplorationLog> output = new OutputDir(Paths.get(outputDirPath)).getExplorationOutput2();
		output.forEach(this::workWithSingleApkExplorationOutput);
	}

	/**
	 * Please see the comment on {@link #deserialize_and_work_with_exploration_result}.
	 */
	private void workWithSingleApkExplorationOutput(IExplorationLog apkOut) {
		final IApk apk = apkOut.getApk();
		if (!apkOut.getExceptionIsPresent()) {

			int actionCounter = 0;
			for (ExplorationRecord actionWithResult : apkOut.getLogRecords()) {
				actionCounter++;

				final ExplorationAction action = actionWithResult.getAction().getBase();
				System.out.println("Action " + actionCounter + " is of type " + action.getClass().getSimpleName());

				if (action instanceof WidgetExplorationAction) {
					WidgetExplorationAction widgetAction = (WidgetExplorationAction) action;
					IWidget w = widgetAction.getWidget();
					System.out.println("Text of acted-upon widget of given action: " + w.getText());
				}

				final IMemoryRecord result = actionWithResult.getResult();
				final IDeviceGuiSnapshot guiSnapshot = result.getGuiSnapshot();

				System.out.println("Action " + actionCounter + " resulted in a screen containing following actionable widgets: ");
				for (IWidget widget : guiSnapshot.getGuiState().getActionableWidgets())
					System.out.println("Widget of class " + widget.getClassName() + " with bounds: " + widget.getBoundsString());

				final List<IApiLogcatMessage> apiLogs = result.getDeviceLogs().getApiLogs();
				System.out.println("Action " + actionCounter + " resulted in following calls to monitored Android framework's APIs being made:");
				for (IApiLogcatMessage apiLog : apiLogs)
					System.out.println(apiLog.getObjectClass() + "." + apiLog.getMethodName());
			}

			// Convenience method for accessing GUI snapshots resulting from all actions.
			@SuppressWarnings("unused") final List<IDeviceGuiSnapshot> guiSnapshots = apkOut.getGuiSnapshots();

			// Convenience method for accessing API logs resulting from all actions.
			@SuppressWarnings("unused") final List<List<IApiLogcatMessage>> apiLogs = apkOut.getApiLogs();
		} else {
			@SuppressWarnings("ThrowableResultOfMethodCallIgnored") final DeviceException exception = apkOut.getException();
			System.out.println("Exploration of " + apk.getFileName() + " resulted in exception: " + exception.toString());
		}
	}
}