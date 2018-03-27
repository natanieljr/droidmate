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

import kotlin.jvm.functions.Function1;
import org.droidmate.configuration.Configuration;
import org.droidmate.exploration.data_aggregators.IExplorationLog;
import org.droidmate.exploration.strategy.ExplorationStrategyPool;
import org.droidmate.exploration.strategy.IExplorationStrategy;
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleStrategyProvider implements Function1<IExplorationLog, IExplorationStrategy> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExampleStrategyProvider.class);

	private final Configuration cfg;

	ExampleStrategyProvider(Configuration cfg) {
		this.cfg = cfg;
	}

	@Override
	public IExplorationStrategy invoke(IExplorationLog explorationLog) {
		LOGGER.info("Loading stored exploration log from $storedLogFile");

		ISelectableExplorationStrategy strategy = new ExampleExplorationStrategy();

		ExplorationStrategyPool pool = ExplorationStrategyPool.Companion.build(explorationLog, cfg);

		pool.registerStrategy(strategy);

		return pool;
	}
}
