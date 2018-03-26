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

package org.droidmate.tests.exploration.strategy

/**
 * Auxiliary functions for testing
 */
object Auxiliary {
    // TODO Fix tests
    /*private fun createWidget(id: String, actionable: Boolean): OldWidget {
        return OldWidget(id).apply {
            packageName = "STUB!"
            bounds = Rectangle(1, 1, 5, 5)
            deviceDisplayBounds = Rectangle(100, 100)
            enabled = actionable
            clickable = actionable
        }
    }

    fun createTestWidgets(): List<OldWidget> {
        val result = ArrayList<OldWidget>()

        result.add(Auxiliary.createWidget("Widget0", true))
        result.add(Auxiliary.createWidget("Widget1", false))
        result.add(Auxiliary.createWidget("Widget2", true))
        result.add(Auxiliary.createWidget("Widget3", false))
        result.add(Auxiliary.createWidget("Widget4", true))

        return result
    }

    @JvmOverloads
    fun createGuiStateFromFile(packageName: String = "ch.bailu.aat"): IGuiStatus {
        try {
            val fileData = ResourceManager.getResourceAsStringList("ch.bailu.aat_18.xml")
            val fileStr = fileData.joinToString(separator = "")
            val dump = UiautomatorWindowDump(fileStr,
                    Dimension(1800, 2485),
                    packageName
            )

            return dump.guiStatus

        } catch (e: IOException) {
            throw UnsupportedOperationException(e)
        } catch (e: URISyntaxException) {
            throw UnsupportedOperationException(e)
        }

    }

    fun createTestWidgetFromRealApp(): List<ITargetWidget> {
        val testData = ArrayList<ITargetWidget>()

        val guiState = Auxiliary.createGuiStateFromFile()
        val widgets = guiState.widgets

        // Button About has no dependency
        widgets.stream()
                .filter { p -> p.text == "About" }
                .forEach { p -> testData.add(TargetWidget(p)) }

        // Other has order GPS/Tracker
        val targetDep = widgets.stream()
                .filter { p -> p.text == "GPS" }
                .findFirst()

        val dep1: ITargetWidget
        dep1 = targetDep
                .map<ITargetWidget> { widget -> TargetWidget(widget) }
                .orElse(null)
        widgets.stream()
                .filter { p -> p.text == "Tracker" }
                .forEach { p -> testData.add(TargetWidget(p, dep1)) }

        return testData
    }

    fun createTestConfig(args: Array<String>): Configuration {
        try {
            return ConfigurationBuilder().build(args)
        } catch (e: ConfigurationException) {
            Assert.fail()
            throw UnsupportedOperationException(e)
        }

    }*/
}
