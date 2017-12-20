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

import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ClassFileFormatTest {
    @Test
    fun `Parses java parameter descriptors`() {
        // Act 1
        ClassFileFormat.matchClassFieldDescriptors("")

        val expected = arrayListOf(
                "Z",
                "C",
                "[[D",
                "Landroid/net/Uri;",
                "[Lclass1;",
                "[Lclas/s2s/x;",
                "J",
                "Landroid/content/ContentValues;",
                "[Z",
                "F",
                "S",
                "[V",
                "[[Ljava/lang/String;",
                "Landroid/location/GpsStatus\$Listener\$SubListener;")

        // Act 2
        val descriptors = ClassFileFormat.matchClassFieldDescriptors(expected.joinToString(""))

        expected.forEachIndexed { i, expectedItem ->
            assert(expectedItem == descriptors[i])
        }
        assert(expected == descriptors)
    }

    @Test
    fun `Matches java type patterns`()
    {
        val m = ClassFileFormat.javaTypePattern.toRegex()

        arrayListOf("boolean",
                "int",
                "some.class.name",
                "java.lang.String[][]",
                "java.util.List<java.util.String[]>[]",
                "org.apache.http.client.ResponseHandler<?_extends_T>",
                "java.util.List<?_extends_Integer[][]>[]"

        ).forEach {
            // Act
            assert(m.matches(it))
        }

        arrayListOf("intx", "[]<>?").forEach {
            // Act
            assert(!m.matches(it))
        }
    }
}
