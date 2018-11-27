@file:Suppress("unused")

package org.droidmate.explorationModel

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.measureNanoTime

/** custom type aliases and extension functions */
typealias DeactivatableFlag = Boolean?

data class ConcreteId(val uid: UUID, val configId: UUID) {
	override fun toString(): String = "${uid}_$configId"

	companion object {
		fun fromString(s: String): ConcreteId? =
				if(s == "null") null else s.split("_").let { ConcreteId(UUID.fromString(it[0]), UUID.fromString(it[1])) }
	}
}

internal operator fun UUID.plus(uuid: UUID?): UUID {
	return if(uuid == null) this
	else UUID(this.mostSignificantBits + uuid.mostSignificantBits, this.leastSignificantBits + uuid.mostSignificantBits)
}
internal operator fun UUID.plus(id: Int): UUID {
	return UUID(this.mostSignificantBits + id, this.leastSignificantBits + id)
}

fun String.toUUID(): UUID = UUID.nameUUIDFromBytes(trim().toByteArray(Charset.forName("UTF-8")))
fun Int.toUUID(): UUID = UUID.nameUUIDFromBytes(toString().toByteArray(Charset.forName("UTF-8")))
fun center(c:Int, d:Int):Int = c+(d/2)

val emptyUUID: UUID = UUID.nameUUIDFromBytes(byteArrayOf())
fun String.asUUID(): UUID? = if(this == "null") null else UUID.fromString(this)
//typealias ConcreteId = Pair<UUID, UUID>
//fun ConcreteId.toString() = "${first}_$second"  // mainly for nicer debugging strings
/** custom dumpString method used for model dump & load **/
//fun ConcreteId.dumpString() = "${first}_$second"
val emptyId = ConcreteId(emptyUUID, emptyUUID)

private const val datePattern = "ddMM-HHmmss"
internal fun timestamp(): String = DateTimeFormatter.ofPattern(datePattern).format(LocalDateTime.now())


/** debug functions */

internal const val debugOutput = true
const val measurePerformance = true

@Suppress("ConstantConditionIf")
fun debugOut(msg:String, enabled: Boolean = true) { if (debugOutput && enabled) println(msg) }

inline fun <T> nullableDebugT(msg: String, block: () -> T?, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T? {
	var res: T? = null
	@Suppress("ConstantConditionIf")
	if (measurePerformance) {
		measureNanoTime {
			res = block.invoke()
		}.let {
			timer(it)
			println("time ${if (inMillis) "${it / 1000000.0} ms" else "${it / 1000.0} ns/1000"} \t $msg")
		}
	} else res = block.invoke()
	return res
}

inline fun <T> debugT(msg: String, block: () -> T, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T {
	return nullableDebugT(msg, block, timer, inMillis)!!
}

fun Collection<Rectangle>.firstCenter() = firstOrNull()?.center ?: Pair(0,0)
fun Collection<Rectangle>.firstOrEmpty() = firstOrNull() ?: Rectangle(0,0,0,0)

internal class UiElementP( properties: Map<String,Any?>) : UiElementPropertiesI {
	/** no meta information is persisted */
	override val metaInfo: List<String> = emptyList()

	override val isKeyboard: Boolean by properties
	override val hintText: String by properties
	override val inputType: Int by properties
	override val text: String by properties
	override val contentDesc: String by properties
	override val resourceId: String by properties
	override val className: String by properties
	override val packageName: String by properties
	override val isInputField: Boolean by properties
	override val isPassword: Boolean by properties
	override val visibleBounds: Rectangle by properties
	override val boundaries: Rectangle by properties
	override val clickable: Boolean by properties
	override val checked: Boolean? by properties
	override val longClickable: Boolean by properties
	override val focused: Boolean? by properties
	override val selected: Boolean by properties
	override val scrollable: Boolean by properties
	override val xpath: String by properties
	override val idHash: Int by properties
	override val parentHash: Int by properties
	override val childHashes: List<Int> by properties
	override val definedAsVisible: Boolean by properties
	override val enabled: Boolean by properties
	override val imgId: Int by properties
	override val visibleAreas: List<Rectangle> by properties
	override val hasUncoveredArea: Boolean by properties
}

object DummyProperties: UiElementPropertiesI {
	override val hintText: String = "Dummy-hintText"
	override val inputType: Int = 0
	override val imgId: Int = 0
	override val visibleBounds: Rectangle = Rectangle(0,0,0,0)
	override val hasUncoveredArea: Boolean = false
	override val boundaries: Rectangle = Rectangle(0,0,0,0)
	override val visibleAreas: List<Rectangle> = listOf(Rectangle(0,0,0,0))
	override val metaInfo: List<String> = emptyList()
	override val isKeyboard: Boolean = false
	override val text: String = "Dummy-Widget"
	override val contentDesc: String = "No-contentDesc"
	override val checked: Boolean? = null
	override val resourceId: String = "No-resourceId"
	override val className: String = "No-className"
	override val packageName: String = "No-packageName"
	override val enabled: Boolean = false
	override val isInputField: Boolean = false
	override val isPassword: Boolean = false
	override val clickable: Boolean = false
	override val longClickable: Boolean = false
	override val scrollable: Boolean = false
	override val focused: Boolean? = null
	override val selected: Boolean = false
	override val definedAsVisible: Boolean = false
	override val xpath: String = "No-xPath"
	override val idHash: Int = 0
	override val parentHash: Int = 0
	override val childHashes: List<Int> = emptyList()
}