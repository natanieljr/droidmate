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