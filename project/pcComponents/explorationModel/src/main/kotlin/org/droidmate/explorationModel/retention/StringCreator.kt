package org.droidmate.explorationModel.retention

import org.droidmate.deviceInterface.exploration.PType
import org.droidmate.deviceInterface.exploration.Persistent
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.Widget
import java.time.LocalDateTime
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

var debugParsing = false

typealias WidgetProperty = AnnotatedProperty<UiElementPropertiesI>

typealias PropertyValue = Pair<String, Any?>
fun PropertyValue.getPropertyName() = this.first
fun PropertyValue.getValue() = this.second

data class AnnotatedProperty<R>(val property: KProperty1<out R, *>, val annotation: Persistent){
	private fun String.getListElements(): List<String> = substring(1,length-1)// remove the list brackets '[ .. ]'
			.split(",").filter { it.trim().isNotBlank() } // split into separate list elements

	private fun String.parseRectangle() = this.split(Rectangle.toStringSeparator).map { it.trim() }.let{ params ->
		check(params.size==4)
		Rectangle(params[0].toInt(),params[1].toInt(),params[2].toInt(),params[3].toInt())
	}

	@Suppress("IMPLICIT_CAST_TO_ANY")
	fun parseValue(values: List<String>, indexMap: Map<AnnotatedProperty<R>,Int>): PropertyValue {
		val s = indexMap[this]?.let{ values[it].trim() }
		debugOut("parse $s of type ${annotation.type}", false)
		return property.name to when (annotation.type) {
			PType.Int -> s?.toInt() ?: 0
			PType.DeactivatableFlag -> if (s == "disabled") null else s?.toBoolean() ?: false
			PType.Boolean -> s?.toBoolean() ?: false
			PType.Rectangle -> s?.parseRectangle() ?: Rectangle.empty()
			PType.RectangleList -> s?.getListElements()?.map { it.parseRectangle() } ?: emptyList<Rectangle>() // create the list of rectangles
			PType.String -> s?: "NOT PERSISTED"
			PType.IntList -> s?.getListElements()?.map { it.trim().toInt() } ?: emptyList<Int>()
			PType.ConcreteId -> if(s == null) emptyId else ConcreteId.fromString(s)
			PType.DateTime -> LocalDateTime.parse(s)
		}
	}

	override fun toString(): String {
		return "${annotation.header}: ${annotation.type}"
	}
}

object StringCreator {
	internal fun createPropertyString(t: PType, pv: Any?):String =
			when{
				t == PType.DeactivatableFlag -> pv?.toString() ?: "disabled"
				t == PType.ConcreteId && pv is Widget? -> pv?.id.toString() // necessary for [Interaction.targetWidget]
				else -> pv.toString()
			}

	private inline fun<reified T,reified R> Sequence<AnnotatedProperty<T>>.processProperty(o: T, crossinline body:(Sequence<Pair<AnnotatedProperty<T>, String>>)->R): R =
			body(this.map { p:AnnotatedProperty<T> ->
				//			val annotation: Persistent = annotatedProperty.annotations.find { it is Persistent } as Persistent
				Pair(p,StringCreator.createPropertyString(p.annotation.type,p.property.call(o)))  // determine the actual values to be stored and transform them into string format
						.also{ (p,s) ->
							if(debugParsing) {
								val v = p.property.call(o)
								val parsed = p.parseValue(listOf(s), mapOf(p to 0)).getValue()
								val validString =
										if (p.property.name == Interaction::targetWidget.name) ((v as? Widget)?.id == parsed)
										else v == parsed
								assert(validString) { "ERROR generated string cannot be parsed to the correct value ${p.property.name}: has value $v but parsed value is $parsed" }
							}
						}
			})

	fun createPropertyString(w: Widget,sep: String): String =
			annotatedProperties.processProperty(w){
				it.joinToString(sep) { (_,valueString) -> valueString }
			}
	fun createActionString(a: Interaction, sep: String): String =
			actionProperties.processProperty(a){
				it.joinToString(sep) { (_,valueString) -> valueString }
			}


	/** [indexMap] has to contain the correct index in the string [values] list for each property */
	internal inline fun<reified T> Sequence<AnnotatedProperty<T>>.parsePropertyString(values: List<String>, indexMap: Map<AnnotatedProperty<T>,Int>): Map<String, Any?> =
		this//.filter { indexMap.containsKey(it) } // we allow for default values for missing properties
				.map{ it.parseValue(values, indexMap) }.toMap()


	internal fun parseWidgetPropertyString(values: List<String>, indexMap: Map<WidgetProperty,Int>): UiElementP
	= UiElementP(	baseAnnotations.parsePropertyString(values,indexMap) )

