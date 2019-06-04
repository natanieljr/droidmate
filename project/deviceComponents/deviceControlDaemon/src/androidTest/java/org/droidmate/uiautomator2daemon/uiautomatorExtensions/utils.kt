package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.droidmate.deviceInterface.exploration.Rectangle
import org.xmlpull.v1.XmlSerializer
import java.nio.ByteBuffer
import java.util.*
import kotlin.Comparator
import kotlin.math.abs

val backgroundScope = CoroutineScope(Dispatchers.Default + CoroutineName("background") + Job())   //same dispatcher as GlobalScope.launch

val api = Build.VERSION.SDK_INT

data class DisplayDimension(val width :Int, val height: Int)

fun Rect.toRectangle() = Rectangle(left, top, abs(width()), abs(height()))

fun Rectangle.toRect() = Rect(leftX,topY,rightX,bottomY)

data class Coordinate(val x:Int, val y: Int)
var markedAsOccupied = true
/** given a set of available window areas ([uncovered]) the (sub-)areas intersecting with [this] are computed,
 * i.e. all areas of this element which are not visible due to overlaying are NOT in the result list.
 * All these intersecting areas are removed from the list of [uncovered] such that later parsed
 * parent and sibling elements can not occupy these areas.*/
fun Rect.visibleAxis(uncovered: MutableCollection<Rect>, isSingleElement: Boolean = false): List<Rect>{
	if(uncovered.isEmpty() || this.isEmpty) return emptyList()
	markedAsOccupied = true
	val newR = LinkedList<Rect>()
	var changed = false
	val del = LinkedList<Rect>()
	return uncovered.mapNotNull {
		val r = Rect()
		if(!it.isEmpty && r.setIntersect(this,it) && !r.isEmpty) {
			changed = true
			if(!isSingleElement || r!= it){  // try detect elements which are for some reason rendered 'behind' an transparent layout element
				del.add(it)
			}else{
				markedAsOccupied = false
			}
			// this probably is done by the apps to determine their definedAsVisible app areas
			newR.apply{ // add surrounding ones areas
				add(Rect(it.left,it.top,it.right,r.top-1))// above intersection
				add(Rect(it.left,r.top,r.left-1,r.bottom))  // left from intersection
				add(Rect(r.right+1,r.top,it.right,r.bottom)) // right from intersection
				add(Rect(it.left,r.bottom+1,it.right,it.bottom))  // below from intersection
			}
			r
		}else null }.also { res ->
		if(changed) {
			uncovered.addAll(newR)
			uncovered.removeAll { it.isEmpty || del.contains(it) }
			debugOut("for $this intersections=$res modified uncovered=$uncovered",false)
		}
	}
}

/** used only in the specific case where a parent node boundaries are ONLY defined by it's children,
 * meaning it has no own 'uncovered' coordinates, then there is no need to modify the input list
 */
fun Rect.visibleAxisR(uncovered: Collection<Rectangle>): List<Rectangle>{
	if (this.isEmpty) return emptyList()
	return uncovered.mapNotNull {
		val r = Rect()
		if(!it.isEmpty() && r.setIntersect(this,it.toRect()) && !r.isEmpty) {
			r.toRectangle()
		} else null
	}.also { res -> //(uncovered is not modified)
		if(uncovered.isNotEmpty()){
			debugOut("for $this intersections=$res",false)
		}
	}
}

operator fun Coordinate.rangeTo(c: Coordinate): Collection<Coordinate> {
	return LinkedList<Coordinate>().apply {
		(x .. c.x).forEach{ px ->
			( y .. c.y).forEach { py ->
				add(Coordinate(px,py))
			}
		}
	}
}

fun visibleOuterBounds(r: Collection<Rect>): Rectangle = with(r.filter { !it.isEmpty }){
	val pl = minBy { it.left }
	val pt = minBy { it.top }
	val pr = maxBy { it.right }
	val pb = maxBy { it.bottom }
	return Rectangle.create(pl?.left ?: 0, pt?.top ?: 0, right = pr?.right ?: 0, bottom = pb?.bottom ?: 0)
}

