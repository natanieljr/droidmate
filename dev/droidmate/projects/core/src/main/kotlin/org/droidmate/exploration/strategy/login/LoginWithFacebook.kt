// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
package org.droidmate.exploration.strategy.login

import org.droidmate.device.datatypes.Widget
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newWidgetExplorationAction
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.exploration.strategy.ResourceManager
import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.exploration.strategy.widget.Explore
import org.droidmate.misc.DroidmateException
import java.io.IOException

@Suppress("unused")
class LoginWithFacebook : Explore() {
    private val DEFAULT_ACTION_DELAY = 5000
    private val emailValue: String
    private val passwordValue: String

    init {
        var email = ""
        var password = ""

        try {
            val data = ResourceManager.getResourceAsStringList("facebookLogin.config")

            data.forEach { row ->
                if (row.contains("username="))
                    email = row.removePrefix("username=")
                else if (row.contains("password="))
                    password = row.removePrefix("password=")
            }
        } catch (e: IOException) {
            // Just log
            logger.error(e.message, e)
        }

        if (email.isEmpty() || password.isEmpty()) {
            throw DroidmateException("Invalid facebook configuration file. To use this strategy it is necessary to " +
                    "have a resource file called 'facebookLogin.config' with a row username=<USERNAME> and a second " +
                    "row with password=<PASSWORD>.")
        }

        emailValue = email
        passwordValue = password
    }

    private var signInClicked = false
    private var emailInserted = false
    private var passwordInserted = false
    private var loginClicked = false
    private var continueClicked = false

    private val RES_ID_EMAIL = "m_login_email"
    private val RES_ID_PASSWORD = "m_login_password"
    private val CONTENT_DESC_LOGIN = arrayOf("Log In", "Continue")
    private val CONTENT_DESC_CONTINUE = "Continue"

    private fun getSignInButton(widgets: List<Widget>): Widget? {
        return widgets.firstOrNull {
            it.className.toLowerCase().contains("button") &&
                    // Text = Facebook - Id = Any
                    ((it.text.toLowerCase() == "facebook") ||
                            // Text = Login - Id = *facebook*
                            ((it.text.toLowerCase() == "login") && (it.resourceId.toLowerCase().contains("facebook"))) ||
                            // Text = Sign In with Facebook - Id = Any
                            ((it.text.toLowerCase().contains("sign in")) && (it.text.toLowerCase().contains("facebook"))))
        }
    }

    private fun canClickSignIn(widgets: List<Widget>): Boolean {
        return (!signInClicked) &&
                this.getSignInButton(widgets) != null
    }

    private fun clickSignIn(widgets: List<Widget>): ExplorationAction {
        val button = getSignInButton(widgets)

        if (button != null) {
            signInClicked = true
            return ExplorationAction.newWidgetExplorationAction(button)
        }

        throw DroidmateException("The exploration shouldn' have reached this point.")
    }

    private fun canInsertEmail(widgets: List<Widget>): Boolean {
        return !emailInserted &&
                widgets.any { it.resourceId == RES_ID_EMAIL }
    }

    private fun insertEmail(widgets: List<Widget>): ExplorationAction {
        val button = widgets.firstOrNull { it.resourceId == RES_ID_EMAIL }

        if (button != null) {
            signInClicked = true
            emailInserted = true
            return ExplorationAction.newEnterTextExplorationAction(emailValue, button)
        }

        throw DroidmateException("The exploration shouldn' have reached this point.")
    }

    private fun canInsertPassword(widgets: List<Widget>): Boolean {
        return !passwordInserted &&
                widgets.any { it.resourceId == RES_ID_PASSWORD }
    }

    private fun insertPassword(widgets: List<Widget>): ExplorationAction {
        val button = widgets.firstOrNull { it.resourceId == RES_ID_PASSWORD }

        if (button != null) {
            passwordInserted = true
            return ExplorationAction.newEnterTextExplorationAction(passwordValue, button)
        }

        throw DroidmateException("The exploration shouldn' have reached this point.")
    }

