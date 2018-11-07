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

package org.droidmate.explorationModel.interaction

import kotlinx.coroutines.experimental.launch
import org.droidmate.deviceInterface.exploration.Persistent
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.*
import org.droidmate.explorationModel.retention.StringCreator.createPropertyString
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties


/**
 * @param uidImgId this lazy value was introduced for performance optimization as the uid computation can be very expensive. It is either already known (initialized) or there is a co-routine running to compute the Widget.uid
 */
@Suppress("MemberVisibilityCanBePrivate") //FIXME don't store properties as val, only use them for initialization
open class Widget internal constructor(properties: UiElementPropertiesI, val uidImgId: Lazy<Pair<UUID, UUID?>>): UiElementPropertiesI {

	//FIXME get rid of this lazy pair, we probably only need one constructor
	constructor(_uid:Lazy<UUID>,properties: UiElementPropertiesI): this(properties, lazyOf(Pair(_uid.value,null)))
	constructor(properties: UiElementPropertiesI) : this(properties, lazy { computeId(properties) })

	override val metaInfo: List<String> = properties.metaInfo

	var usedForStateId = false  //FIXME do we really still need this? if so we should have an open compute function used to determine a lazy value

	/** final properties */

	@property:Persistent("Unique Id",0)
	val uid: UUID by lazy { computeUId() }
	/** id to characterize the current 'configuration' of an element, e.g. is it visible, checked etc */
	val propertyId: UUID by lazy { computePropertyId() }
	val imgId by lazy{  uidImgId.value.second } //FIXME this should be a constructor parameter instead
	/** A widget mainly consists of two parts, [uid] encompasses the identifying one [image,Text,Description] used for unique identification
	 * and the modifiable properties, like checked, focused etc. identified via [propertyId] (and possibly [imgId]) */
	val id by lazy { computeConcreteId() }
	val visibleText: String by lazy { computeVisibleText(text, contentDesc) }
	val bounds: Rectangle by lazy { visibleBoundaries.firstOrNull()?.boundsRect() ?: Rectangle(0,0,0,0) }  // used for actions -> we are only interested in the visible area
	private val simpleClassName by lazy { className.substring(className.lastIndexOf(".") + 1) }

	val isInteractive: Boolean by lazy { computeInteractive() }

	fun hasContent(): Boolean = visibleText.isNotBlank()

	/** open function default implementations */

	protected open fun computeUId() = uidImgId.value.first
	protected open fun computePropertyId(): UUID {  //FIXME make a proper distinction which properties shall be used for PId
		// determine a deterministic string of all property values (the order of properties will always be alphabetically but if we change names our older models may become deprecated)
		return UiElementPropertiesI::class.declaredMemberProperties.joinToString("<;>"){ p: KProperty1<out UiElementPropertiesI, Any?> ->
			createPropertyString(p.call(this))
		}.toUUID()
	}
	protected open fun computeConcreteId() = Pair(uid, propertyId+imgId)
	protected open fun computeInteractive(): Boolean =
			enabled && visible && visibleBoundaries.isNotEmpty()
					&& ( isInputField || clickable || checked ?: false || longClickable || scrollable)
	open fun isLeaf(): Boolean = childHashes.isEmpty()



//TODO these should be computed on pc side (if required at all), maybe not necessary at all as we change the Ui extraction to use FOCUS_INPUT?
//TODO hasActableDescendant = children.any { it.actable || it.hasActableDescendant } -> in generateWidget function
//	val hasActableDescendant: Boolean
	/** coordinate where only this element is triggered and no actable child
	 * even if this element is actable this uncoveredCoord may be null if the children are overlying the whole element bounds
	 */
	//TODO uncovered Coordinate
//	val uncoveredCoord: Pair<Int, Int>?
	val hasActableDescendant: Boolean = false //TODO implement in widget generation and set in constructor
	var parentId: Pair<UUID, UUID>? = null  //FIXME should be initialized on construction


