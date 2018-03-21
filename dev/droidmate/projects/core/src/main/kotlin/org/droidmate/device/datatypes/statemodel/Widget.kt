// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.device.datatypes.statemodel

import kotlinx.coroutines.experimental.launch
import org.droidmate.device.datatypes.InvalidWidgetBoundsException
import org.droidmate.exploration.actions.Direction
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.Charset
import java.util.*
import javax.imageio.ImageIO
import kotlin.reflect.full.declaredMemberProperties


/** always prefer directly creating the UUID from an byte array as it is more than factor 20 faster */
fun String.toUUID(): UUID = UUID.nameUUIDFromBytes(trim().toByteArray(Charset.forName("UTF-8")))

@Suppress("unused")
/**
 * @param index only used during dumpParsing to create xPath !! should not be used otherwise !!
 * @param parent only used during dumpParsing to create xPath and to determine the Widget.parentId within the state model !! should not be used otherwise !!
 */
class WidgetData(map: Map<String,Any?>,val index: Int = -1,val parent: WidgetData? = null){
	constructor(resId: String, xPath:String)
			:this(defaultProperties.toMutableMap().apply { replace(WidgetData::resourceId.name,resId)	}){ this.xpath = xPath }

	val uid = map.values.toString().toUUID()
	val text: String by map
	val contentDesc: String by map
	val resourceId: String by map
	val className: String by map
	val packageName: String by map
	val isPassword: Boolean by map
	val enabled: Boolean by map
	val clickable: Boolean by map
	val longClickable: Boolean by map
	val scrollable: Boolean by map
	val checked: Boolean? by map
	val focused: Boolean? by map
	val selected: Boolean by map
	val bounds: Rectangle by map
	val visible: Boolean by map
	val isLeaf:Boolean by map
	var xpath:String = ""

	@Deprecated("use the new UUID from state model instead")
	val id: String by map.withDefault { "" } // only used for testing
	fun content():String = text + contentDesc


	fun canBeActedUpon(): Boolean = enabled && visible && (clickable || checked?:false || longClickable || scrollable)

	operator fun<R,T> getValue(thisRef: R, p: kotlin.reflect.KProperty<*>): T {  // remark do not use delegate in performance essential code (10% overhead) and prefer Type specialized Delegates as otherwise Boxing and Unboxing overhead occurs for primitive types
		@Suppress("UNCHECKED_CAST")
		return this::class.declaredMemberProperties.find { it.name==p.name }?.call(this) as T
	}


	companion object {
		@JvmStatic val defaultProperties by lazy { P.propertyMap(Array(P.values().size, { "false" }).toList()) }
		@JvmStatic fun empty() = WidgetData(defaultProperties)

		@JvmStatic
		fun parseBounds(bounds: String): Rectangle {
			assert(bounds.isNotEmpty())

			// The input is of form "[xLow,yLow][xHigh,yHigh]" and the regex below will capture four groups: xLow yLow xHigh yHigh
			val boundsMatcher = Regex("\\[(-?\\p{Digit}+),(-?\\p{Digit}+)\\]\\[(-?\\p{Digit}+),(-?\\p{Digit}+)\\]")
			val foundResults = boundsMatcher.findAll(bounds).toList()
			if (foundResults.isEmpty())
				throw InvalidWidgetBoundsException("The window hierarchy bounds matcher was unable to match $bounds against the regex")

			val matchedGroups = foundResults[0].groups

			val lowX = matchedGroups[1]!!.value.toInt()
			val lowY = matchedGroups[2]!!.value.toInt()
			val highX = matchedGroups[3]!!.value.toInt()
			val highY = matchedGroups[4]!!.value.toInt()

			return Rectangle(lowX, lowY, highX - lowX, highY - lowY)
		}
	}
}

