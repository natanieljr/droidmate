package org.droidmate.uiautomator_daemon.guimodel

import java.io.Serializable
import java.nio.charset.Charset
import java.util.*

fun String.toUUID(): UUID = UUID.nameUUIDFromBytes(trim().toByteArray(Charset.forName("UTF-8")))
fun Int.toUUID(): UUID = UUID.nameUUIDFromBytes(toString().toByteArray(Charset.forName("UTF-8")))

data class WidgetData(
		val text: String,
		val contentDesc: String ="",
		val resourceId: String ="",
		val className: String ="",
		val packageName: String ="",
		val enabled: Boolean = false,
		val editable: Boolean = false,
		val isPassword: Boolean = false,
		val clickable: Boolean = false,
		val longClickable: Boolean = false,
		val scrollable: Boolean = false,
		val checked: Boolean? = null,
		val focused: Boolean? = null,
		val selected: Boolean = false,

		/** important the bounds may lay outside of the screen bounderies, if the element is (partially) invisible */
		val boundsX: Int = 0,
		val boundsY: Int = 0,
		val boundsWidth: Int = 0,
		val boundsHeight: Int = 0,

		val isLeaf: Boolean = false,
		val visible: Boolean = false
) : Serializable{

	constructor(resId: String, xPath: String)
			: this("default",resourceId = resId){
		this.xpath = xPath
	}

	val uid: UUID = toString().toUUID()
	var xpath: String = ""
	var idHash: Int = 0
	/** coordinate where only this element is triggered and no actable child
	 * even if this element is actable this uncoveredCoord may be null if the children are overlying the whole element bounds
	 */
	var uncoveredCoord: Pair<Int,Int>? = null
	var parentHash: Int = 0
	var childrenXpathHashes: List<Int> = emptyList()
	fun content(): String = text + contentDesc

	val actable: Boolean by lazy{ enabled && visible && (clickable || checked ?: false || longClickable || scrollable)}
	var hasActableDescendant: Boolean = false

	companion object {
		@JvmStatic
		fun empty() = WidgetData(text = "EMPTY")

		@JvmStatic
		fun fromString(line: List<String>):WidgetData =
				WidgetData( text = line[P.Text.ordinal], clickable = line[P.Clickable.ordinal].toBoolean(), longClickable =
				line[P.LongClickable.ordinal].toBoolean(), scrollable = line[P.Scrollable.ordinal].toBoolean(),
						isPassword = line[P.IsPassword.ordinal].toBoolean(), enabled = line[P.Enabled.ordinal].toBoolean(),
						selected = line[P.Selected.ordinal].toBoolean(), visible = line[P.Visible.ordinal].toBoolean(), 	checked =
				flag(line[P.Checked.ordinal]),	focused = flag(line[P.Focused.ordinal]), boundsX = line[P.BoundsX.ordinal].toInt(),
						boundsY = line[P.BoundsY.ordinal].toInt(), boundsWidth = line[P.BoundsWidth.ordinal].toInt(),
						boundsHeight = line[P.BoundsHeight.ordinal].toInt(),contentDesc = line[P.Desc.ordinal],
						resourceId = line[P.ResId.ordinal], packageName = line[P.PackageName.ordinal], className = line[P.Type.ordinal],
						isLeaf = line[P.IsLeaf.ordinal].toBoolean()
				).apply {
					uncoveredCoord = line[P.Coord.ordinal].let{ if(it=="null") null else with(it.split(",")){ kotlin.Pair(get(0).toInt(), get(1).toInt()) }}
				}

	}
}

enum class P(var header: String = "") {
	UID, WdId(header = "data UID"),
	Type("widget class"),
	Interactive,
	Coord,
	Text,
	Desc("Description"),
	ParentID(header = "parentID"),
	Enabled,
	Visible,
	Clickable,
	LongClickable,
	Scrollable,
	Checked,
	Focused,
	Selected,
	IsPassword,
	BoundsX,
	BoundsY,
	BoundsWidth,
	BoundsHeight,
	ResId("Resource Id"),
	XPath,
	IsLeaf,
	PackageName;

	init {
		if (header == "") header = name
	}
}
private val flag = { entry: String -> if (entry == "disabled") null else entry.toBoolean() }