	//TODO we need image similarity otherwise even sleigh changes like additional boarders/highlighting will screw up the imgId
	//TODO check if still buggy in amazon "Sign In" does not always compute to same id
	// if we don't have any text visibleText we use the image, otherwise use the text for unique identification
//	var uid: UUID = (text + contentDesc).let{debugT("gen UID ${text + contentDesc}",{ if(hasContent()) it.toUUID() else  imgId }) }
	// special care for EditText elements, as the input text will change the [text] property
	// we keep track of the field via state-eContext + xpath once we interacted with it
	// however that means uid would have to become a var and we rewrite it after the state is parsed (ignoring EditField.text) and lookup the initial uid for this widget in the initial state-eContext
	// need model access => do this after widget generation before StateData instantiation => state.uid compute state candidates by ignoring all EditFields => determine exact one by computing ref.uid when ignoring EditFields whose xPath is a target widget within the trace + proper constructor
	// and compute the state.uid with the original editText contents
	// => model keep track of interacted EditField xPaths
	// => stateData compute function getting set of xPaths to be ignored if EditField
	// => stateData val idWithoutEditFields

	//TODO remove, and use StringCreator
	val dataString:(String)->String by lazy {{ sep:String ->
		P.values().joinToString(separator = sep) { p ->
			when (p) {
				P.UID -> uid.toString()
				P.Type -> className
				P.Interactive -> isInteractive.toString()
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
//				P.IsLeaf -> isLeaf.toString()
				P.PackageName -> packageName
//				P.Coord -> uncoveredCoord?.let { it.first.toString()+","+it.second.toString() } ?: "null"
				P.Editable -> isInputField.toString()
				P.ImgId -> imgId.toString()
				P.UsedforStateId -> usedForStateId.toString()
				P.HashId -> properties.idHash.toString()
			}
		}
	}}


