package org.droidmate.explorationModel.retention

import org.droidmate.deviceInterface.exploration.Persistent
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.ConcreteId
import org.droidmate.explorationModel.config.dumpString
import org.droidmate.explorationModel.interaction.Widget
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

private val separator = ";" //TODO separator from config

object StringCreator {
	internal fun createPropertyString(propertyValue: Any?):String =
	//FIXME cannot infer specific type ConcreteId unless we change it to class instead of type alias
			when(propertyValue){
		is DeactivatableFlag -> propertyValue.toString()

//			is ConcreteId -> propertyValue.toString()
//			is ConcreteId? -> propertyValue?.toString() ?: "null"
		is Pair<*,*> -> (propertyValue as? ConcreteId)?.dumpString() ?: "ERROR unknown type $propertyValue"
				is Pair<*,*>? -> propertyValue?.let{ (propertyValue as? ConcreteId)?.dumpString() ?: "ERROR unknown type $propertyValue" } ?: "null"

		else -> propertyValue.toString()
	}
	private inline fun<reified R> processProperty(w: Widget, crossinline body:(List<Pair<AnnotatedProperty, String>>)->R): R =
			body(annotatedProperties.map { p ->
				//			val annotation: Persistent = annotatedProperty.annotations.find { it is Persistent } as Persistent
				Pair(p,StringCreator.createPropertyString(p.property.call(w)))  // determine the actual values to be stored and transform them into string format
			})


	fun createPropertyString(w: Widget): String =
			processProperty(w){
				it.joinToString(separator) { (_,valueString) -> valueString }
			}

	fun debugString(w: Widget):String =
			processProperty(w){
				it.joinToString(separator = ",\t"){ (p,valueString) ->
					//TODO remove this debug out
					val s = "${p.annotation.header} = $valueString"
					println("@${p.property.name} [${p.annotation.ordinal}]\t $s")
					s
				}}

	data class AnnotatedProperty(val property: KProperty1<out UiElementPropertiesI, *>, val annotation: Persistent)

	private val baseAnnotations: List<AnnotatedProperty> by lazy {
		UiElementPropertiesI::class.declaredMemberProperties.mapNotNull { property ->
			property.findAnnotation<Persistent>()?.let { annotation -> AnnotatedProperty(property, annotation) }
		}
	}

	val annotatedProperties: List<AnnotatedProperty> by lazy { Widget::class.declaredMemberProperties.mapNotNull { property ->
		property.findAnnotation<Persistent>()?.let{ annotation -> AnnotatedProperty(property,annotation) }
	}.plus( baseAnnotations ).sortedBy { (_,annotation) -> annotation.ordinal }
	}

	@JvmStatic
	val widgetHeader by lazy{annotatedProperties.joinToString(separator) { it.annotation.header }}

	@JvmStatic fun main(args: Array<String>) {

		println(createPropertyString(Widget.emptyWidget))
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
