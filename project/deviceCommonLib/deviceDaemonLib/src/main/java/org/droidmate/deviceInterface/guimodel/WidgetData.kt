package org.droidmate.deviceInterface.guimodel

import java.io.Serializable
import java.nio.charset.Charset
import java.util.*

fun String.toUUID(): UUID = UUID.nameUUIDFromBytes(trim().toByteArray(Charset.forName("UTF-8")))
fun Int.toUUID(): UUID = UUID.nameUUIDFromBytes(toString().toByteArray(Charset.forName("UTF-8")))
fun center(c:Int, d:Int):Int = c+(d/2)

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

	// Currently used only for Android'' native text edits: use xpath instead of text for re-identification
	// Text changes every time we input a new letter,
	// With the old metric re-identification is not possible
	fun content(): String = if (className == "android.widget.EditText")
					xpath
				else
					text + contentDesc

	val actable: Boolean by lazy{ enabled && visible && (clickable || checked ?: false || longClickable || scrollable)}
	var hasActableDescendant: Boolean = false

	companion object {
		@JvmStatic
		fun empty() = WidgetData(text = "EMPTY")

		@JvmStatic
		fun fromString(line: List<String>, indexMap: Map<P, Int> = P.defaultIndicies): WidgetData =
				WidgetData(text = line[P.Text.idx(indexMap)], clickable = line[P.Clickable.idx(indexMap)].toBoolean(), longClickable =
				line[P.LongClickable.idx(indexMap)].toBoolean(), scrollable = line[P.Scrollable.idx(indexMap)].toBoolean(),
						isPassword = line[P.IsPassword.idx(indexMap)].toBoolean(), enabled = line[P.Enabled.idx(indexMap)].toBoolean(),
						selected = line[P.Selected.idx(indexMap)].toBoolean(), visible = line[P.Visible.idx(indexMap)].toBoolean(), checked =
				flag(line[P.Checked.idx(indexMap)]), focused = flag(line[P.Focused.idx(indexMap)]), boundsX = line[P.BoundsX.idx(indexMap)].toInt(),
						boundsY = line[P.BoundsY.idx(indexMap)].toInt(), boundsWidth = line[P.BoundsWidth.idx(indexMap)].toInt(),
						boundsHeight = line[P.BoundsHeight.idx(indexMap)].toInt(), contentDesc = line[P.Desc.idx(indexMap)],
						resourceId = line[P.ResId.idx(indexMap)], packageName = line[P.PackageName.idx(indexMap)], className = line[P.Type.idx(indexMap)],
						isLeaf = line[P.IsLeaf.idx(indexMap)].toBoolean(), editable = line[P.Editable.idx(indexMap)].toBoolean()
				).apply {
					uncoveredCoord = line[P.Coord.idx(indexMap)].let{ if(it=="null") null else with(it.split(",")){ kotlin.Pair(get(0).toInt(), get(1).toInt()) }}
				}

	}
}

@Suppress("MemberVisibilityCanBePrivate")
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
	Editable,
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
	PackageName,
	ImgId;

	init {
		if (header == "") header = name
	}
	
	fun idx(indexMap:Map<P,Int> = defaultIndicies): Int = indexMap[this]!!
	companion object {
		@JvmStatic val defaultIndicies = P.values().associate { it to it.ordinal }
	}
}
private val flag = { entry: String -> if (entry == "disabled") null else entry.toBoolean() }