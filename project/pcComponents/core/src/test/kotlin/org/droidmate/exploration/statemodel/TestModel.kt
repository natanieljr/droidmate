package org.droidmate.exploration.statemodel

import org.droidmate.deviceInterface.guimodel.WidgetData
import java.time.LocalDateTime

interface TestModel{
	val parentData: WidgetData get() = WidgetData.empty()
	val parentWidget: Widget get() = Widget(parentData)
	val testWidgetData: WidgetData
	val testWidget: Widget get() = Widget(testWidgetData).apply { parentId = parentWidget.id }
	val testWidgetDumpString: String
}

typealias TestAction = ActionData
@JvmOverloads fun createTestAction(targetWidget:Widget?=null, oldState: ConcreteId = emptyId, nextState: ConcreteId = emptyId, actionType:String = "TEST_ACTION"):	TestAction
		= ActionData(actionType, targetWidget, LocalDateTime.MIN, LocalDateTime.MIN, true, "test action", nextState, sep = ";").apply {
	prevState = oldState
}

class DefaultTestModel: TestModel {
	override val testWidgetData by lazy{
		WidgetData(text = "text-mock",
				contentDesc = "description-mock",
				resourceId = "resourceId-mock",
				className = "class-mock",
				packageName = "package-mock",
				enabled = true,
				clickable = true,
				visible = true,
				boundsX = 11,
				boundsY = 136,
				boundsWidth = 81,
				boundsHeight = 51,
				isLeaf = true
		)
	}

	// per default we don't want to re-generate the widgets on each access, therefore make them persistent values
	override val testWidget: Widget by lazy{ super.testWidget }
	override val parentWidget: Widget by lazy{ super.parentWidget }

	override val testWidgetDumpString = "5a3d425d-66bc-38d5-a375-07e0b682e0ba;${testWidgetData.pId};class-mock;"+
			"true;null;text-mock;description-mock;${parentWidget.id.dumpString()};true;true;true;false;false;disabled;"+
			"false;disabled;false;false;11;136;81;51;resourceId-mock;;true;package-mock;null;false;0"
}