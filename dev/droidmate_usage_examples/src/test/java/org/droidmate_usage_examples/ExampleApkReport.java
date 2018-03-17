// Copyright (c) 2012-2018 Saarland University
// All rights reserved.
//
// Author: Konrad Jamrozik, jamrozik@st.cs.uni-saarland.de
//
// This file is part of the "DroidMate" project.
//
// www.droidmate.org
package org.droidmate_usage_examples;

import org.droidmate.exploration.data_aggregators.IExplorationLog;
import org.droidmate.report.apk.ApkReport;

import java.nio.file.Path;

public class ExampleApkReport extends ApkReport{
    @Override
    protected void safeWriteApkReport(IExplorationLog iExplorationLog, Path path) {
        // Write your own report within the report/<APK>/ dir
    }
}
