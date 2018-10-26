package org.droidmate.deviceInterface.exploration

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

@Target( AnnotationTarget.PROPERTY) annotation class PId // used to annotate which properties are used for WidgetData.pId computation

interface UiElementProperties{
	@property:PId
	val text:String

	fun annotatedP(): List<KProperty1<out UiElementProperties, Any?>> = UiElementProperties::class.declaredMemberProperties.filter { it.findAnnotation<PId>() != null }
}