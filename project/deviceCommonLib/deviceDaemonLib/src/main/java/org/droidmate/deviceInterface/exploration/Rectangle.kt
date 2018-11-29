package org.droidmate.deviceInterface.exploration

import java.io.Serializable

/** meta information of currently displayed windows */
data class AppWindow(
	val windowId: Int,
	val pkgName: String,

	/** for these two Focus properties we have yet to check which ones are keyboard only and which indicate appWindow focus*/

	val hasInputFocus: Boolean,
	/** has accessibility focus */
	val hasFocus: Boolean,

	/** This is the 'overall' boundary of this window however it may be (partially) hidden by other windows.
	 * These overlays are considered within the UiElement-Visibility computation but cannot currently be reconstructed on client side
	 * since system windows are already removed from the extracted data
	 */
	val boundaries: Rectangle
) : Serializable{
	companion object {
		private const val serialVersionUID: Long = -686914223 // this is "AppWindow".hashCode but it only has to be unique
	}
}

//This would be a perfect candidate for multi-platform implementation
/** android 6 and below does not support java.awt such that we need an own rectangle wrapper in the communication layer */
data class Rectangle(val leftX:Int, val topY:Int, val width: Int, val height: Int): Serializable{

	fun isNotEmpty() = width>0 && height>0
	fun isEmpty() = width<=0 || height<=0

	val rightX by lazy{ leftX + width }
	val bottomY by lazy{ topY + height }

	val center by lazy{ Pair(leftX + width/2, topY + height/2) }

	fun contains(r: Rectangle): Boolean = r.isNotEmpty() &&
			( r.leftX in leftX..rightX
					|| r.rightX in leftX..rightX) && // left or right x is contained in this
			( r.topY in topY..bottomY  // top or bottom y is contained in this
					|| r.bottomY in topY..bottomY)

	override fun toString(): String = "$leftX$toStringSeparator$topY$toStringSeparator$width$toStringSeparator$height"

	companion object {
		const val toStringSeparator = ":"
		fun create(left:Int, top:Int, right: Int, bottom: Int) = Rectangle(left,top,width = right-left, height = bottom-top)
		private const val serialVersionUID: Long = -1394915106 // this is "CustomRectangle".hashCode but it only has to be unique

		fun empty() = Rectangle(0,0,0,0)
	}
}

fun List<Rectangle>.visibleOuterBounds(): Rectangle = with(filter { it.isNotEmpty() }){
	val pl = minBy { it.leftX }
	val pt = minBy { it.topY }
	val pr = maxBy { it.rightX }
	val pb = maxBy { it.bottomY }
	return Rectangle.create(pl?.leftX ?: 0, pt?.topY ?: 0, right = pr?.rightX ?: 0, bottom = pb?.bottomY ?: 0)
}

fun List<Rectangle>.isComplete():Boolean{
	if(size<2) return true
	var complete=true
//FIXME we may have non rectangle overall form i.e. a smaller area is connected to a bigger one
//	-> use current algo to determine rectangle area and filter all elements not in this list
// for the elements not in list check if attached to big rectangle of first step
	// if not connected choose biggest inter-connected area, problem here is e.g. walmart shopping cart has non-continious areas or need to allow for "small" distance (like x=90 on pixel)

	val areas = filter{ isNotEmpty() }.asSequence()

	// we cannot find a neighbor for the outer areas with maxX or maxY
	val maxX = areas.maxBy { it.rightX }?.rightX ?: -1
	val maxY = areas.maxBy { it.bottomY }?.bottomY ?: -1
	areas.forEach { r ->
		if(!complete) return false
		complete = (if(r.rightX<maxX) areas.any {
			(it.leftX-r.rightX).let { it<5 && it>-5 } } else true )
				&& (if(r.bottomY<maxY) areas.any{ (it.topY-r.bottomY).let { it<5 && it>-5 } } else true )
	}
	return complete
}
