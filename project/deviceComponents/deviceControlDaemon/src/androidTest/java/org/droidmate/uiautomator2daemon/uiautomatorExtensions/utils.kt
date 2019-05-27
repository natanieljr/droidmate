package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.droidmate.deviceInterface.exploration.Rectangle
import org.xmlpull.v1.XmlSerializer
import java.nio.ByteBuffer
import java.util.*
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
