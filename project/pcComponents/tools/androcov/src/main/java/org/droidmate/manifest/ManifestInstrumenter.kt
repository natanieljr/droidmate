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

package org.droidmate.manifest

import com.google.common.collect.Sets
import com.ximpleware.*

import java.nio.file.Path
import java.util.Collections
import java.util.HashSet

/**
 * Currently only enables us to add permissions to the android manifest.
 *
 * Originally copied to a large extent from the aggregator project.
 */
class ManifestInstrumenter(pathToAndroidManifest: Path) {
    private val filePath: String = pathToAndroidManifest.toString()
    private val additionalPermissions = HashSet<String>()
    private val existingPermissions: Set<String>

    private val nav: VTDNav
        get() {
            val vtdGen = VTDGen()
            vtdGen.parseFile(filePath, false)
            try {
                return vtdGen.nav
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

        }

    init {
        try {
            existingPermissions = readPermissions()
        } catch (e: NavException) {
            throw RuntimeException(e)
        }

    }

    @Throws(NavException::class)
    private fun readPermissions(): Set<String> {
        val res = HashSet<String>()

        val nav = nav
        if (!nav.toElement(VTDNav.FIRST_CHILD)) {
            return emptySet()
        }

        do {
            val s = nav.toString(nav.currentIndex)
            if (s == "uses-permission") {
                val ap = AutoPilot(nav)
                ap.selectAttr("name")

                var i = ap.iterateAttr()
                while (i != -1) {
                    val perm = nav.toString(i + 1)
                    res.add(perm)
                    i = ap.iterateAttr()
                }
            }
        } while (nav.toElement(VTDNav.NS))

        return res
    }

    fun addPermission(permission: String) {
        additionalPermissions.add(permission)
    }

    fun writeOut() {
        try {
            val nav = nav
            val modifier = XMLModifier(nav)

            // we append at the end of the file
            nav.toElement(VTDNav.LAST_CHILD)

            for (additionalPermission in Sets.difference(additionalPermissions, existingPermissions)) {
                modifier.insertAfterElement(String.format("\n<uses-permission android:name=\"%s\" />\n", additionalPermission))
            }

            modifier.output(filePath)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }
}
