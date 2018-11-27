package org.droidmate.deviceInterface.communication

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI

// REMARK: this data class needs to stay in the communication library as the serializer will otherwise throw class def not found
/** this is only supposed to be used internally in the device communication interface (in device driver and for serializer read) */
data class UiElementProperties(
		override val idHash: Int,
		override val text: String,
		override val contentDesc: String,
		override val resourceId: String,
		override val className: String,
		override val packageName: String,
		override val enabled: Boolean,
		override val isInputField: Boolean,
		override val isPassword: Boolean,
		override val clickable: Boolean,
		override val longClickable: Boolean,
		override val scrollable: Boolean,
		override val checked: Boolean?,
		override val focused: Boolean?,
		override val selected: Boolean,
		override val boundaries: Rectangle,
		override val definedAsVisible: Boolean,
		override val visibleAreas: List<Rectangle>,
		override val xpath: String,
		override val parentHash: Int,
		override val childHashes: List<Int> = emptyList(),
		override val isKeyboard: Boolean,
		override val metaInfo: List<String>,
		override val hasUncoveredArea: Boolean,
		override val visibleBounds: Rectangle,
		override val imgId: Int,
//		override val isInBackground: Boolean,
		val allSubAreas: List<Rectangle>,
		override val hintText: String,
		override val inputType: Int
) : UiElementPropertiesI {

//TODO cleanup once parsing is repaired
//	constructor(resId: String, xPath: String)
//			: this("default",resourceId = resId,xpath = xPath)

	// quick-fix for compatibility reasons with previous model-dumps
	private val idString by lazy{ toString().replaceAfter("_uid",")").replace(", _uid","") }
//	override val propertyId: UUID by lazy{ _uid ?: (if(idHash!=0) idString+idHash.toString() else idString).toUUID() }  //FIXME move to WidgetI


//		fun empty() = UiElementProperties(text = "EMPTY")

//		@JvmStatic
//		fun fromString(line: List<String>, indexMap: Map<P, Int> //= P.defaultIndicies
//		): UiElementProperties = TODO()
//				UiElementProperties(text = line[P.Text.idx(indexMap)], clickable = line[P.Clickable.idx(indexMap)].toBoolean(), longClickable =
//				line[P.LongClickable.idx(indexMap)].toBoolean(), scrollable = line[P.Scrollable.idx(indexMap)].toBoolean(),
//						isPassword = line[P.IsPassword.idx(indexMap)].toBoolean(), enabled = line[P.Enabled.idx(indexMap)].toBoolean(),
//						selected = line[P.Selected.idx(indexMap)].toBoolean(), definedAsVisible = line[P.Visible.idx(indexMap)].toBoolean(), checked =
//				flag(line[P.Checked.idx(indexMap)]), focused = flag(line[P.Focused.idx(indexMap)]), boundsX = line[P.BoundsX.idx(indexMap)].toInt(),
//						boundsY = line[P.BoundsY.idx(indexMap)].toInt(), boundsWidth = line[P.BoundsWidth.idx(indexMap)].toInt(),
//						boundsHeight = line[P.BoundsHeight.idx(indexMap)].toInt(), contentDesc = line[P.Desc.idx(indexMap)],
//						resourceId = line[P.ResId.idx(indexMap)], packageName = line[P.PackageName.idx(indexMap)], className = line[P.Type.idx(indexMap)],
////						isLeaf = line[P.IsLeaf.idx(indexMap)].toBoolean(), isInputField = line[P.Editable.idx(indexMap)].toBoolean(),
//						xpath = line[P.XPath.idx(indexMap)],
////						uncoveredCoord = line[P.Coord.idx(indexMap)].let{ if(it=="null") null else with(it.split(",")){ kotlin.Pair(get(0).toInt(), get(1).toInt()) }},
//						idHash = line[P.HashId.idx(indexMap)].let{ it.toInt() }
//				)

	companion object {
		// necessary for TCP communication, otherwise it would be computed by the class hash which may cause de-/serialization errors
		const val serialVersionUID: Long = 5205083142890068067//		@JvmStatic
	}

}


private val flag = { entry: String -> if (entry == "disabled") null else entry.toBoolean() }