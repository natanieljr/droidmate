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

	fun center() = Pair(leftX + width/2, topY + height/2)

	companion object {
		fun create(left:Int, top:Int, right: Int, bottom: Int) = Rectangle(left,top,width = right-left, height = bottom-top)
		private const val serialVersionUID: Long = -1394915106 // this is "CustomRectangle".hashCode but it only has to be unique
	}
}

fun List<Rectangle>.visibleOuterBounds(): Rectangle = with(filter { it.isNotEmpty() }){
	val pl = minBy { it.leftX }
	val pt = minBy { it.topY }
	val pr = maxBy { it.rightX }
	val pb = maxBy { it.bottomY }
	return Rectangle.create(pl?.leftX ?: 0, pt?.topY ?: 0, right = pr?.rightX ?: 0, bottom = pb?.bottomY ?: 0)
}