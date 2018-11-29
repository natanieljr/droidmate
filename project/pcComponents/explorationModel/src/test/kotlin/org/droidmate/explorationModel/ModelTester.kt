package org.droidmate.explorationModel

import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.retention.StringCreator
import org.droidmate.explorationModel.retention.loading.dataString
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.util.*

/**
 * test cases:
 * - UiElementP UID computation
 * - Widget UID computation
 * - dump-String computations of: Widget & Interaction
 * - actionData creation/ model update for mocked ActionResult
 * (- ignore edit-field mechanism)
 * (- re-identification of State for ignored properties variations)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ModelTester: TestI, TestModel by DefaultTestModel() {

	@Test
	fun widgetUidTest(){
		val emptyWidget = Widget.emptyWidget
		expect(parentWidget.configId.toString(), "20ef5802-33dc-310b-8efc-c791586ede85")  // quickFix due to new UiElementP constructor
		expect(emptyWidget.configId, parentWidget.configId)
		expect(parentWidget.id.toString(),"70a351cc-119f-38b5-899d-5a4728752672_${parentWidget.configId}")
		expect(testWidget.parentId, emptyWidget.id)

		expect(testWidget.configId, UUID.fromString("5d802df8-481b-3882-9c3b-95ea87b08a03"))
	}

	@Test
	fun widgetDumpTest(){
		expect(testWidget.dataString(";"), testWidgetDumpString)
		val properties =StringCreator.parseWidgetPropertyString(testWidget.dataString(";").split(";"), StringCreator.defaultMap)
		expect(Widget(properties, testWidget.parentId).dataString(";"),testWidget.dataString(";"))
	}
}