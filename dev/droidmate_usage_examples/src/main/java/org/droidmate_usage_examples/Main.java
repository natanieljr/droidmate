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

import org.droidmate.frontend.DroidmateFrontend;

public class Main {
    /**
     * This method shows example usage of DroidMate API. For more examples, please see <tt>org.droidmate_usage_examples.MainTest</tt>
     * located in
     * <pre>repo/dev/droidmate_usage_examples/src/test/java/org/droidmate_usage_examples/MainTest.java</pre>
     * where <tt>repo</tt> is your local clone of
     * <pre>https://github.com/konrad-jamrozik/droidmate</pre>
     */
    public static void main(String[] args) {
        DroidmateFrontend.execute(new String[]{"-help"});
    }
}