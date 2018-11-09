//package org.droidmate.explorationModel
//
//import org.droidmate.deviceInterface.guimodel.UiElementProperties
//import org.droidmate.explorationModel.config.ConcreteId
//import org.droidmate.explorationModel.config.dumpString
//import org.droidmate.explorationModel.config.emptyId
//import java.time.LocalDateTime
//
//interface TestModel{
////	val parentData: UiElementProperties get() = UiElementProperties.empty()
////	val parentWidget: Widget get() = Widget(parentData)
//	val testWidgetData: UiElementProperties
////	val testWidget: Widget get() = Widget(testWidgetData).apply { parentId = parentWidget.id }
//	val testWidgetDumpString: String
//}
//
//typealias TestAction = Interaction
//@JvmOverloads fun createTestAction(targetWidget: Widget?=null, oldState: ConcreteId = emptyId, nextState: ConcreteId = emptyId, actionType:String = "TEST_ACTION"): TestAction
//		= Interaction(actionType, targetWidget, LocalDateTime.MIN, LocalDateTime.MIN, true, "test action", nextState, sep = ";").apply {
//	prevState = oldState
//}
///*
//TestProperties(
//		override val text: String,
//		override val contentDesc: String ="",
//		override val resourceId: String ="",
//		override val className: String ="",
//		override val packageName: String ="",
//		override val enabled: Boolean = false,
//		override val isInputField: Boolean = false,
//		override val isPassword: Boolean = false,
//		override val clickable: Boolean = false,
//		override val longClickable: Boolean = false,
//		override val scrollable: Boolean = false,
//		override val checked: Boolean? = null,
//		override val focused: Boolean? = null,
//		override val selected: Boolean = false,
//
//		/** important the bounds may lay outside of the screen bounderies, if the element is (partially) invisible */
//		override val boundsX: Int = 0,
//		override val boundsY: Int = 0,
//		override val boundsWidth: Int = 0,
//		override val boundsHeight: Int = 0,
//
//		override val definedAsVisible: Boolean = false,
//		private val _uid: UUID? = null,  // for copy/transform function only to transfer old pId values
//
//		override val xpath: String = "noPath",
//		override val parentHash: Int = 0,
//		override val childHashes: List<Int> = emptyList(), override val idHash: Int = 0, override val isKeyboard: Boolean, override val windowId: Boolean
//): UiElementProperties
//
// */
//class DefaultTestModel: TestModel {
////	override val testWidgetData by lazy{
////		UiElementProperties(text = "text-mock",
////				contentDesc = "description-mock",
////				resourceId = "resourceId-mock",
////				className = "class-mock",
////				packageName = "package-mock",
////				enabled = true,
////				clickable = true,
////				definedAsVisible = true,
////				boundsX = 11,
////				boundsY = 136,
////				boundsWidth = 81,
////				boundsHeight = 51,
////				isLeaf = true
////		)
////	}
//
//	// per default we don't want to re-generate the widgets on each access, therefore make them persistent values
//	override val testWidget: Widget by lazy{ super.testWidget }
//	override val parentWidget: Widget by lazy{ super.parentWidget }
//
//	override val testWidgetDumpString = "5a3d425d-66bc-38d5-a375-07e0b682e0ba;${testWidgetData.propertyId};class-mock;"+
//			"true;null;text-mock;description-mock;${parentWidget.id.dumpString()};true;true;true;false;false;disabled;"+
//			"false;disabled;false;false;11;136;81;51;resourceId-mock;;true;package-mock;null;false;0"
//}