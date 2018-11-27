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
		expect(parentWidget.configId.toString(), "5a116021-8aa6-370a-aa94-03ab99745b25")  // quickFix due to new UiElementP constructor
		expect(emptyWidget.configId, parentWidget.configId)
		expect(parentWidget.id.toString(),"269e17d0-32f9-3d14-bdab-ab2c6c8f100e_${parentWidget.configId}")
		expect(testWidget.parentId, emptyWidget.id)

		expect(testWidget.configId, UUID.fromString("defcef0e-5a90-38a2-a77d-b62d34e93723"))
	}

	@Test
	fun widgetDumpTest(){
		expect(testWidget.dataString(";"), testWidgetDumpString)
		val properties =StringCreator.parseWidgetPropertyString(testWidget.dataString(";").split(";"), StringCreator.defaultMap)
		expect(Widget(properties, testWidget.parentId).dataString(";"),testWidget.dataString(";"))
	}
}