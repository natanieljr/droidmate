package org.droidmate.exploration.statemodel

import org.droidmate.exploration.statemodel.config.ConcreteId
import org.droidmate.exploration.statemodel.config.dumpString
import org.droidmate.exploration.statemodel.config.emptyId
import org.droidmate.uiautomator_daemon.WidgetData
import java.time.LocalDateTime

interface TestModel{
	val parentData: WidgetData get() = WidgetData.empty()
	val parentWidget: Widget get() = Widget(parentData)
	val testWidgetData: WidgetData
	val testWidget: Widget get() = Widget(testWidgetData).apply { parentId = parentWidget.id }
	val testWidgetDumpString: String

	class TestAction(targetWidget:Widget?=null, prevState:ConcreteId= emptyId, nextState:ConcreteId =emptyId, actionType:String = "TEST_ACTION")
		:ActionData(actionType, targetWidget, LocalDateTime.MIN, LocalDateTime.MIN, true, "test action", nextState, sep = ";"){
		init {
			super.prevState = prevState
		}
	}
}

class DefaultTestModel: TestModel {
	override val testWidgetData by lazy{ WidgetData(mutableMapOf(
			WidgetData::text.name to "text-mock",
			WidgetData::contentDesc.name to "description-mock",
			WidgetData::resourceId.name to "resourceId-mock",
			WidgetData::className.name to "class-mock",
			WidgetData::packageName.name to "package-mock",
			WidgetData::isPassword.name to false,
			WidgetData::enabled.name to true,
			WidgetData::clickable.name to true,
			WidgetData::longClickable.name to false,
			WidgetData::scrollable.name to false,
			WidgetData::checked.name to true,
			WidgetData::focused.name to null,
			WidgetData::selected.name to false,
			WidgetData::visible.name to true,
			WidgetData::isLeaf.name to true,
			WidgetData::boundsX.name to 11,
			WidgetData::boundsY.name to 136,
			WidgetData::boundsWidth.name to 81,
			WidgetData::boundsHeight.name to 51
	), index = 42, parent = parentData	)
	}

	// per default we don't want to re-generate the widgets on each access, therefore make them persistent values
	override val testWidget: Widget by lazy{ super.testWidget }
	override val parentWidget: Widget by lazy{ super.parentWidget }

	override val testWidgetDumpString = "5a3d425d-66bc-38d5-a375-07e0b682e0ba;${testWidgetData.uid};class-mock;true;text-mock;description-mock;${parentWidget.id.dumpString()};true;true;true;false;false;true;disabled;false;false;11;136;81;51;resourceId-mock;;true;package-mock"

}