private enum class P(val pName:String="",var header: String="") {
	UID,  WdId(header="data UID"), Type(WidgetData::className.name,"widget class"), Interactive, Text(WidgetData::text.name),
	Desc(WidgetData::contentDesc.name,"Description"), 	ParentUID(header = "parentID"), Enabled(WidgetData::enabled.name), Visible(WidgetData::visible.name),
	Clickable(WidgetData::clickable.name), LongClickable(WidgetData::longClickable.name), Scrollable(WidgetData::scrollable.name),
	Checkable(WidgetData::checked.name), Focusable(WidgetData::focused.name), Selected(WidgetData::selected.name), IsPassword(WidgetData::isPassword.name),
	Bounds(WidgetData::bounds.name), ResId(WidgetData::resourceId.name,"Resource Id"), XPath, IsLeaf(WidgetData::isLeaf.name), PackageName(WidgetData::packageName.name) ;
	init{ if(header=="") header=name }
	companion object {
		val propertyValues = P.values().filter { it.pName != "" }
		fun propertyMap(line: List<String>): Map<String, Any?> = propertyValues.map {
			(it.pName to
					when (it) {
						Clickable, LongClickable, Scrollable, IsPassword, Enabled, Selected, Visible, IsLeaf -> line[it.ordinal].toBoolean()
						Checkable, Focusable -> flag(line[it.ordinal])
						Bounds -> rectFromString(line[it.ordinal])
						else -> line[it.ordinal]  // Strings
					})
		}.toMap()
	}
}


@Suppress("MemberVisibilityCanBePrivate")
class Widget(private val properties: WidgetData, var _uid: Lazy<UUID>) {

	constructor(properties: WidgetData = WidgetData.empty()): this(properties, lazy({ computeId(properties) }))

	var uid: UUID
		set(value) { _uid = lazyOf(value)}
		get() {
			return /*debugT("get UID",{ */			_uid.value
//			})
		}

//	init {
//		launch { _uid.value } // id computation takes time and we don't want to block the main (exploration) thread for it just in case the widget image dumping is ever deactivated
//	}

	/** A widget mainly consists of two parts, [uid] encompasses the identifying one [image,Text,Description] used for unique identification
	 * and the modifiable properties, like checked, focused etc. identified via [propertyConfigId] */
	val propertyConfigId:UUID = properties.uid

	val text: String =  properties.text
	val contentDesc: String = properties.contentDesc
	val resourceId: String = properties.resourceId
	val className: String = properties.className
	val packageName: String = properties.packageName
	val isPassword: Boolean = properties.isPassword
	val enabled: Boolean = properties.enabled
	val visible: Boolean = properties.visible
	val clickable: Boolean = properties.clickable
	val longClickable: Boolean = properties.longClickable
	val scrollable: Boolean = properties.scrollable
	val checked: Boolean? = properties.checked
	val focused: Boolean? = properties.focused
	val selected: Boolean = properties.selected
	val bounds: Rectangle = properties.bounds
	val xpath: String = properties.xpath
	var parentId:Pair<UUID,UUID>? = null
	val isLeaf: Boolean = properties.isLeaf
	internal val parentXpath:String = properties.parent?.xpath?:""
	//FIXME we need image similarity otherwise even sleigh changes like additional boarders/highlighting will screw up the imgId
	//FIXME buggy in amazon "Sign In" does not always compute to same id
	// if we don't have any text content we use the image, otherwise use the text for unique identification
//	var uid: UUID = (text + contentDesc).let{debugT("gen UID ${text + contentDesc}",{ if(hasContent()) it.toUUID() else  imgId }) }
	// special care for EditText elements, as the input text will change the [text] property
	// we keep track of the field via state-context + xpath once we interacted with it
	// however that means uid would have to become a var and we rewrite it after the state is parsed (ignoring EditField.text) and lookup the initial uid for this widget in the initial state-context
	// need model access => do this after widget generation before StateData instantiation => state.uid compute state candidates by ignoring all EditFields => determine exact one by computing ref.uid when ignoring EditFields whose xPath is a target widget within the trace + proper constructor
	// and compute the state.uid with the original editText contents
	// => model keep track of interacted EditField xPaths
	// => stateData compute function getting set of xPaths to be ignored if EditField
	// => stateData val idWithoutEditFields

	val id by lazy{ Pair(uid,propertyConfigId) }


	val isEdit:Boolean = className.toLowerCase().contains("edit")

	fun hasContent():Boolean = (text + contentDesc) != ""