fun bitmapToBytes(bm: Bitmap):ByteArray{
	val h = bm.height
	val size = bm.rowBytes * h
	val buffer = ByteBuffer.allocate(size*4)  // *4 since each pixel is is 4 byte big
	bm.copyPixelsToBuffer(buffer)
//		val config = Bitmap.Config.valueOf(bm.getConfig().name)

	return buffer.array()
}

@Suppress("unused") // keep it here for now, it may become usefull later on
fun bytesToBitmap(b: ByteArray, width: Int, height: Int): Bitmap {
	val config= Bitmap.Config.ARGB_8888  // should be the value from above 'val config = ..' call
	val bm = Bitmap.createBitmap(width, height, config)
	val buffer = ByteBuffer.wrap(b)
	bm.copyPixelsFromBuffer(buffer)
	return bm
}


var debugEnabled = true
fun debugOut(msg: String, enabled: Boolean = true){
	@Suppress("ConstantConditionIf")
	if(debugEnabled && enabled) Log.i("droidmate/uiad/DEBUG", msg)
}

fun XmlSerializer.addAttribute(name: String, value: Any?){
	val valueString = when (value){
		null -> "null"
		is Int -> Integer.toString(value)
		is Boolean -> java.lang.Boolean.toString(value)
		else -> safeCharSeqToString(value.toString().replace("<","&lt").replace(">","&gt"))
	}
	try {
		attribute("", name, valueString)
	} catch (e: Throwable) {
		throw java.lang.RuntimeException("'$name':'$value' contains illegal characters")
	}
}

fun safeCharSeqToString(cs: CharSequence?): String {
	return if (cs == null)	""
	else
		stripInvalidXMLChars(cs).trim()
}

private fun stripInvalidXMLChars(cs: CharSequence): String {
	val ret = StringBuffer()
	var ch: Char
	/* http://www.w3.org/TR/xml11/#charsets
			[#x1-#x8], [#xB-#xC], [#xE-#x1F], [#x7F-#x84], [#x86-#x9F], [#xFDD0-#xFDDF],
			[#x1FFFE-#x1FFFF], [#x2FFFE-#x2FFFF], [#x3FFFE-#x3FFFF],
			[#x4FFFE-#x4FFFF], [#x5FFFE-#x5FFFF], [#x6FFFE-#x6FFFF],
			[#x7FFFE-#x7FFFF], [#x8FFFE-#x8FFFF], [#x9FFFE-#x9FFFF],
			[#xAFFFE-#xAFFFF], [#xBFFFE-#xBFFFF], [#xCFFFE-#xCFFFF],
			[#xDFFFE-#xDFFFF], [#xEFFFE-#xEFFFF], [#xFFFFE-#xFFFFF],
			[#x10FFFE-#x10FFFF].
			 */
	for (i in 0 until cs.length) {
		ch = cs[i]

		if (ch.toInt() in 0x1..0x8 || ch.toInt() in 0xB..0xC || ch.toInt() in 0xE..0x1F ||
			ch.toInt() == 0x14 ||
			ch.toInt() in 0x7F..0x84 || ch.toInt() in 0x86..0x9f ||
			ch.toInt() in 0xFDD0..0xFDDF || ch.toInt() in 0x1FFFE..0x1FFFF ||
			ch.toInt() in 0x2FFFE..0x2FFFF || ch.toInt() in 0x3FFFE..0x3FFFF ||
			ch.toInt() in 0x4FFFE..0x4FFFF || ch.toInt() in 0x5FFFE..0x5FFFF ||
			ch.toInt() in 0x6FFFE..0x6FFFF || ch.toInt() in 0x7FFFE..0x7FFFF ||
			ch.toInt() in 0x8FFFE..0x8FFFF || ch.toInt() in 0x9FFFE..0x9FFFF ||
			ch.toInt() in 0xAFFFE..0xAFFFF || ch.toInt() in 0xBFFFE..0xBFFFF ||
			ch.toInt() in 0xCFFFE..0xCFFFF || ch.toInt() in 0xDFFFE..0xDFFFF ||
			ch.toInt() in 0xEFFFE..0xEFFFF || ch.toInt() in 0xFFFFE..0xFFFFF ||
			ch.toInt() in 0x10FFFE..0x10FFFF)
			ret.append(".")
		else
			ret.append(ch)
	}
	return ret.toString()
}

