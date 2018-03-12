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
package org.droidmate

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties


abstract class ConfigProperties{
  val logLevel by stringType  // TODO we could use a nice enumType instead
  val configPath by uriType

  object output: PropertyGroup() {
    val droidmateOutputDirPath by uriType
    val reportOutputDir by uriType
  }
  object deploy:PropertyGroup(){
    val installApk by booleanType
    val installAux by booleanType
    val inline by booleanType
  }
  object exploration:PropertyGroup(){
    val apksDir by uriType
    val takeScreenshots by booleanType
    val device by intType
    val launchActivityDelay by intType
    val actionLimit by intType
    val resetEvery by intType
    val randomSeed by intType
  }
}




