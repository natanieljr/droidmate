// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.debug

import java.io.File
import java.lang.Thread.sleep
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

inline fun<T> debugT(msg:String, block:()->T, timer:(Long)->Unit = {}):T{
    var res:T? = null
    measureNanoTime {
        res = block.invoke()
    }.let {
        timer(it)
        println("time ${it/1000.0} \t $msg")
    }
    return res!!
}


class D {
    companion object {
        // Debug counter
        @JvmStatic
        var C = 0

        @JvmStatic
        val debugFile = File("./temp_debug.txt")

        /*static {
          debugFile.delete()
      }*/

        @JvmStatic
        fun e(dc: Int, c: () -> Any) {
            if (dc == C)
                c()
        }

        @JvmStatic
        fun Dprintln(debugContent: String) {
            debugFile.appendText(debugContent + System.lineSeparator())
            // println(debugContent
        }

        @JvmStatic
        fun wait8seconds() {
            println("waiting 8 seconds")
            sleep(1000)
            println("7")
            sleep(1000)
            println("6")
            sleep(1000)
            println("5")
            sleep(1000)
            println("4")
            sleep(1000)
            println("3")
            sleep(1000)
            println("2")
            sleep(1000)
            println("1")
            sleep(1000)
            println("continue")
        }
    }
}
