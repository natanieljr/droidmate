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

import org.droidmate.command.DroidmateCommand;
import org.droidmate.command.ExploreCommand;
import org.droidmate.command.exploration.Exploration;
import org.droidmate.command.exploration.IExploration;
import org.droidmate.configuration.Configuration;
import org.droidmate.configuration.ConfigurationBuilder;
import org.droidmate.configuration.ConfigurationException;
import org.droidmate.misc.TimeProvider;
import org.droidmate.storage.IStorage2;
import org.droidmate.storage.Storage2;
import org.droidmate.tools.*;

import java.nio.file.FileSystems;
import java.nio.file.Path;

public class ExampleCommandProvider extends ExploreCommand {

    private ExampleCommandProvider(IApksProvider apksProvider,
                                   IAndroidDeviceDeployer deviceDeployer,
                                   IApkDeployer apkDeployer,
                                   IExploration exploration,
                                   IStorage2 storage2) {
        super(apksProvider, deviceDeployer, apkDeployer, exploration, storage2);
    }

    public static DroidmateCommand buildCommand(Configuration cfg){
        DeviceTools deviceTools = new DeviceTools(cfg);
        ApksProvider apksProvider = new ApksProvider(deviceTools.getAapt());
        TimeProvider timeProvider = new TimeProvider();

        ExampleStrategyProvider testifyStrategy = new ExampleStrategyProvider(cfg);

        IStorage2 storage2 = new Storage2(cfg.droidmateOutputDirPath);
        IExploration exploration = Exploration.build(cfg, timeProvider, testifyStrategy);

        ExampleCommandProvider command = new ExampleCommandProvider(apksProvider, deviceTools.getDeviceDeployer(), deviceTools.getApkDeployer(),
                exploration, storage2);

        // New reports
        command.registerReporter(new ExampleReport());
        command.registerReporter(new ExampleApkReport());

        return command;
    }
}
