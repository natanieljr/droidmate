package org.droidmate.deviceInterface.exploration

import java.io.Serializable

/** this annotation is used by the exploration model to easily determine the order (by [ordinal]) and header names for properties to be persisted */
@Target( AnnotationTarget.PROPERTY) annotation class Persistent(val header: String, val ordinal: Int)

interface UiElementPropertiesI : Serializable {		//FIXME load/create properties for these properties

	fun copy(): UiElementPropertiesI {
		TODO("if necessary should be implemented by instantiating class")
	}

	val isKeyboard: Boolean
	val windowId: Int

	@property:Persistent("Displayed Text", 5)
	val text: String
	@property:Persistent("Alternative Text", 6)
	val contentDesc: String
	@property:Persistent("Checkable", 10)
	val checked: Boolean?
	val resourceId: String
	@property:Persistent("UI Class", 2)
	val className: String
	val packageName: String
	val enabled: Boolean
	val isInputField: Boolean
	val isPassword: Boolean
	val clickable: Boolean
	val longClickable: Boolean
	val scrollable: Boolean
	val focused: Boolean?
	val selected: Boolean

	val visible: Boolean
	/** REMARK: the boundaries may lay outside of the screen boundaries, if the element is (partially) invisible.
	 * This is necessary to compute potential scroll operations to navigate to this element (get it into the visible area) */
	val boundaries: Rectangle
	/** window and UiElement overlays are analyzed to determine if this element is accessible (partially on top)
	 * ore hidden behind other elements (like menu bars).
	 * If [hasUncoveredArea] is true these boundaries are uniquely covered by this UI element otherwise it may contain visible child coordinates
	 */
	@property:Persistent("Visible Area", 20)
	val visibleBoundaries: List<Rectangle>
	@property:Persistent("Covers Unique Area", 19)
	val hasUncoveredArea: Boolean
	val xpath: String

	/** used internally to re-identify elements between device and pc
	 * (computed as hash code of the elements (customized by +windowId) unique xpath) */
	val idHash: Int // internally computed in UiElementProperties -> does not have to be persisted necessarily
	val parentHash: Int
	val childHashes: List<Int>

	val metaInfo: List<String>

	companion object {
		// necessary for TCP communication, otherwise it would be computed by the class hash which may cause de-/serialization errors
		const val serialVersionUID: Long = 5205083142890068067//		@JvmStatic
	}

}