    private fun canClickLogInButton(widgets: List<Widget>): Boolean {
        return !loginClicked &&
                widgets.any { w -> CONTENT_DESC_LOGIN.any { c -> c.trim() == w.contentDesc.trim() } }
    }

    private fun clickLogIn(widgets: List<Widget>): ExplorationAction {
        val button = widgets.firstOrNull { w -> CONTENT_DESC_LOGIN.any { c -> c.trim() == w.contentDesc.trim() } }

        if (button != null) {
            loginClicked = true
            // Logging in on facebook is sometimes slow. Add a 3 seconds delay
            return ExplorationAction.newWidgetExplorationAction(button, DEFAULT_ACTION_DELAY)
        }

        throw DroidmateException("The exploration shouldn' have reached this point.")
    }

    private fun canClickContinueButton(widgets: List<Widget>): Boolean {
        return !continueClicked &&
                widgets.any { it.contentDesc.trim() == CONTENT_DESC_CONTINUE }
    }

    private fun clickContinue(widgets: List<Widget>): ExplorationAction {
        val button = widgets.firstOrNull { it.contentDesc.trim() == CONTENT_DESC_CONTINUE }

        if (button != null) {
            continueClicked = true
            // Logging in on facebook is sometimes slow. Add a 3 seconds delay
            return ExplorationAction.newWidgetExplorationAction(button, DEFAULT_ACTION_DELAY)
        }

        throw DroidmateException("The exploration shouldn' have reached this point.")
    }

    override fun mustPerformMoreActions(): Boolean {
        // Between sign in and log in it's a single process, afterwards it may change depending on
        // what facebook displays, therefore handle it on a case by case basis on getFitness method
        return signInClicked && !loginClicked
    }

    override fun getFitness(): StrategyPriority {
        // Not the correct app, or already logged in
        if (continueClicked)
            return StrategyPriority.NONE

        val widgets = context.getCurrentState().actionableWidgets

        // Can click on login
        if (canClickSignIn(widgets) ||
                canInsertEmail(widgets) ||
                canInsertPassword(widgets) ||
                canClickLogInButton(widgets) ||
                canClickContinueButton(widgets))
            return StrategyPriority.SPECIFIC_WIDGET

        return StrategyPriority.NONE
    }

    private fun getWidgetAction(widgets: List<Widget>): ExplorationAction {
        // Can click on login
        return when {
            canClickSignIn(widgets) -> clickSignIn(widgets)
            canInsertEmail(widgets) -> insertEmail(widgets)
            canInsertPassword(widgets) -> insertPassword(widgets)
            canClickLogInButton(widgets) -> clickLogIn(widgets)
            canClickContinueButton(widgets) -> clickContinue(widgets)
            else -> throw UnexpectedIfElseFallthroughError("Should not have reached this point. $widgets")
        }
    }

    override fun chooseAction(): ExplorationAction {
        return if (context.getCurrentState().isRequestRuntimePermissionDialogBox) {
            val widget = context.getCurrentState().widgets.let { widgets ->
                widgets.firstOrNull { it.resourceId == "com.android.packageinstaller:id/permission_allow_button" }
                    ?: widgets.first { it.text.toUpperCase() == "ALLOW" }
            }
            newWidgetExplorationAction(widget)
        } else {
            val widgets = context.getCurrentState().actionableWidgets
            getWidgetAction(widgets)
        }
    }

    override fun equals(other: Any?): Boolean {
        return (other is LoginWithFacebook)
    }

    override fun hashCode(): Int {
        return this.RES_ID_EMAIL.hashCode()
    }

    override fun toString(): String {
        return javaClass.simpleName
    }

    companion object {
        /**
         * Creates a new exploration strategy instance to login using facebook
         * Tested on:
         * - Booking (com.booking)
         * - Candidate (at.schneider_holding.candidate)
         * - TripAdvisor (com.tripadvisor.tripadvisor)
         */
        fun build(): ISelectableExplorationStrategy {
            return LoginWithFacebook()
        }
    }
}