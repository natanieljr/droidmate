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

		val visible: Boolean = false
		) : Serializable{
	val uid: UUID = toString().toUUID()
	var xpath: String = ""
	var xpathHash: Int = 0
	var isLeaf: Boolean = false
	/** coordinate where only this element is triggered and no actable child
	 * even if this element is actable this uncoveredCoord may be null if the children are overlying the whole element bounds
	 */
	var uncoveredCoord: Pair<Int,Int>? = null
	var parentHash: Int = 0
	var childrenXpathHashes: List<Int> = emptyList()
	val actable: Boolean by lazy{ enabled && visible && (clickable || checked ?: false || longClickable || scrollable)}
	constructor(resId: String, xPath: String)
			: this("default",resourceId = resId){
		this.xpath = xPath
	}

	constructor(properties: MutableMap<P, Any?>) : this(
			properties[P.Text] as String,
			properties[P.Desc] as String,
			properties[P.ResId] as String,
			properties[P.Type] as String,
			properties[P.PackageName] as String,
			properties[P.Enabled] as Boolean,
			(properties[P.Type] as String).contains("Edit"), // fixme editable not yet persistated
			properties[P.IsPassword] as Boolean,
			properties[P.Clickable] as Boolean,
			properties[P.LongClickable] as Boolean,
			properties[P.Scrollable] as Boolean,
			properties[P.Checked] as Boolean?,
			properties[P.Focused] as Boolean?,
			properties[P.Selected] as Boolean,
			properties[P.BoundsX] as Int,
			properties[P.BoundsY] as Int,
			properties[P.BoundsWidth] as Int,
			properties[P.BoundsHeight] as Int,
			visible = properties[P.Visible] as Boolean
			){
		xpath = properties[P.XPath] as String
		isLeaf = properties[P.IsLeaf] as Boolean
	}

	fun content(): String = text + contentDesc

	override fun equals(other: Any?): Boolean {
		return super.equals(other) && xpathHash == (other as WidgetData).xpathHash
	}

	override fun hashCode(): Int {
		return super.hashCode()+xpathHash
	}

	companion object {
		@JvmStatic
		fun empty() = WidgetData(text = "EMPTY")
	}
}

enum class P(val pName: String = "", var header: String = "") {
	UID, WdId(header = "data UID"),
	Type(WidgetData::className.name, "widget class"),
	Interactive,
	Coord(WidgetData::uncoveredCoord.name),
	Text(WidgetData::text.name),
	Desc(WidgetData::contentDesc.name, "Description"),
	ParentID(header = "parentID"),
	Enabled(WidgetData::enabled.name),
	Visible(WidgetData::visible.name),
	Clickable(WidgetData::clickable.name),
	LongClickable(WidgetData::longClickable.name),
	Scrollable(WidgetData::scrollable.name),
	Checked(WidgetData::checked.name),
	Focused(WidgetData::focused.name),
	Selected(WidgetData::selected.name),
	IsPassword(WidgetData::isPassword.name),
	BoundsX(WidgetData::boundsX.name),
	BoundsY(WidgetData::boundsY.name),
	BoundsWidth(WidgetData::boundsWidth.name),
	BoundsHeight(WidgetData::boundsHeight.name),
	ResId(WidgetData::resourceId.name, "Resource Id"),
	XPath,
	IsLeaf(WidgetData::isLeaf.name),
	PackageName(WidgetData::packageName.name);

	init {
		if (header == "") header = name
	}

	companion object {
		@JvmStatic private val propertyValues = P.values().filter { it.pName != "" }
		fun propertyMap(line: List<String>): MutableMap<P, Any?> = propertyValues.map {
			(it to
					when (it) {
						Clickable, LongClickable, Scrollable, IsPassword, Enabled, Selected, Visible, IsLeaf -> line[it.ordinal].toBoolean()
						Focused, Checked -> flag(line[it.ordinal])
						Coord -> line[it.ordinal].let{ if(it=="null") null else with(it.split(",")){Pair(get(0).toInt(),get(1).toInt())}}
						BoundsX, BoundsY, BoundsWidth, BoundsHeight -> line[it.ordinal].toInt()
						else -> line[it.ordinal]  // Strings
					})
		}.toMap().toMutableMap()
	}
}
private val flag = { entry: String -> if (entry == "disabled") null else entry.toBoolean() }