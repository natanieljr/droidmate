// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
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
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.launch
import org.droidmate.configuration.ConfigProperties.ModelProperties
import org.droidmate.deviceInterface.guimodel.P
import org.droidmate.deviceInterface.guimodel.WidgetData
import org.droidmate.deviceInterface.guimodel.toUUID
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

/**
 * @param uidImgId this lazy value was introduced for performance optimization as the uid computation can be very expensive. It is either already known (initialized) or there is a co-routine running to compute the Widget.uid
 */
@Suppress("MemberVisibilityCanBePrivate")
class Widget(properties: WidgetData, val uidImgId: Lazy<Pair<UUID,UUID?>>) {

	constructor(_uid:Lazy<UUID>,properties: WidgetData): this(properties, lazyOf(Pair(_uid.value,null)))
	constructor(properties: WidgetData = WidgetData.empty()) : this(properties, lazy { computeId(properties) })

	val uid: UUID get() = uidImgId.value.first

//	init {
//		launch { _uid.value } // id computation takes time and we don't want to block the main (exploration) thread for it just in case the widget image dumping is ever deactivated
//	}

	/** A widget mainly consists of two parts, [uid] encompasses the identifying one [image,Text,Description] used for unique identification
	 * and the modifiable properties, like checked, focused etc. identified via [propertyId] */
	val propertyId: UUID = properties.uid

	val text: String = properties.text
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
	val bounds: Rectangle = Rectangle(properties.boundsX, properties.boundsY, properties.boundsWidth, properties.boundsHeight)
	val xpath: String = properties.xpath
	var parentId: Pair<UUID, UUID>? = null
	val isLeaf: Boolean = properties.isLeaf

	val uncoveredCoord: Pair<Int, Int>? = properties.uncoveredCoord
	val hasActableDescendant: Boolean = properties.hasActableDescendant
	//TODO we need image similarity otherwise even sleigh changes like additional boarders/highlighting will screw up the imgId
	//TODO check if still buggy in amazon "Sign In" does not always compute to same id
	// if we don't have any text content we use the image, otherwise use the text for unique identification
//	var uid: UUID = (text + contentDesc).let{debugT("gen UID ${text + contentDesc}",{ if(hasContent()) it.toUUID() else  imgId }) }
	// special care for EditText elements, as the input text will change the [text] property
	// we keep track of the field via state-eContext + xpath once we interacted with it
	// however that means uid would have to become a var and we rewrite it after the state is parsed (ignoring EditField.text) and lookup the initial uid for this widget in the initial state-eContext
	// need model access => do this after widget generation before StateData instantiation => state.uid compute state candidates by ignoring all EditFields => determine exact one by computing ref.uid when ignoring EditFields whose xPath is a target widget within the trace + proper constructor
	// and compute the state.uid with the original editText contents
	// => model keep track of interacted EditField xPaths
	// => stateData compute function getting set of xPaths to be ignored if EditField
	// => stateData val idWithoutEditFields

	val imgId get() =  uidImgId.value.second
	val id by lazy { Pair(uid, propertyId+imgId) }
	// used internally to re-identify elements between device and pc (computed as hash code of the elements (customized) unique xpath)
	internal val idHash = properties.idHash


	val isEdit: Boolean = properties.editable

	fun hasContent(): Boolean = (text + contentDesc) != ""

	val dataString:(String)->String by lazy {{ sep:String ->
		P.values().joinToString(separator = sep) { p ->
			when (p) {
				P.UID -> uid.toString()
				P.Type -> className
				P.Interactive -> canBeActedUpon.toString()
				P.Text -> text
				P.Desc -> contentDesc
				P.Clickable -> clickable.toString()
				P.Scrollable -> scrollable.toString()
				P.Checked -> checked?.toString() ?: "disabled"  //FIXME this was probably bugged by Nathaniel
				P.Focused -> focused?.toString() ?: "disabled"
				P.BoundsX -> bounds.x.toString()
				P.BoundsY -> bounds.y.toString()
				P.BoundsWidth -> bounds.width.toString()
				P.BoundsHeight -> bounds.height.toString()
				P.ResId -> resourceId
				P.XPath -> xpath
				P.WdId -> propertyId.toString()
				P.ParentID -> parentId?.dumpString() ?: "null"
				P.Enabled -> enabled.toString()
				P.LongClickable -> longClickable.toString()
				P.Selected -> selected.toString()
				P.IsPassword -> isPassword.toString()
				P.Visible -> visible.toString()
				P.IsLeaf -> isLeaf.toString()
				P.PackageName -> packageName
				P.Coord -> uncoveredCoord?.let { it.first.toString()+","+it.second.toString() } ?: "null"
				P.Editable -> isEdit.toString()
				P.ImgId -> imgId.toString()
			}
		}
	}}

	private val simpleClassName by lazy { className.substring(className.lastIndexOf(".") + 1) }
	@Suppress("unused")
	fun getStrippedResourceId(): String = resourceId.removePrefix("$packageName:")
	fun toShortString(): String {
		return "Wdgt:$simpleClassName/\"$text\"/\"$uid\"/[${bounds.centerX.toInt()},${bounds.centerY.toInt()}]"
	}

	fun toTabulatedString(includeClassName: Boolean = true): String {
		val pCls = simpleClassName.padEnd(20, ' ')
		val pResId = resourceId.padEnd(64, ' ')
		val pText = text.padEnd(40, ' ')
		val pContDesc = contentDesc.padEnd(40, ' ')
		val px = "${bounds.centerX.toInt()}".padStart(4, ' ')
		val py = "${bounds.centerY.toInt()}".padStart(4, ' ')

		val clsPart = if (includeClassName) "Wdgt: $pCls / " else ""

		return "${clsPart}resourceId: $pResId / text: $pText / contDesc: $pContDesc / click xy: [$px,$py]"
	}

