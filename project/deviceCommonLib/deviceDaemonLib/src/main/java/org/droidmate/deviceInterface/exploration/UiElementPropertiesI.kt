package org.droidmate.deviceInterface.exploration

import java.io.Serializable

/** this annotation is used by the exploration model to easily determine the order (by [ordinal]) and header names for properties to be persisted */
@Target( AnnotationTarget.PROPERTY) annotation class Persistent(val header: String, val ordinal: Int)

interface UiElementPropertiesI : Serializable {		//FIXME load/create properties for these properties
	val serialVersionUID: Long  // necessary for TCP communication, otherwise it would be computed by the class hash which may cause de-/serialization errors
		get() = 5205083142890068067

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

	/** REMARK: the bounds may lay outside of the screen boundaries, if the element is (partially) invisible */
	val boundsX: Int
	val boundsY: Int
	val boundsWidth: Int
	val boundsHeight: Int

	val visible: Boolean
	val xpath: String

	/** used internally to re-identify elements between device and pc (computed as hash code of the elements (customized) unique xpath) */
	val idHash: Int
	val parentHash: Int
	val childHashes: List<Int>

	val metaInfo: List<String>

}

