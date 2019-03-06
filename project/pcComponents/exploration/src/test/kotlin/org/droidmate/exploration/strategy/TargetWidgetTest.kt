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

package org.droidmate.exploration.strategy

import org.droidmate.test_tools.DroidmateTestCase
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

/**
 * Testing class for Backstage wrapper stub.
 * This testing is necessary because during development the stub will be
 * used frequently, since backstage takes a long time to execute.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class TargetWidgetTest : DroidmateTestCase() {
	// TODO Fix tests
	@Test
	fun dummy() {
	}
	// TODO Fix tests
	/*private fun createWidget(id: String): Widget {
			return UiElementP.defaultProperties.toMutableMap().apply {
					replace(UiElementP::id.name,id)
					replace(UiElementP::packageName.name,"STUB!")
					replace(UiElementP::bounds.name,Rectangle(1, 1, 5, 5))
					replace(UiElementP::enabled.name,true)
					replace(UiElementP::clickable.name,true)
			}.let{ Widget(UiElementP(it)) }
//        w.deviceDisplayBounds = Rectangle(100, 100)
	}

	private fun createTestWidgetList(): List<ITargetWidget> {
			val testData = ArrayList<ITargetWidget>()
			testData.update(TargetWidget(this.createWidget("Widget1")))

			val dependencyWidget3 = TargetWidget(
							this.createWidget("Widget3"))
			val dependencyWidget4 = TargetWidget(
							this.createWidget("Widget4"), dependencyWidget3)
			val dependencyWidget5 = TargetWidget(
							this.createWidget("Widget5"))
			val dependencyWidget2 = TargetWidget(
							this.createWidget("Widget2"), dependencyWidget4, dependencyWidget5)
			testData.update(dependencyWidget2)

			// Check initialization
			for (target in testData) {
					Assert.assertFalse(target.isSatisfied)
					if (target.hasDependencies())
							Assert.assertFalse(target.isDependenciesSatisfied())
			}

			return testData
	}

	private fun satisfyChildren(widgets: List<ITargetWidget>) {
			for (widget in widgets) {
					if (widget.hasDependencies()) {
							val unsatisfiedChildren = widget.dependencies.filterNot { it.isSatisfied }
							this.satisfyChildren(unsatisfiedChildren)
					}

					widget.satisfy()
			}
	}

	@Test
	fun satisfyTargetWithoutDependencies() {
			val targets = this.createTestWidgetList()

			// First widget has no dependency and should be satisfiable
			val widget1 = targets[0]
			Assert.assertFalse(widget1.hasDependencies())
			widget1.satisfy()
			Assert.assertTrue(widget1.isSatisfied)
	}

	@Test
	fun satisfyTargetWithDependencies() {
			val targets = this.createTestWidgetList()

			val widget2 = targets[1]

			// Can't satisfy until all dependencies are ok
			try {
					widget2.satisfy()
					Assert.fail()
			} catch (e: AssertionError) {
					// do noting
					Assert.assertTrue(true)
			}

			this.satisfyChildren(widget2.dependencies.filterNot { it.isSatisfied })
			Assert.assertFalse(widget2.isSatisfied)
			Assert.assertTrue(widget2.isDependenciesSatisfied())
			widget2.satisfy()
			Assert.assertTrue(widget2.isSatisfied)
	}

	@Test
	fun satisfyTargetIgnoreDependencies() {
			val targets = this.createTestWidgetList()

			val widget2 = targets[1]

			// Can't satisfy until all dependencies are ok, unless explicitly defining
			try {
					widget2.satisfy()
					Assert.fail()
			} catch (e: AssertionError) {
					// do noting
					Assert.assertTrue(true)
			}
			Assert.assertFalse(widget2.isSatisfied)
			widget2.satisfy(true)
			Assert.assertTrue(widget2.isSatisfied)
			Assert.assertFalse(widget2.isDependenciesSatisfied())
	}*/
}
