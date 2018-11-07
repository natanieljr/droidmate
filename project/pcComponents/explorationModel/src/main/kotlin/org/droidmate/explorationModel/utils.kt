@file:Suppress("unused")

package org.droidmate.explorationModel

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import java.nio.charset.Charset
import java.util.*
import kotlin.system.measureNanoTime

/** custom type aliases and extension functions */
typealias DeactivatableFlag = Boolean?
fun DeactivatableFlag.toString() = this?.toString() ?: "disabled"
@Suppress("SpellCheckingInspection")
val DeactivatableFlagFromString: (String) -> Boolean? = { s: String -> if (s == "disabled") null else s.toBoolean() }

// TODO move to own class file or keep it as Pair<> type alias
//class ConcreteId(val uid: UUID, val configId: UUID): Serializable {
//	override fun toString(): String = "${uid}_$configId"
//	fun fromString(s: String): ConcreteId? = if(s == "null") null else s.split("_").let { ConcreteId(UUID.fromString(it[0]), UUID.fromString(it[1])) }
//}

fun String.toUUID(): UUID = UUID.nameUUIDFromBytes(trim().toByteArray(Charset.forName("UTF-8")))
fun Int.toUUID(): UUID = UUID.nameUUIDFromBytes(toString().toByteArray(Charset.forName("UTF-8")))
fun center(c:Int, d:Int):Int = c+(d/2)

/** debug functions */

internal const val debugOutput = false
const val measurePerformance = true

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

fun visibleOuterBounds(r: List<Rectangle>): Rectangle{
	val p0 = r.firstOrNull()
	val p1 = r.lastOrNull()
	return Rectangle.create(p0?.leftX ?: 0, p0?.topY ?: 0, right = p1?.rightX ?: 0, bottom = p1?.bottomY ?: 0)
}

fun Collection<Rectangle>.firstCenter() = firstOrNull()?.center() ?: Pair(0,0)
fun Collection<Rectangle>.firstOrEmpty() = firstOrNull() ?: Rectangle(0,0,0,0)

object DummyProperties: UiElementPropertiesI {
	override val hasUncoveredArea: Boolean = false
	override val boundaries: Rectangle = Rectangle(0,0,0,0)
	override val visibleBoundaries: List<Rectangle> = listOf(Rectangle(0,0,0,0))
	override val metaInfo: List<String> = emptyList()
	override val isKeyboard: Boolean = false
	override val windowId: Int = 0
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
	override val visible: Boolean = false
	override val xpath: String = "No-xPath"
	override val idHash: Int = 0
	override val parentHash: Int = 0
	override val childHashes: List<Int> = emptyList()
}
