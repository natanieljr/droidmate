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

import org.droidmate.exploration.data_aggregators.IExplorationLog;
import org.droidmate.report.Reporter;

import java.nio.file.Path;
import java.util.List;

public class ExampleReport extends Reporter {
	@Override
	protected void safeWrite(Path path, List<? extends IExplorationLog> list) {
		// Write method on the main report dir
	}
}
