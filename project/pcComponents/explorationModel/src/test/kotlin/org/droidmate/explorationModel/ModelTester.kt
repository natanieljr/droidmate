package org.droidmate.explorationModel

import org.droidmate.deviceInterface.guimodel.WidgetData
import org.droidmate.deviceInterface.guimodel.toUUID
import org.droidmate.explorationModel.config.ConcreteId
import org.droidmate.explorationModel.config.dumpString
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
class ModelTester: TestI, TestModel by DefaultTestModel() {

	@Test
	fun widgetUidTest(){
		val emptyWidget = Widget()
		expect(parentData.pId, WidgetData.empty().toString().replaceAfter("_uid",")").replace(", _uid","").toUUID())  // quickFix due to new WidgetData constructor
		expect(emptyWidget.propertyId, parentData.pId)
		expect(emptyWidget.uid, parentData.content().toUUID())
		expect(parentWidget.id.dumpString(),"ba2b45bd-c11e-3a4a-ae86-aab2ac693cbb_ad50bfd4-2f22-3d8d-b716-fa93641caa1c")

		expect(testWidgetData.pId, UUID.fromString("a24a0a9f-6bdc-3726-81f0-97f4011ee3b1"))
		expect(testWidget.uid, Widget.computeId(testWidgetData).first)
		expect(testWidget.propertyId, testWidgetData.pId)
		expect(testWidget.id, ConcreteId(Widget.computeId(testWidgetData).first, testWidgetData.pId))
		expect(testWidget.parentId, emptyWidget.id)
	}

	@Test
	fun widgetDumpTest(){
		expect(testWidget.dataString(";"), testWidgetDumpString)
		expect(Widget.fromString(testWidget.splittedDumpString(";")).dataString(";"),testWidget.dataString(";"))
	}
}