	/** helper functions for parsing */
	fun Rectangle.dataString():String{
		return "x=$x, y=$y, width=$width, height=$height"
	}
	val dataString by lazy {
		P.values().joinToString(separator = sep) { p ->
			when (p) {
				P.UID -> uid.toString()
				P.Type -> className
				P.Interactive -> canBeActedUpon().toString()
				P.Text -> text
				P.Desc -> contentDesc
				P.Clickable -> clickable.toString()
				P.Scrollable -> scrollable.toString()
				P.Checkable -> checked?.toString() ?: "disabled"
				P.Focusable -> focused?.toString() ?: "disabled"
				P.Bounds -> bounds.dataString()
				P.ResId -> resourceId
				P.XPath -> xpath
				P.WdId -> propertyConfigId.toString()
				P.ParentUID -> parentId?.toString()?:"null"
				P.Enabled -> enabled.toString()
				P.LongClickable -> longClickable.toString()
				P.Selected -> selected.toString()
				P.IsPassword -> isPassword.toString()
				P.Visible -> visible.toString()
				P.IsLeaf -> isLeaf.toString()
				P.PackageName -> packageName
			}
		}
	}

	private val simpleClassName by lazy { className.substring(className.lastIndexOf(".") + 1) }
	fun center(): Point = Point(bounds.centerX.toInt(), bounds.centerY.toInt())
	fun getStrippedResourceId(): String = properties.resourceId.removePrefix("$packageName:")
	fun toShortString(): String{
		return "Wdgt:$simpleClassName/\"$text\"/\"$resourceId\"/[${bounds.centerX.toInt()},${bounds.centerY.toInt()}]"
	}
	fun toTabulatedString(includeClassName: Boolean = true): String{
		val pCls = simpleClassName.padEnd(20, ' ')
		val pResId = resourceId.padEnd(64, ' ')
		val pText = text.padEnd(40, ' ')
		val pContDesc = contentDesc.padEnd(40, ' ')
		val px = "${bounds.centerX.toInt()}".padStart(4, ' ')
		val py = "${bounds.centerY.toInt()}".padStart(4, ' ')

		val clsPart = if (includeClassName) "Wdgt: $pCls / " else ""

		return "${clsPart}resourceId: $pResId / text: $pText / contDesc: $pContDesc / click xy: [$px,$py]"
	}
	fun canBeActedUpon(): Boolean = properties.canBeActedUpon()

	@Deprecated(" no longer used? ")
	fun getClickPoint(deviceDisplayBounds:Rectangle?): Point{
		if (deviceDisplayBounds == null) {
			val center = this.center()
			return Point(center.x, center.y)
		}

		assert(bounds.intersects(deviceDisplayBounds))

		val clickRectangle = bounds.intersection(deviceDisplayBounds)
		return Point(clickRectangle.centerX.toInt(), clickRectangle.centerY.toInt())
	}
	@Deprecated(" no longer used? ")
	fun getAreaSize(): Double{
		return bounds.height.toDouble() * bounds.width
	}
	@Deprecated(" no longer used? ")
	fun getDeviceAreaSize(deviceDisplayBounds:Rectangle?): Double{
		return if (deviceDisplayBounds != null)
			deviceDisplayBounds.height.toDouble() * deviceDisplayBounds.width
		else
			-1.0
	}
	@Deprecated(" no longer used? ")
	fun getResourceIdName(): String = resourceId.removeSuffix(this.packageName + ":uid/")
	@Deprecated(" no longer used? ")
	fun getSwipePoints(direction: Direction, percent: Double,deviceDisplayBounds:Rectangle?): List<Point>{

		assert(bounds.intersects(deviceDisplayBounds))

		val swipeRectangle = bounds.intersection(deviceDisplayBounds)
		val offsetHor = (swipeRectangle.getWidth() * (1 - percent)) / 2
		val offsetVert = (swipeRectangle.getHeight() * (1 - percent)) / 2

		return when (direction) {
			Direction.LEFT -> arrayListOf(Point((swipeRectangle.maxX - offsetHor).toInt(), swipeRectangle.centerY.toInt()),
					Point((swipeRectangle.minX + offsetHor).toInt(), swipeRectangle.centerY.toInt()))
			Direction.RIGHT -> arrayListOf(Point((this.bounds.minX + offsetHor).toInt(), this.bounds.centerY.toInt()),
					Point((this.bounds.maxX - offsetHor).toInt(), this.bounds.centerY.toInt()))
			Direction.UP -> arrayListOf(Point(this.bounds.centerX.toInt(), (this.bounds.maxY - offsetVert).toInt()),
					Point(this.bounds.centerX.toInt(), (this.bounds.minY + offsetVert).toInt()))
			Direction.DOWN -> arrayListOf(Point(this.bounds.centerX.toInt(), (this.bounds.minY + offsetVert).toInt()),
					Point(this.bounds.centerX.toInt(), (this.bounds.maxY - offsetVert).toInt()))
		}
	}

