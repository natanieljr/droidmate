// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
package org.droidmate.device.datatypes.statemodel

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

//TODO use konfig for configuration management (as soon as Configuration is replaces within DroidMate)
class ModelDumpConfig(path:String, appName:String) {
  val modelBaseDir = "$path${File.separator}$appName${File.separator}"  // directory path where the model file(s) should be stored
  private val stateDst = "${modelBaseDir}states${File.separator}"       // each state gets an own file named according to UUID in this directory
  private val widgetImgDst = "${modelBaseDir}widgets${File.separator}"  // the images for the app widgets are stored in this directory (for report/debugging purpose only)

  val sTextWidget = "_textWidgets" // file suffix for all widgets with text for a state
  val sWidget = "_NoTextWidgets"   // widgets without text (may become relevant for analysis later on)

  constructor(appName:String):this("out${File.separator}model",appName)
  init {
    initDirectories()
  }
  fun initDirectories(){
    Files.createDirectories(Paths.get(modelBaseDir))
    Files.createDirectories(Paths.get(stateDst))
    Files.createDirectories(Paths.get(widgetImgDst))
    Files.createDirectories(Paths.get("${widgetImgDst}nonInteractive${File.separator}"))
  }

  val widgetFile:(StateId)->String = { id->statePath(id, postfix = "_AllWidgets") }
  private val idPath:(String, String, String, String)->String = { baseDir, id, postfix, fileExtension-> baseDir+id+postfix+"."+fileExtension }
  fun statePath(id: StateId,postfix:String="",fileExtension:String="csv"):String{
    return idPath(stateDst,"${id.first}_${id.second}",postfix,fileExtension)
  }
  fun widgetImgPath(id:UUID, postfix:String="", fileExtension:String="png", interactive:Boolean):String{
    val baseDir = widgetImgDst+ if(interactive) "nonInteractive${File.separator}" else ""
    return idPath(baseDir,id.toString(),postfix,fileExtension)
  }
  val traceFile = { date:String -> "$modelBaseDir${traceFilePrefix}$date.txt" }
}

private const val datePattern = "ddMM-HHmmss"
internal fun timestamp():String = DateTimeFormatter.ofPattern(datePattern).format(LocalDateTime.now())
internal const val sep = ";"
internal const val traceFilePrefix = "trace"
val emptyUUID = UUID.nameUUIDFromBytes(byteArrayOf())
