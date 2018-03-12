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
package org.droidmate.device.datatypes

import org.droidmate.tests.windowDump_nexus7_2013_home_empty
import org.droidmate.tests.windowDump_nexus7_2013_home_removed_systemui
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import org.w3c.dom.Attr
import org.w3c.dom.Node
import org.xmlunit.builder.Input

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class UiautomatorWindowDumpFunctionsTest {

    /*
    Implemented with the help of: 
    http://stackoverflow.com/questions/141993/best-way-to-compare-2-xml-documents-in-java
    https://github.com/xmlunit/user-guide/wiki/Migrating-from-XMLUnit-1.x-to-2.x
    https://github.com/xmlunit/user-guide/wiki
   */
    @Test
    fun `removes systemui nodes`() {

        // Act
        val withRemovedNodes = UiautomatorWindowDump.removeSystemuiNodes(windowDump_nexus7_2013_home_empty)

        val diff = org.xmlunit.builder.DiffBuilder
                .compare(Input.fromString(windowDump_nexus7_2013_home_removed_systemui))
                .withTest(Input.fromString(withRemovedNodes))
                .withAttributeFilter { attr: Attr -> attr.name != "bounds" }
                .withNodeFilter { node: Node -> depth(node) <= 9 }
                .ignoreWhitespace()
                .build()

        assert(!diff.hasDifferences(), {diff.toString()})
    }

    private fun depth(node: Node): Int {
        var depth = 1
        var currNode = node
        while (currNode.parentNode != null) {
            currNode = currNode.parentNode
            depth++
        }
        return depth
    }
}