	/*************************/
	companion object {


		/** used for dummy initializations, if nullable is undesirable */
		val emptyWidget by lazy{ Widget(DummyProperties) }

		private fun computeVisibleText(displayText: String, alternativeText: String) = "$displayText $alternativeText"

		private fun org.droidmate.deviceInterface.exploration.Rectangle.boundsRect(): Rectangle
			= Rectangle(leftX, topY, width, height)
		@JvmStatic
		private val UiElementPropertiesI.boundsRect: Rectangle
			get() {
				return with(boundaries){ boundsRect() }
			}

		/** widget creation */
		@JvmStatic
		fun fromString(line: List<String>, indexMap: Map<P, Int> = P.defaultIndicies): Widget {
			TODO("instead create own data class of UiElementProperties to initialize Widget, this is necessary because of the delegation in the main constructor")
//			UiElementProperties.fromString(line,indexMap).let { w ->
////				assert(w.propertyId.toString()==line[P.WdId.idx(indexMap)]) {
////					"ERROR on widget parsing: property-Id was ${w.propertyId} but should have been ${line[P.WdId.idx(indexMap)]}"}
//				val imgId = line[P.ImgId.idx(indexMap)].asUUID()
//				return Widget(w, lazyOf(Pair(UUID.fromString(line[P.UID.idx(indexMap)]), imgId)))
//						.apply { parentId = line[P.ParentID.idx(indexMap)].let { if (it == "null") null else idFromString(it) }
//							P.UsedforStateId.execIfSet(line,indexMap){ usedForStateId = it.toBoolean() }
//						}
//			}
		}

		/** compute the pair of (widget.uid,widget.imgId), if [isCut] is true we assume the screenImage already matches the widget.bounds
		 * @return a tuple of (uid, imgId: UUID?) if imgId != null we have an image which we can not reliably identify without
		 * considering the img.
		 * In this case the imgId should be added to the widgets configuration Id to ensure proper distinguation
		 */
		@JvmStatic
		fun computeId(w: UiElementPropertiesI, screenImg: BufferedImage? = null, isCut: Boolean = false): Pair<UUID,UUID?> =
				computeVisibleText(w.text, w.contentDesc).trim().let { visibleText ->
					when {
						w.isInputField -> when {
							w.contentDesc.isNotBlank() -> Pair(w.contentDesc.toUUID(),null)
							w.resourceId.isNotBlank() -> Pair(w.resourceId.toUUID(),null)
							else -> Pair(w.idHash.toUUID(),null)
						}
						visibleText.isNotBlank() -> { // compute id from textual visibleText if there is any
							val ignoreNumbers = visibleText.replace("[0-9]", "")
							if (ignoreNumbers.isNotEmpty()) Pair(ignoreNumbers.toUUID(),null)
							else Pair(visibleText.toUUID(),null)
						}
						else -> screenImg?.let {  // we have an Widget without any visible text
							val imgId = when {
								!w.visible || w.isInputField || w.checked != null -> null  // edit-fields would often have a cursor if focused which should only reflect in the propertyId but not in the unique-id
								isCut -> idOfImgCut(screenImg)
								else -> idOfImgCut(it.getSubImage(w.boundsRect))
							}
							if(w.resourceId.isNotBlank()) Pair(w.resourceId.toUUID(),imgId)
							else Pair(w.idHash.toUUID(),imgId)
						} ?: Pair(w.idHash.toUUID(),null) // no text visibleText => compute id from img or if no screenshot is taken use xpath
					}
				}


		@JvmStatic
		private fun idOfImgCut(image: BufferedImage): UUID =
				(image.raster.getDataElements(0, 0, image.width, image.height, null) as ByteArray)
//						.let { UUID.nameUUIDFromBytes(it) }
						.contentHashCode().toUUID()

		fun fromUiNode(w: UiElementPropertiesI, screenImg: BufferedImage?, config: ModelConfig): Widget {
			val widgetImg = if(w.visible && w.visibleBoundaries.isNotEmpty()) screenImg?.getSubImage(w.boundsRect) else null
			widgetImg.let { wImg ->
				lazy {
					computeId(w, wImg, true)
				}
						.let { widgetIdPair ->  // (uid,imgId)
							launch { widgetIdPair.value }  // issue initialization in parallel
							return  Widget(w, widgetIdPair).also{ widget ->
							// print the screen img if there is one and it is configured to be printed
							if (wImg != null && config[ConfigProperties.ModelProperties.imgDump.widgets] && (!config[ConfigProperties.ModelProperties.imgDump.widget.onlyWhenNoText] ||
											(config[ConfigProperties.ModelProperties.imgDump.widget.onlyWhenNoText] && widget.visibleText.isBlank() ) ) &&
									( config[ConfigProperties.ModelProperties.imgDump.widget.interactable] && widget.isInteractive||
											config[ConfigProperties.ModelProperties.imgDump.widget.nonInteractable] && !widget.isInteractive ) )

								launch {
									File(config.widgetImgPath(id = widgetIdPair.value.first, postfix = "_${widget.propertyId}", interactive = widget.isInteractive)).let {
										if (!it.exists()) ImageIO.write(wImg, "png", it)
									}
								}
//							return /* debugT("create to Widget ", { */ Widget(w, widgetIdPair)
//							})
						}}
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
		return "${uid}_$propertyId:$simpleClassName[text=$text; contentDesc=$contentDesc, resourceId=$resourceId, ${visibleOuterBounds(visibleBoundaries)}]"
	}

	/** all properties extracted from the device are supposed to be immutable, to guarantee reproducibility
	 * if the developer wants a different semantic he has to implement his properties/functions on top */

	final override val isKeyboard: Boolean = properties.isKeyboard
	final override val windowId: Int = properties.windowId
	final override val text: String = properties.text
	final override val contentDesc: String = properties.contentDesc
	final override val checked: Boolean? = properties.checked
	final override val resourceId: String = properties.resourceId
	final override val className: String = properties.className
	final override val packageName: String = properties.packageName
	final override val enabled: Boolean = properties.enabled
	final override val isInputField: Boolean = properties.isInputField
	final override val isPassword: Boolean = properties.isPassword
	final override val clickable: Boolean = properties.clickable
	final override val longClickable: Boolean = properties.longClickable
	final override val scrollable: Boolean = properties.scrollable
	final override val focused: Boolean? = properties.focused
	final override val selected: Boolean = properties.selected
	final override val boundaries = properties.boundaries
	final override val visibleBoundaries: List<org.droidmate.deviceInterface.exploration.Rectangle> = properties.visibleBoundaries
	final override val xpath: String = properties.xpath
	final override val idHash: Int = properties.idHash
	final override val parentHash: Int = properties.parentHash
	final override val childHashes: List<Int> = properties.childHashes
	final override val visible: Boolean = properties.visible
	final override val hasUncoveredArea: Boolean = properties.hasUncoveredArea
	/** end immutable ui-element properties */
	/* end override */

}
