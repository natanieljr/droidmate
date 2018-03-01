// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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

package org.droidmate.exploration.strategy

import org.droidmate.device.datatypes.IGuiState
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newEnterTextExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newTerminateExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newWidgetExplorationAction
import org.slf4j.LoggerFactory

class ForwardExplorationSpecialCases : IForwardExplorationSpecialCases {

    companion object {
        private val log = LoggerFactory.getLogger(ForwardExplorationSpecialCases::class.java)
        @JvmStatic
        private val user = "debugg7"
        @JvmStatic
        private val pass = "qwer////"
        @JvmStatic
        val loginDelay = 3000 // ms

        private fun loginSucceeded(guiState: IGuiState, packageName: String): Boolean =
                inSnapchat(packageName) && !onAnyLoginScreen(guiState, packageName)

        private fun inSnapchat(packageName: String): Boolean = packageName == "com.snapchat.android"

        private fun onAnyLoginScreen(guiState: IGuiState, packageName: String): Boolean =
                onLandingPage(guiState, packageName) || onLoginEntryPage(guiState, packageName) || onTryAgainPopup(guiState, packageName)

        private fun onLandingPage(guiState: IGuiState, packageName: String): Boolean =
                inSnapchat(packageName) && guiState.widgets.any { it.text == "LOG IN" } &&
                        guiState.widgets.any { it.text == "SIGN UP" }

        private fun onLoginEntryPage(guiState: IGuiState, packageName: String): Boolean =
                inSnapchat(packageName) && guiState.widgets.any { it.text in arrayListOf("LOG IN", "Log In") } &&
                        !guiState.widgets.any { it.text == "SIGN UP" }

        private fun onTryAgainPopup(guiState: IGuiState, packageName: String): Boolean =
                inSnapchat(packageName) && guiState.widgets.any { it.text == "Try again" }
    }

    private val snapchatLoginAttempts = 5
    private var snapchatLoginAttemptsLeft = snapchatLoginAttempts

    private var lastStep = Step.UNKNOWN

    private enum class Step {
        // @formatter:off
        // After app reset, when we still have to determine if login is necessary at all
        UNKNOWN,
        // Step 1
        CLICK_TO_GO_TO_LOGIN_ENTRY_PAGE,
        // Step 2
        ENTER_USER,
        // Step 3
        ENTER_PASS,
        // Step 4
        LOG_INTO_THE_APP,
        // If login succeeds
        NONE,
        // If login fails
        CLICK_TRY_AGAIN_POPUP,
        // If all login attempts have been exhausted or unknown GUI state is encountered
        TERMINATE
        // @formatter:on
    }

    override fun process(guiState: IGuiState, packageName: String): Pair<Boolean, ExplorationAction?> {
        /*
        At this time special processing is turned off. It was being used only for Snapchat.
        Snapchat can no longer be explored due to remote update that prevents using any but the newer versions.
        The never versions have integrity check of bytecode.
        The check makes Snapchat unusable after startup if it was inlined.
        Thus, it can no longer be explored by DroidMate, as it required inlining.

        Furthermore, only this code used "EnterTextExplorationAction". Its handling is not implemented in the code.
        To be exact, it is not handled in RunnableExplorationAction.from() method.
        This is because code has been refactored after Snapchat stopped working.

        Thus, the code is turned off to prevent triggering the bug https://hg.st.cs.uni-saarland.de/issues/995.
         */
//    def currentStep = updateLoginStep(guiState, packageName, lastStep)
//
//    def outExplAction = proceedWithSnapchatLogin(guiState, currentStep)
//
//    lastStep = currentStep
//    return [outExplAction != null, outExplAction]
        return Pair(false, null)
    }

    private fun updateLoginStep(guiState: IGuiState, packageName: String, lastLoginStep: Step): Step {
        val currentStep: Step
        when (lastLoginStep) {
            Step.UNKNOWN -> currentStep = initiateLoginIfNecessary(guiState, packageName)
            Step.CLICK_TO_GO_TO_LOGIN_ENTRY_PAGE -> {
                assert(onLoginEntryPage(guiState, packageName))
                currentStep = Step.ENTER_USER
            }
            Step.ENTER_USER -> {
                assert(onLoginEntryPage(guiState, packageName))
                currentStep = Step.ENTER_PASS
            }
            Step.ENTER_PASS -> {
                assert(onLoginEntryPage(guiState, packageName))
                currentStep = Step.LOG_INTO_THE_APP
            }

            Step.LOG_INTO_THE_APP -> {
                currentStep = evaluateLoginResult(guiState, packageName)
                assert(currentStep in arrayListOf(Step.NONE, Step.CLICK_TRY_AGAIN_POPUP, Step.TERMINATE))
            }

            Step.NONE -> {
                currentStep = if (onLandingPage(guiState, packageName)) Step.CLICK_TO_GO_TO_LOGIN_ENTRY_PAGE else Step.NONE
                assert(!onLoginEntryPage(guiState, packageName))
                assert(!onTryAgainPopup(guiState, packageName))
            }

            Step.CLICK_TRY_AGAIN_POPUP -> {
                currentStep = Step.ENTER_USER
                assert(onLoginEntryPage(guiState, packageName))
            }
            ForwardExplorationSpecialCases.Step.TERMINATE -> {
                throw UnexpectedIfElseFallthroughError()
            }
        }
        return currentStep
    }