	val canBeActedUpon by lazy { enabled && visible && (clickable || checked ?: false || longClickable || scrollable) }

	/*************************/
	companion object {
		@JvmStatic
		private val WidgetData.boundsRect: Rectangle
			get() {
				return Rectangle(this.boundsX, this.boundsY, this.boundsWidth, this.boundsHeight)
			}

		/** widget creation */
		@JvmStatic
		fun fromString(line: List<String>, indexMap: Map<P, Int> = P.defaultIndicies): Widget {
			WidgetData.fromString(line,indexMap).apply { xpath = line[P.XPath.idx(indexMap)] }.let { w ->
				assert(w.uid.toString()==line[P.WdId.idx(indexMap)]) {
					"ERROR on widget parsing: property-Id was ${w.uid} but should have been ${line[P.WdId.idx(indexMap)]}"}
				val imgId = line[P.ImgId.idx(indexMap)].let{ if(it == "null") null else UUID.fromString(it)}
				return Widget(w,lazyOf(Pair(UUID.fromString(line[P.UID.idx(indexMap)]),imgId)))
						.apply { parentId = line[P.ParentID.idx(indexMap)].let { if (it == "null") null else idFromString(it) } }
			}
		}

		/** compute the pair of (widget.uid,widget.imgId), if [isCut] is true we assume the screenImage already matches the widget.bounds
		 * @return a tuple of (uid, imgId: UUID?) if imgId != null we have an image which we can not reliably identify without
		 * considering the img.
		 * In this case the imgId should be added to the widgets configuration Id to ensure proper distinguation
		 */
		@JvmStatic
		fun computeId(w: WidgetData, screenImg: BufferedImage? = null, isCut: Boolean = false): Pair<UUID,UUID?> =
				w.content().trim().let { visibleText ->
					when {
						w.editable -> when {
							w.contentDesc.isNotBlank() -> Pair(w.contentDesc.toUUID(),null)
							w.resourceId.isNotBlank() -> Pair(w.resourceId.toUUID(),null)
							else -> Pair(w.idHash.toUUID(),null)
						}
						visibleText.isNotBlank() -> { // compute id from textual content if there is any
							val ignoreNumpers = visibleText.replace("[0-9]", "")
							if (ignoreNumpers.isNotEmpty()) Pair(ignoreNumpers.toUUID(),null)
							else Pair(visibleText.toUUID(),null)
						}
						else -> screenImg?.let {  // we have an Widget without any visible text
							val imgId = when {
								!w.visible || w.editable || w.checked != null -> null  // edit-fields would often have a cursor if focused which should only reflect in the propertyId but not in the unique-id
								isCut -> idOfImgCut(screenImg)
								else -> idOfImgCut(it.getSubImage(w.boundsRect))
							}
							if(w.resourceId.isNotBlank()) Pair(w.resourceId.toUUID(),imgId)
							else Pair(w.idHash.toUUID(),imgId)
						} ?: Pair(w.idHash.toUUID(),null) // no text content => compute id from img or if no screenshot is taken use xpath
					}
				}


		@JvmStatic
		private fun idOfImgCut(image: BufferedImage): UUID =
				(image.raster.getDataElements(0, 0, image.width, image.height, null) as ByteArray)
//						.let { UUID.nameUUIDFromBytes(it) }
						.contentHashCode().toUUID()

		fun fromUiNode(w: WidgetData, screenImg: BufferedImage?, config: ModelConfig): Widget {
			val widgetImg = if(w.visible && w.boundsWidth>0 && w.boundsHeight>0) screenImg?.getSubImage(w.boundsRect) else null
			widgetImg.let { wImg ->
				lazy {
					computeId(w, wImg, true)
				}
						.let { widgetIdPair ->  // (uid,imgId)
							launch { widgetIdPair.value }  // issue initialization in parallel
							// print the screen img if there is one and it is configured to be printed
							if (wImg != null && config[ModelProperties.imgDump.widgets] && (!config[ModelProperties.imgDump.widget.onlyWhenNoText] ||
											(config[ModelProperties.imgDump.widget.onlyWhenNoText] && w.content() == "" ) ) &&
									( config[ModelProperties.imgDump.widget.interactable] && w.actable ||
											config[ModelProperties.imgDump.widget.nonInteractable] && !w.actable ) )

								launch {
									File(config.widgetImgPath(id = widgetIdPair.value.first, postfix = "_${w.uid}", interactive = w.actable)).let {
										if (!it.exists()) ImageIO.write(wImg, "png", it)
									}
								}
							return /* debugT("create to Widget ", { */ Widget(w, widgetIdPair)
//							})
						}
			}
		}

		@JvmStatic
		val idIdx by lazy { Pair(P.UID.idx(), P.WdId.idx()) }
		@JvmStatic
		val widgetHeader:(String)->String by lazy {{ sep:String -> P.values().joinToString(separator = sep) { it.header } } }

		@JvmStatic
		private fun BufferedImage.getSubImage(r: Rectangle) =
				this.getSubimage(r.x, r.y, r.width, r.height)
	}

	/*** overwritten functions ***/
	override fun equals(other: Any?): Boolean {
		return when (other) {
			is Widget -> id == other.id
			else -> false
		}
	}

	override fun hashCode(): Int {
		return id.hashCode()
	}

	override fun toString(): String {
		return "${uid}_$propertyId:$simpleClassName[text=$text; contentDesc=$contentDesc, resourceId=$resourceId, pos=${bounds.location}:dx=${bounds.width},dy=${bounds.height}]"
	}
	/* end override */
}