	internal fun parseActionPropertyString(values: List<String>, target: Widget?,
	                                       indexMap: Map<AnnotatedProperty<Interaction>, Int> = defaultActionMap): Interaction
			= with(actionProperties.parsePropertyString(values,indexMap)){
		Interaction( targetWidget = target,
				actionType = get(Interaction::actionType.name) as String,
				startTimestamp = get(Interaction::startTimestamp.name) as LocalDateTime,
				endTimestamp = get(Interaction::endTimestamp.name) as LocalDateTime,
				successful = get(Interaction::successful.name) as Boolean,
				exception = get(Interaction::exception.name) as String,
				prevState = get(Interaction::prevState.name) as ConcreteId,
				resState = get(Interaction::resState.name) as ConcreteId,
				data = get(Interaction::data.name) as String
		)}

	private val baseAnnotations: Sequence<WidgetProperty> by lazy {
		UiElementPropertiesI::class.declaredMemberProperties.mapNotNull { property ->
			property.findAnnotation<Persistent>()?.let { annotation -> AnnotatedProperty(property, annotation) }
		}.asSequence()
	}

	@JvmStatic
	val actionProperties: Sequence<AnnotatedProperty<Interaction>> by lazy{
		Interaction::class.declaredMemberProperties.mapNotNull { property ->
			property.findAnnotation<Persistent>()?.let{ annotation -> AnnotatedProperty(property,annotation) }
		}.sortedBy { (_,annotation) -> annotation.ordinal }.asSequence()
	}

	@JvmStatic
	val widgetProperties: Sequence<WidgetProperty> by lazy {
		Widget::class.declaredMemberProperties.mapNotNull { property ->
			property.findAnnotation<Persistent>()?.let{ annotation -> WidgetProperty(property,annotation) }
		}.asSequence()
	}

	@JvmStatic
	val annotatedProperties: Sequence<WidgetProperty> by lazy {
		widgetProperties.plus( baseAnnotations ).sortedBy { (_,annotation) -> annotation.ordinal }.asSequence()
	}

	fun headerFor(p: KProperty1<out UiElementPropertiesI, *>): String? = p.findAnnotation<Persistent>()?.header

	@JvmStatic
	val widgetHeader: (String)->String = { sep -> annotatedProperties.joinToString(sep) { it.annotation.header }}

	@JvmStatic
	val actionHeader: (String)->String = { sep -> actionProperties.joinToString(sep) { it.annotation.header }}

	@JvmStatic
	val defaultMap: Map<WidgetProperty, Int> = annotatedProperties.mapIndexed{ i, p -> Pair(p,i)}.toMap()

	@JvmStatic
	val defaultActionMap = actionProperties.mapIndexed{ i, p -> Pair(p,i)}.toMap()

	@JvmStatic fun main(args: Array<String>) {
		val sep = ";\t"
		val s = createPropertyString(Widget.emptyWidget,sep)
		println(s)
		println("-------- create value map")
		val vMap: Map<WidgetProperty, Int> = widgetHeader(sep).split(sep).associate { h ->
//			println("find $h")
			val i = annotatedProperties.indexOfFirst { it.annotation.header.trim() == h.trim() }
			Pair(annotatedProperties.elementAt(i),i)
		}

		val verifyProperties = vMap.filter { widgetProperties.contains(it.key) }
		println("-- Widget properties, currently only used for verify \n " +
				"${verifyProperties.map { "'${it.key.annotation.header}': Pair<PropertyName, ${it.key.annotation.type.name}> " +
						"= ${it.key.parseValue(s.split(sep),verifyProperties)}" }}")

		println("-------- create widget property")
		val wp = parseWidgetPropertyString(s.split(sep),vMap)
		println(wp)
		val w = Model.emptyModel(ModelConfig("someApp")).generateWidgets(mapOf(wp.idHash to wp))
		println(createPropertyString(w.first(),sep))
	}

}

// possibly used later for debug strings -> keep it for now

//	fun getStrippedResourceId(): String = resourceId.removePrefix("$packageName:")
//	fun toShortString(): String {
//		return "Wdgt:$simpleClassName/\"$text\"/\"$uid\"/[${bounds.centerX.toInt()},${bounds.centerY.toInt()}]"
//	}
//
//	fun toTabulatedString(includeClassName: Boolean = true): String {
//		val pCls = simpleClassName.padEnd(20, ' ')
//		val pResId = resourceId.padEnd(64, ' ')
//		val pText = text.padEnd(40, ' ')
//		val pContDesc = contentDesc.padEnd(40, ' ')
//		val px = "${bounds.centerX.toInt()}".padStart(4, ' ')
//		val py = "${bounds.centerY.toInt()}".padStart(4, ' ')
//
//		val clsPart = if (includeClassName) "Wdgt: $pCls / " else ""
//
//		return "${clsPart}resourceId: $pResId / text: $pText / contDesc: $pContDesc / click xy: [$px,$py]"
//	}
