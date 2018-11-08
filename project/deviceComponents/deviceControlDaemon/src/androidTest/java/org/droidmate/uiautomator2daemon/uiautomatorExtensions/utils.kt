package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.graphics.Rect
import android.util.Log
import org.droidmate.deviceInterface.exploration.Rectangle
import java.util.*

data class DisplayDimension(val width :Int, val height: Int)

fun Rect.toRectangle() = Rectangle(left, top, width(), height())

fun Rectangle.toRect() = Rect(leftX,topY,rightX,bottomY)

data class Coordinate(val x:Int, val y: Int)
var markedAsOccupied = true
fun Rect.visibleAxis(uncovered: MutableCollection<Rect>, isSingleElement: Boolean = false): List<Rect>{
	if(uncovered.isEmpty()) return emptyList()
	markedAsOccupied = true
	val newR = LinkedList<Rect>()
	var changed = false
	val del = LinkedList<Rect>()
	return uncovered.mapNotNull {
		val r = Rect()
		if(r.setIntersect(this,it)) {
			changed = true
			if(!isSingleElement || r!= it){  // try detect elements which are for some reason rendered 'behind' an transparent layout element
				del.add(it)
			}else{
				markedAsOccupied = false
			}
			// this probably is done by the apps to determine their visible app areas
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
	return uncovered.mapNotNull {
		val r = Rect()
		if(r.setIntersect(this,it.toRect())) {
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

fun visibleOuterBounds(r: Collection<Rect>): Rectangle{
	debugOut("bounds for $r", false)
	val p0 = r.firstOrNull()
	val p1 = r.lastOrNull()
	return Rect(p0?.left ?: 0, p0?.top ?: 0, p1?.right ?: 0, p1?.bottom ?: 0).toRectangle()
}

var debugEnabled = true
fun debugOut(msg: String, enabled: Boolean = true){
	@Suppress("ConstantConditionIf")
	if(debugEnabled && enabled) Log.i("droidmate/uiad/DEBUG", msg)
}