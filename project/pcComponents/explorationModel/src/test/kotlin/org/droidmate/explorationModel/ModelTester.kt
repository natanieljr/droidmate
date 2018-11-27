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
		expect(parentWidget.configId.toString(), "655be249-a31d-33b1-9f77-566cc3bef242")  // quickFix due to new UiElementP constructor
		expect(emptyWidget.configId, parentWidget.configId)
		expect(parentWidget.id.toString(),"3a1ac1a7-1301-368c-9d59-d025a743531c_${parentWidget.configId}")
		expect(testWidget.parentId, emptyWidget.id)

		expect(testWidget.configId, UUID.fromString("845d7969-31c5-392a-b7be-f6ab2f4f565b"))
	}

	@Test
	fun widgetDumpTest(){
		expect(testWidget.dataString(";"), testWidgetDumpString)
		val properties =StringCreator.parseWidgetPropertyString(testWidget.dataString(";").split(";"), StringCreator.defaultMap)
		expect(Widget(properties, testWidget.parentId).dataString(";"),testWidget.dataString(";"))
	}
}