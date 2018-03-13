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
package org.droidmate.exploration.strategy

import org.slf4j.LoggerFactory
import java.io.*
import kotlin.streams.toList


/**
 * Class responsible for managing access to resource files
 */
object ResourceManager {
    internal val logger = LoggerFactory.getLogger(ResourceManager::class.java)

    /**
     * Returns the path of a resource.
     *
     * @param resName Name of the resource
     *
     * @return Path of the resource
     *
     * @throws UnsupportedOperationException when resource if not found
     */
    fun getResource(resName: String): InputStream {
        logger.debug("Loading resource $resName")
        try {
            val classLoader = ResourceManager.javaClass.classLoader
            val data = classLoader.getResourceAsStream(resName)

            val output = ByteArrayOutputStream()

            data.copyTo(output)
            output.flush()
            data.close()

            return ByteArrayInputStream(output.toByteArray())
        } catch (e: IOException) {
            logger.error("Error loading resource $resName: ${e.message}")
            throw UnsupportedOperationException(e)
        }
    }

    fun getResourceAsStringList(resName: String): List<String> {
        val res = getResource(resName)
        return BufferedReader(InputStreamReader(res)).lines().toList()
    }
}