package org.droidmate.explorationModel.retention

import org.droidmate.deviceInterface.exploration.PType
import org.droidmate.deviceInterface.exploration.Persistent
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.Widget
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

typealias PropertyValue = Pair<String, Any?>
fun PropertyValue.getPropertyName() = this.first
fun PropertyValue.getValue() = this.second

data class AnnotatedProperty(val property: KProperty1<out UiElementPropertiesI, *>, val annotation: Persistent){
	private fun String.getListElements(): List<String> = substring(1,length-1)// remove the list brackets '[ .. ]'
			.split(",").filter { it.trim().isNotBlank() } // split into separate list elements

	private fun String.parseRectangle() = this.split(Rectangle.toStringSeparator).map { it.trim() }.let{ params ->
		check(params.size==4)
		Rectangle(params[0].toInt(),params[1].toInt(),params[2].toInt(),params[3].toInt())
	}

	@Suppress("IMPLICIT_CAST_TO_ANY")
	fun parseValue(values: List<String>, indexMap: Map<AnnotatedProperty,Int>): PropertyValue {
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
		}
	}

	override fun toString(): String {
		return "${annotation.header}: ${annotation.type}"
	}
}

object StringCreator {
	internal fun createPropertyString(pv: Any?):String =
			when(pv){
				is DeactivatableFlag -> pv?.toString() ?: "disabled"
				else -> pv.toString()
			}

	private inline fun<reified R> processProperty(w: Widget, crossinline body:(Sequence<Pair<AnnotatedProperty, String>>)->R): R =
			body(annotatedProperties.map { p ->
				//			val annotation: Persistent = annotatedProperty.annotations.find { it is Persistent } as Persistent
				Pair(p,StringCreator.createPropertyString(p.property.call(w)))  // determine the actual values to be stored and transform them into string format
						.also{ (p,s) ->
							assert(p.property.call(w)==p.parseValue(listOf(s), mapOf(p to 0)).getValue()) {
								"ERROR generated string cannot be parsed to the correct value"
							}
						}
			})

	fun createPropertyString(w: Widget,sep: String): String =
			processProperty(w){
				it.joinToString(sep) { (_,valueString) -> valueString }
			}


	/** [indexMap] has to contain the correct index in the string [values] list for each property */
	internal fun parsePropertyString(values: List<String>, indexMap: Map<AnnotatedProperty,Int>): UiElementP{
		val propertyValues: Sequence<Pair<String, Any?>> = baseAnnotations//.filter { indexMap.containsKey(it) } // we allow for default values for missing properties
				.map{ it.parseValue(values, indexMap) }
		return UiElementP(	propertyValues.toMap()	)
	}

	private val baseAnnotations: Sequence<AnnotatedProperty> by lazy {
		UiElementPropertiesI::class.declaredMemberProperties.mapNotNull { property ->
			property.findAnnotation<Persistent>()?.let { annotation -> AnnotatedProperty(property, annotation) }
		}.asSequence()
	}

	val widgetProperties: Sequence<AnnotatedProperty> by lazy {
		Widget::class.declaredMemberProperties.mapNotNull { property ->
			property.findAnnotation<Persistent>()?.let{ annotation -> AnnotatedProperty(property,annotation) }
		}.asSequence()
	}

	val annotatedProperties: Sequence<AnnotatedProperty> by lazy {
		widgetProperties.plus( baseAnnotations ).sortedBy { (_,annotation) -> annotation.ordinal }.asSequence()
	}

	fun headerFor(p: KProperty1<out UiElementPropertiesI, *>): String? = p.findAnnotation<Persistent>()?.header

	@JvmStatic
	val widgetHeader: (String)->String = { sep -> annotatedProperties.joinToString(sep) { it.annotation.header }}

	@JvmStatic
	val defaultMap: Map<AnnotatedProperty, Int> = annotatedProperties.mapIndexed{ i, p -> Pair(p,i)}.toMap()

	@JvmStatic fun main(args: Array<String>) {
		val sep = ";\t"
		val s = createPropertyString(Widget.emptyWidget,sep)
		println(s)
		println("-------- create value map")
		val vMap: Map<AnnotatedProperty, Int> = widgetHeader(sep).split(sep).associate { h ->
//			println("find $h")
			val i = annotatedProperties.indexOfFirst { it.annotation.header.trim() == h.trim() }
			Pair(annotatedProperties.elementAt(i),i)
		}

		val verifyProperties = vMap.filter { widgetProperties.contains(it.key) }
		println("-- Widget properties, currently only used for verify \n " +
				"${verifyProperties.map { "'${it.key.annotation.header}': Pair<PropertyName, ${it.key.annotation.type.name}> " +
						"= ${it.key.parseValue(s.split(sep),verifyProperties)}" }}")

		println("-------- create widget property")
		val wp = parsePropertyString(s.split(sep),vMap)
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
