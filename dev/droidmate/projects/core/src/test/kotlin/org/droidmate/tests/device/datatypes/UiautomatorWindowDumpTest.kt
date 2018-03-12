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

package org.droidmate.tests.device.datatypes

import org.droidmate.device.datatypes.Widget
import org.droidmate.test_tools.DroidmateTestCase
import org.droidmate.test_tools.device.datatypes.UiautomatorWindowDumpTestHelper
import org.droidmate.test_tools.device.datatypes.WidgetTestHelper
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.awt.Rectangle

// WISH add test checking that widget.canBeClicked or not, depending if it intersects with visible device display bounds.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class UiautomatorWindowDumpTest : DroidmateTestCase() {

    /**
     * Bug: ANR with disabled OK button is invalid
     * https://hg.st.cs.uni-saarland.de/issues/987
     */
    @Test
    fun `Has no bug #987`() {
        val gs = UiautomatorWindowDumpTestHelper.newAppHasStoppedDialogOKDisabledWindowDump()
        assert(!gs.validationResult.valid)
    }

    @Test
    fun `Uiautomator window dump 'empty' fixture is indeed empty`() {
        val sut = UiautomatorWindowDumpTestHelper.newEmptyActivityWindowDump()

        // Act
        val guiState = sut.guiState

        assert(guiState.getActionableWidgets().isEmpty())
    }

    @Test
    fun `Gets GUI state from window dump and parses widgets with negative bounds`() {
        // Arrange
        val w1 = WidgetTestHelper.newClickableWidget(mutableMapOf("text" to "fake_control", "bounds" to arrayListOf(-100, -5, 90, -3)))
        val w2 = WidgetTestHelper.newClickableWidget(mutableMapOf("text" to "dummy_button", "bounds" to arrayListOf(15, -50379, 93, -50357)))
        val inputFixture = UiautomatorWindowDumpTestHelper.createDumpSkeleton(UiautomatorWindowDumpTestHelper.dump(w1) + UiautomatorWindowDumpTestHelper.dump(w2))

        val sut = UiautomatorWindowDumpTestHelper.newWindowDump(inputFixture)

        // Act
        val guiState = sut.guiState

        // Assert

        assert(guiState.widgets.size == 2)
        assert(w1 == guiState.widgets[0])
        assert(w2 == guiState.widgets[1])
    }

    @Test
    fun `Gets GUI state from 'app has stopped' dialog box`() {
        // Act
        val sut = UiautomatorWindowDumpTestHelper.newAppHasStoppedDialogWindowDump()

        val guiState = sut.guiState

        val nexus7vertAppHasStoppedDialogBoxBounds = arrayListOf(138, 620, 661, 692)

        val expected = WidgetTestHelper.newClickableWidget(mutableMapOf("bounds" to nexus7vertAppHasStoppedDialogBoxBounds, "text" to "OK"))
        assert(guiState.getActionableWidgets().size == 1)
        assert(expected.bounds == guiState.getActionableWidgets()[0].bounds)
        assert(expected.text == guiState.getActionableWidgets()[0].text)
    }

    @Test
    fun `Gets GUI state from home screen`() {
        // Arrange

        val sut = UiautomatorWindowDumpTestHelper.newHomeScreenWindowDump()

        // Act

        val guiState = sut.guiState

        // Assert

        assert(guiState.isHomeScreen)
    }

    @Test
    fun `Parses bounds`() {
        assert(Rectangle(100, 150, 1, 3) == Widget.parseBounds("[100,150][101,153]"))
    }

    @Test
    fun `Recognizes 'Select a Home app' dialog box`() {
        val gs = UiautomatorWindowDumpTestHelper.newSelectAHomeAppWindowDump()
        assert(gs.guiState.isSelectAHomeAppDialogBox)
    }
}