object SiblingNodeComparator: Comparator<Pair<Int,AccessibilityNodeInfo>> {
	private var parentBounds: Rect = Rect()
	/**
	 * this function has to be called for the parent before using the compare function to sort its children,
	 * in order to be able to detect 'empty' layout frames
	 */
	fun initParentBounds(p: AccessibilityNodeInfo){ p.getBoundsInScreen(parentBounds) }
	/**
	 * Comparator to be applied to a set of child nodes to determine their processing order.
	 * Elements at the beginning of the list should be processed first,
	 * since they are rendered 'on top' of their siblings OR in special cases the
	 * sibling is assumed to be a transparent/framing element similar to insets which does
	 * does not hide any other elements but is only used by the app for layout scaling features.
	 *
	 * @param o1 the first object to be compared.
	 * @param o2 the second object to be compared.
	 * @return a negative integer, zero, or a positive integer as the
	 *         first argument is less than, equal to, or greater than the
	 *         second.
	 * @throws NullPointerException if an argument is null and this
	 *         comparator does not permit null arguments
	 * @throws ClassCastException if the arguments' types prevent them from
	 *         being compared by this comparator.
	 */
	override fun compare(o1: Pair<Int, AccessibilityNodeInfo>?, o2: Pair<Int, AccessibilityNodeInfo>?): Int {
		if (o1 == null || o2 == null) throw NullPointerException("this comparator should not be called on nullable nodes")
		var n1 = o1.second
		val r1 = Rect().apply { n1.getBoundsInScreen(this) }
		var n2 = o2.second
		val r2 = Rect().apply { n2.getBoundsInScreen(this) }
		var swapped = false
		val (c1,c2) = when{
			o1.drawOrder() > o2.drawOrder() -> Pair(o1,o2)
			o1.drawOrder() < o2.drawOrder() -> Pair(o2,o1).also { swapped = true }
			// do not swap if they have the same drawing order to keep the order by index for equal drawing order
			else -> Pair(o1,o2)
		}
		// in case o1 and o2 were swapped update the rectangle variables
		if(swapped) {
			c1.second.getBoundsInScreen(r1)
			c2.second.getBoundsInScreen(r2)
			n1 = c1.second  // just for better readability
			n2 = c2.second
		}
		// check if c1 may be an empty/transparent frame element which is rendered in front of c2 but should not hide its sibling
		val c1IsTransparent = (r1 == parentBounds) && r1.contains(r2) &&  // the sibling c2 is completely hidden behind c1
			n1.childCount == 0 && n2.childCount>0 &&  // c2 would have child nodes but c1 does not
				n1.isEnabled && n2.isEnabled && n1.isVisibleToUser && n2.isVisibleToUser && // if one node is not visible it does not matter which one is processed first
				o1.first < o2.first	// check if the drawing order is different from the order defined via its hierarchy index => TODO check if this condition is too restrictive

		return when{
			c1IsTransparent && swapped -> -2
			c1IsTransparent -> 2
			else -> o2.drawOrder().compareTo(o1.drawOrder())  // inverted order since we want nodes with higher drawing Order to be processed first
		}
	}

	private fun Pair<Int, AccessibilityNodeInfo>.drawOrder() = this.let{ (idx,node) -> if(api>=24) node.drawingOrder else idx }
}