	/*************************/
	companion object {
		/** widget creation */
		@JvmStatic fun fromString(line:List<String>): Widget {
			WidgetData(P.propertyMap(line)).apply { xpath = line[P.XPath.ordinal] }.let { w ->
				return Widget(w, lazyOf(UUID.fromString(line[P.UID.ordinal])))
						.apply { parentId = line[P.UID.ordinal].let { if (it == "null") null else stateIdFromString(it) } }
			}
		}

		/** compute the pair of (widget.uid,widget.imgId), if [isCut] is true we assume the screenImage already matches the widget.bounds */
		@JvmStatic fun computeId(w: WidgetData, screenImg: BufferedImage?=null, isCut: Boolean=false):UUID	=
				w.content().let {
					if (it != "") it.toUUID()  // compute id from textual content if there is any
					else screenImg?.let {
						if(isCut) idOfImgCut(screenImg)
						else idOfImgCut(it.getSubimage(w.bounds))
					}	?: emptyUUID // no text content => compute id from img
				}

		@JvmStatic private fun idOfImgCut(image: BufferedImage):UUID =
//			debugT("compute img id raster elements", { // seams to work
				(image.raster.getDataElements(0, 0, image.width, image.height, null) as ByteArray)
//
//			})
			 // debugT("compute img id dataBuffer", {  //seams to be much faster but not properly unique
//				(image.raster.dataBuffer as DataBufferByte).data
//			})
					.let { UUID.nameUUIDFromBytes(it) }


		@JvmStatic suspend fun fromWidgetData(w: WidgetData, screenImg: BufferedImage?, config: ModelDumpConfig): Widget {
			screenImg?.getSubimage(w.bounds).let { wImg ->
//				debugT("compute id ${w.bounds}",{
//				val t = async{ computeId(w, wImg,true) }
					lazy{
//						runBlocking { t.await() }
						computeId(w, wImg, true)
					}
//				})
						.let{ widgetId ->
							launch{ widgetId.value }  // issue initialization in parallel
							// print the screen img if there is one
							if(wImg!=null	&& w.content()=="") launch {
								File(config.widgetImgPath(id = widgetId.value,postfix = "_${w.uid}", interactive = w.canBeActedUpon())).let{
									if(!it.exists()) ImageIO.write(wImg,"png", it)
								}
							}
							return /* debugT("create to Widget ", { */ Widget(w, widgetId)
//							})
						}
			}
		}

		@JvmStatic val idIdx by lazy { P.UID.ordinal }
		@JvmStatic val widgetHeader by lazy { P.values().joinToString(separator = sep) { it.header } }

		@JvmStatic private fun BufferedImage.getSubimage(r:Rectangle) = this.getSubimage(r.x,r.y,r.width,r.height)
	}
	/*** overwritten functions ***/
	override fun equals(other: Any?): Boolean {
		return when(other){
			is Widget -> uid == other.uid && propertyConfigId == other.propertyConfigId
			else -> false
		}
	}

	override fun hashCode(): Int {
		return uid.hashCode()+propertyConfigId.hashCode()
	}

	override fun toString(): String {
		return "${uid}_$propertyConfigId:$simpleClassName[text=$text; contentDesc=$contentDesc, resourceId=$resourceId, pos=${bounds.location}:dx=${bounds.width},dy=${bounds.height}]"
	}
	/* end override */
}

private val emptyRectangle by lazy{ Rectangle(0,0,0,0)}
//FIXME we would like image similarity for images with same text,desc,id,similar_size
private val flag={entry:String ->if(entry=="disabled") null else entry.toBoolean()}
private fun rectFromString(s:String): Rectangle {  //         return "x=$x, y=$y, width=$width, height=$height"
	if (s.isEmpty()|| !s.contains("=")) return emptyRectangle
	fun String.value(delimiter:String="="):Int { return this.split(delimiter)[1].toInt() }
	return s.split(", ").let {
		Rectangle(it[0].value(),it[1].value(),it[2].value(),it[3].value())
	}
}