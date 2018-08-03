package org.droidmate.exploration.statemodel

import org.droidmate.test_tools.DroidmateTestCase
import org.droidmate.deviceInterface.guimodel.WidgetData
import org.droidmate.deviceInterface.guimodel.toUUID
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.util.*

/**
 * test cases:
 * - WidgetData UID computation
 * - Widget UID computation
 * - dump-String computations of: Widget & ActionData
 * - actionData creation/ model update for mocked ActionResult
 * (- ignore edit-field mechanism)
 * (- re-identification of State for ignored properties variations)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ModelTester: DroidmateTestCase(), TestModel by DefaultTestModel() {

	@Test
	fun widgetUidTest(){
		val emptyWidget = Widget()
		expect(parentData.uid, WidgetData.empty().toString().toUUID())
		expect(emptyWidget.propertyId, parentData.uid)
		expect(emptyWidget.uid, parentData.content().toUUID())
		expect(parentWidget.id.dumpString(),"ba2b45bd-c11e-3a4a-ae86-aab2ac693cbb_ad50bfd4-2f22-3d8d-b716-fa93641caa1c")

		expect(testWidgetData.uid, UUID.fromString("a24a0a9f-6bdc-3726-81f0-97f4011ee3b1"))
		expect(testWidget.uid, Widget.computeId(testWidgetData).first)
		expect(testWidget.propertyId, testWidgetData.uid)
		expect(testWidget.id, ConcreteId(Widget.computeId(testWidgetData).first, testWidgetData.uid))
		expect(testWidget.parentId, emptyWidget.id)
	}

	@Test
	fun widgetDumpTest(){
		expect(testWidget.dataString(";"), testWidgetDumpString)
		expect(Widget.fromString(testWidget.splittedDumpString(";")).dataString(";"),testWidget.dataString(";"))
	}
}