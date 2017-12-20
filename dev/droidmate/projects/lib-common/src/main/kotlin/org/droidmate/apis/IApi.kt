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

package org.droidmate.apis

/**
 * <p>
 * Monitored Android API method signature.
 * </p>
 */

interface IApi {
    val objectClass: String

    val methodName: String

    val returnClass: String

    val paramTypes: List<String>

    val paramValues: List<String>

    val threadId: String

    val stackTrace: String

    fun getStackTraceFrames(): List<String>

    val uniqueString: String

    fun getIntents(): List<String>

    fun parseUri(): String
}