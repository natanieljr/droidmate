// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
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
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org
package org.droidmate

import com.natpryce.konfig.Configuration

object ConfigWrapper {
  private val presenceFlags = arrayOf("inline","takeScreenshots")
  fun createOldConfigArgs(config:Configuration):Array<String> =
    config.list().flatMap { (_,map) ->  map.map { (prop,value) -> prop.substringAfterLast(".").let{ propName
      -> "-$propName" + if(!presenceFlags.contains(propName)) "=$value"  else ""} }}
        .filterNot { it.contains("configPath") || (it.contains("inline") && !config[ConfigProperties.deploy.inline]) }
        .toTypedArray()
}