@file:Suppress("ClassName")

package org.droidmate.exploration.statemodel.config

import com.natpryce.konfig.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

val emptyUUID: UUID = UUID.nameUUIDFromBytes(byteArrayOf())
typealias ConcreteId = Pair<UUID, UUID>
fun stateIdFromString(s: String) = s.split("_").let { ConcreteId(UUID.fromString(it[0]), UUID.fromString(it[1])) }
fun ConcreteId.toString() = "${first}_$second"
val emptyId = ConcreteId(emptyUUID, emptyUUID)

private const val datePattern = "ddMM-HHmmss"
internal fun timestamp(): String = DateTimeFormatter.ofPattern(datePattern).format(LocalDateTime.now())

object path: PropertyGroup(){
	val statesSubDir by uriType
	val widgetsSubDir by uriType
	val cleanDirs by booleanType
}
object dump: PropertyGroup(){
	val sep by stringType
	val onEachAction by booleanType

	val stateFileExtension by stringType

	val traceFileExtension by stringType
	val traceFilePrefix by stringType
}
object imgDump: PropertyGroup(){
	val states by booleanType
	val widgets by booleanType

	object widget: PropertyGroup(){
		val nonInteractable by booleanType
		val interactable by booleanType
		val onlyWhenNoText by booleanType
	}
}
