package org.droidmate.exploration.statemodel

import org.droidmate.exploration.statemodel.config.ConcreteId
import org.droidmate.exploration.statemodel.config.dumpString
import org.droidmate.test_tools.DroidmateTestCase
import org.droidmate.uiautomator_daemon.WidgetData
import org.droidmate.uiautomator_daemon.toUUID
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
		expect(parentData.uid, WidgetData.defaultProperties.toSortedMap().values.toString().toUUID())
		expect(emptyWidget.propertyConfigId, parentData.uid)
		expect(emptyWidget.uid, parentData.content().toUUID())
		expect(parentWidget.id.dumpString(),"e6c865df-7848-3219-ad41-1c1466b01686_32c40e3d-0896-365e-9888-5ceb374a9138")

		expect(testWidgetData.uid, UUID.fromString("b207f26f-96a4-31fa-9083-ad677c2b4931"))
		expect(testWidget.uid, Widget.computeId(testWidgetData))
		expect(testWidget.propertyConfigId, testWidgetData.uid)
		expect(testWidget.id, ConcreteId(Widget.computeId(testWidgetData),testWidgetData.uid))
		expect(testWidget.parentId, emptyWidget.id)
	}

	@Test
	fun widgetDumpTest(){
		expect(testWidget.dataString(";"), testWidgetDumpString)
		expect(Widget.fromString(testWidget.splittedDumpString(";")).dataString(";"),testWidget.dataString(";"))
	}
}