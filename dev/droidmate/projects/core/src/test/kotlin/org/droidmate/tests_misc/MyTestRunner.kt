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
package org.droidmate.tests_misc;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.slf4j.LoggerFactory;

class MyTestRunner @Throws(InitializationError::class) constructor(klass: Class<*>) : BlockJUnit4ClassRunner(klass) {

    override fun run(notifier: RunNotifier?) {
        if (notifier == null)
            return

        notifier.addFirstListener(CustomRunListener())
        super.run(notifier)
    }

    class CustomRunListener() : RunListener() {
        override fun testFailure(failure: Failure?) {
            val exception = failure?.exception
            // The root logger is configured to print out the "root cause first' stack trace.
            LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).error("err: ", exception)
        }
    }
}
