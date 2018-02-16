@file:Suppress("unused")

package org.droidmate.uiautomator_daemon.guimodel

import java.io.Serializable

/**
 * Created by J.H. on 05.02.2018.
 */
sealed class Action : Serializable

enum class WidgetSelector {
    ResourceId,
    ClassName,
    ContentDesc,
    XPath
}

data class ClickAction(val xPath: String,
                       val resId: String = "") : Action()

data class CoordinateClickAction(val x: Int,
                                 val y: Int) : Action()

data class LongClickAction(val xPath: String,
                           val resId: String = "") : Action()

data class CoordinateLongClickAction(val x: Int,
                                     val y: Int) : Action()

data class TextAction(val xPath: String,
                      val resId: String = "",
                      val text: String) : Action()

data class WaitAction(val target: String,
                      val criteria: WidgetSelector) : Action()

class SwipeAction private constructor(val start: Pair<Int, Int>? = null, val dst: Pair<Int, Int>? = null, val xPath: String = "", val direction: String = "") : Action() {
    constructor(_start: Pair<Int, Int>, _dst: Pair<Int, Int>) : this(start = _start, dst = _dst)
    constructor(xPath: String, _direction: String) : this(xPath = xPath, direction = _direction)
}

class PressBack : Action()

class PressHome : Action()

class EnableWifi : Action()

data class LaunchApp(val appLaunchIconName: String) : Action()