    private fun initiateLoginIfNecessary(guiState: IGuiState, packageName: String): Step {
        // True when exploring other apps.
        if (!inSnapchat(packageName))
            return Step.NONE

        if (onLandingPage(guiState, packageName))
            return Step.CLICK_TO_GO_TO_LOGIN_ENTRY_PAGE

        assert(!onLoginEntryPage(guiState, packageName))
        assert(!onTryAgainPopup(guiState, packageName))

        assert(inSnapchat(packageName))
        return Step.NONE
    }

    private fun proceedWithSnapchatLogin(guiState: IGuiState, currentStep: Step): ExplorationAction? {
        assert(currentStep != Step.UNKNOWN)
        val action: ExplorationAction?

        when (currentStep) {
            Step.CLICK_TO_GO_TO_LOGIN_ENTRY_PAGE -> {
                val w = guiState.widgets.find { it.text == "LOG IN" }
                action = newWidgetExplorationAction(w!!, true)
            }

            Step.ENTER_USER -> {
                if (guiState.widgets.any { it.resourceId == "com.snapchat.android:id/login_username_email" })
                // Works for Snapchat from March 2014 / CCS 2014
                    action = newEnterTextExplorationAction(user, "com.snapchat.android:id/login_username_email")
                else {
                    assert(guiState.widgets.any {
                        it.resourceId == "com.snapchat.android:id/login_username_email_field"
                    })
                    // Works for Snapchat from February 2015
                    action = newEnterTextExplorationAction(user, "com.snapchat.android:id/login_username_email_field")
                }
            }

            Step.ENTER_PASS -> {
                if (guiState.widgets.any { it.resourceId == "com.snapchat.android:id/login_password" })
                // Works for Snapchat from March 2014 / CCS 2014
                    action = newEnterTextExplorationAction(pass, "com.snapchat.android:id/login_password")
                else {
                    assert(guiState.widgets.any { it.resourceId == "com.snapchat.android:id/login_password_field" })
                    // Works for Snapchat from February 2015
                    action = newEnterTextExplorationAction(pass, "com.snapchat.android:id/login_password_field")
                }
            }

            Step.LOG_INTO_THE_APP -> {
                // Works for both versions of snapchat
                val w = guiState.widgets.find { it.text == "LOG IN" }
                action = newWidgetExplorationAction(w!!, loginDelay, true)
            }

            Step.NONE -> {
                action = null
            }

            Step.CLICK_TRY_AGAIN_POPUP -> {
                // Works for both versions of snapchat
                val w = guiState.widgets.find { it.text == "Try again" }
                action = newWidgetExplorationAction(w!!, true)
            }

            Step.TERMINATE -> action = newTerminateExplorationAction()

            Step.UNKNOWN -> {
                assert(false, { "Current step cannot be 'unknown'" })
                action = null
            }
        }

        assert(action != null || currentStep == Step.NONE)
        return action
    }

    private fun evaluateLoginResult(guiState: IGuiState, packageName: String): Step {
        if (loginSucceeded(guiState, packageName)) {
            snapchatLoginAttemptsLeft = snapchatLoginAttempts
            return Step.NONE
        } else {
            snapchatLoginAttemptsLeft--
            log.warn("Failed to login to snapchat. Making another attempt. Attempts left after this one: $snapchatLoginAttemptsLeft")

            if (snapchatLoginAttemptsLeft == 0) {
                log.warn("Aborting exploration of Snapchat: all $snapchatLoginAttempts hard-coded login attempts failed.\n" +
                        "Details: expected to start the hard-coded login process, but the current GUI state doesn't have the expected " +
                        "'SIGN UP' button on it.\n" +
                        "Possible reason: previous login attempt failed because wifi is disabled and snapchat displays the login data entry " +
                        "login screen instead of the initial one with 'SIGN UP' button. To confirm this is the case, please investigate the " +
                        "logs from the run.")
                return Step.TERMINATE
            }

            if (onTryAgainPopup(guiState, packageName))
                return Step.CLICK_TRY_AGAIN_POPUP
            else {
                log.warn("Ended up in unknown GUI state after making an attempt to log into snapchat. $guiState")
                return Step.TERMINATE
            }
        }
    }
}
