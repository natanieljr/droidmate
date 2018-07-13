//// DroidMate, an automated execution generator for Android apps.
//// Copyright (C) 2012-2018 Konrad Jamrozik
////
//// This program is free software: you can redistribute it and/or modify
//// it under the terms of the GNU General Public License as published by
//// the Free Software Foundation, either version 3 of the License, or
//// (at your option) any later version.
////
//// This program is distributed in the hope that it will be useful,
//// but WITHOUT ANY WARRANTY; without even the implied warranty of
//// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//// GNU General Public License for more details.
////
//// You should have received a copy of the GNU General Public License
//// along with this program.  If not, see <http://www.gnu.org/licenses/>.
////
//// email: jamrozik@st.cs.uni-saarland.de
//// web: www.droidmate.org
//package org.droidmate.uiautomator_daemon
//
//import org.slf4j.LoggerFactory
//import java.io.Serializable
//import java.nio.charset.Charset
//import java.util.*
//import kotlin.reflect.full.declaredMemberProperties
//
///** always prefer directly creating the UUID from an byte array as it is more than factor 20 faster */
//fun String.toUUID(): UUID = UUID.nameUUIDFromBytes(trim().toByteArray(Charset.forName("UTF-8")))
//
///**
// * @param index only used during dumpParsing to create xPath !! should not be used otherwise !!
// * @param parent only used during dumpParsing to create xPath and to determine the Widget.parentId within the state model !! should not be used otherwise !!
// */
//class WidgetDataOld @JvmOverloads constructor(map: MutableMap<String, Any?>, val index: Int = -1, val parent: WidgetData? = null) : Serializable {
//	init{
//		map.keys.forEach { key ->
//			if (map[key] is String)
//				map.replaceKt(key, sanitize(map[key] as String).trim())
//		}
//	}
//
//	constructor(resId: String, xPath: String)
//			: this(defaultProperties.toMutableMap().apply { replaceKt(WidgetData::resourceId.name, resId) }) {
//		this.xpath = xPath
//	}
//
//	val uid: UUID = map.toSortedMap().values.toString().toUUID()
//	val text: String by map
//	val contentDesc: String by map
//	val resourceId: String by map
//	val className: String by map
//	val packageName: String by map
//	val isPassword: Boolean by map
//	val enabled: Boolean by map
//	val clickable: Boolean by map
//	val longClickable: Boolean by map
//	val scrollable: Boolean by map
//	val checked: Boolean? by map
//	val focused: Boolean? by map
//	val selected: Boolean by map
//
//	val boundsX: Int by map
//	val boundsY: Int by map
//	val boundsWidth: Int by map
//	val boundsHeight: Int by map
//
//	val visible: Boolean by map
//	val isLeaf: Boolean by map
//	var xpath: String = ""
//
//	fun content(): String = text + contentDesc
//
//	fun canBeActedUpon(): Boolean = enabled && visible && (clickable || checked ?: false || longClickable || scrollable)
//
//	operator fun <R, T> getValue(thisRef: R, p: kotlin.reflect.KProperty<*>): T {  // remark do not use delegate in performance essential code (10% overhead) and prefer Type specialized Delegates as otherwise Boxing and Unboxing overhead occurs for primitive types
//		@Suppress("UNCHECKED_CAST")
//		return this::class.declaredMemberProperties.find { it.name == p.name }?.call(this) as T
//	}
//
//	companion object {
//		@JvmStatic
//		private val log by lazy { LoggerFactory.getLogger(DeviceResponse::class.java) }
//
//		@JvmStatic
//		private fun sanitize(value: String): String{
//			return value
//					.replace(";", "<semicolon>")
//					.replace("\n", "<newline>")
//		}
//
//		@JvmStatic
//		val defaultProperties by lazy {
//			P.propertyMap(
//					Array(P.values().size, { "false" }).apply{
//						this[P.BoundsX.ordinal] = "0"
//						this[P.BoundsY.ordinal] = "0"
//						this[P.BoundsWidth.ordinal] = "0"
//						this[P.BoundsHeight.ordinal] = "0"
//					}.toList())
//		}
//
//		@JvmStatic
//		fun empty() = WidgetData(defaultProperties.toMutableMap())
//
//		@JvmStatic
//		fun parseBounds(bounds: String): List<Int> {
//			assert(bounds.isNotEmpty())
//
//			// The input is of form "[xLow,yLow][xHigh,yHigh]" and the regex below will capture four groups: xLow yLow xHigh yHigh
//			val boundsMatcher = Regex("\\[(-?\\p{Digit}+),(-?\\p{Digit}+)\\]\\[(-?\\p{Digit}+),(-?\\p{Digit}+)\\]")
//			val foundResults = boundsMatcher.findAll(bounds).toList()
//			if (foundResults.isEmpty()) {
//				log.warn("The window hierarchy bounds matcher was unable to match $bounds against the regex")
//				return listOf(0, 0, 0, 0)
//			}
//
//			val matchedGroups = foundResults[0].groups
//
//			val lowX = matchedGroups[1]!!.value.toInt()
//			val lowY = matchedGroups[2]!!.value.toInt()
//			val highX = matchedGroups[3]!!.value.toInt()
//			val highY = matchedGroups[4]!!.value.toInt()
//
//			return listOf(lowX, lowY, highX - lowX, highY - lowY)
//		}
//
//		@JvmStatic
//		private fun <K, V> MutableMap<K,V>.replaceKt(key: K, value: V) {
//			if (this[key] != null || this.containsKey(key)) {
//				this.put(key, value)
//			}
//		}
//	}
//}
//
//
//enum class P(val pName: String = "", var header: String = "") {
//	UID, WdId(header = "data UID"),
//	Type(WidgetData::className.name, "widget class"),
//	Interactive,
//	Text(WidgetData::text.name),
//	Desc(WidgetData::contentDesc.name, "Description"),
//	ParentID(header = "parentID"),
//	Enabled(WidgetData::enabled.name),
//	Visible(WidgetData::visible.name),
//	Clickable(WidgetData::clickable.name),
//	LongClickable(WidgetData::longClickable.name),
//	Scrollable(WidgetData::scrollable.name),
//	Checked(WidgetData::checked.name),
//	Focused(WidgetData::focused.name),
//	Selected(WidgetData::selected.name),
//	IsPassword(WidgetData::isPassword.name),
//	BoundsX(WidgetData::boundsX.name),
//	BoundsY(WidgetData::boundsY.name),
//	BoundsWidth(WidgetData::boundsWidth.name),
//	BoundsHeight(WidgetData::boundsHeight.name),
//	ResId(WidgetData::resourceId.name, "Resource Id"),
//	XPath,
//	IsLeaf(WidgetData::isLeaf.name),
//	PackageName(WidgetData::packageName.name);
//
//	init {
//		if (header == "") header = name
//	}
//
//	companion object {
//		@JvmStatic private val propertyValues = P.values().filter { it.pName != "" }
//		fun propertyMap(line: List<String>): MutableMap<String, Any?> = propertyValues.map {
//			(it.pName to
//					when (it) {
//						Clickable, LongClickable, Scrollable, IsPassword, Enabled, Selected, Visible, IsLeaf -> line[it.ordinal].toBoolean()
//						Focused, Checked -> flag(line[it.ordinal])
//						BoundsX, BoundsY, BoundsWidth, BoundsHeight -> line[it.ordinal].toInt()
//						else -> line[it.ordinal]  // Strings
//					})
//		}.toMap().toMutableMap()
//	}
//}
//
////TODO we would like image similarity for images with same text,desc,id,similar_size
//private val flag = { entry: String -> if (entry == "disabled") null else entry.toBoolean() }
