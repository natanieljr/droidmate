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

package org.droidmate.tests_misc

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.io.*
import java.nio.file.Files
import java.util.*

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
/**
 * @see org.droidmate.tests.logging.LogbackAppendersTest
 */
class JavaTest {
    @Test
    fun test_manyScannersOnOneReaderReadingLinesThrowException() {
        val r = StringReader(String.format("line 1%n line 2 %n line 3"))
        val s1 = Scanner(r)
        println(s1.nextLine())
        val s2 = Scanner(r)

        try {
            println(s2.nextLine())
        } catch (ignored: NoSuchElementException) {
            return
        }

        assert(false)
    }

    @Test
    fun `serializes and deserializes`() {
        val fs = Jimfs.newFileSystem(Configuration.unix())
        val tmpDir = fs.getPath("/tmp")
        Files.createDirectory(tmpDir)
        val tmpFile = tmpDir.resolve("employee.ser")

        println("SERIALIZING")

        val empl = Employee()
        empl.name = "Reyan Ali"
        empl.address = "Phokka Kuan, Ambehta Peer"
        empl.SSN = 11122333
        empl.number = 101
        try {
            val out = ObjectOutputStream(FileOutputStream(tmpFile.toFile()))
            out.writeObject(empl)
            out.close()
            System.out.printf("Serialized data is saved in /tmp/employee.ser")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        println("")
        println("DESERIALIZING")

        try {
            val inpStr = ObjectInputStream(FileInputStream(tmpFile.toFile()))
            val empl2 = inpStr.readObject() as Employee
            inpStr.close()

            System.out.println("Deserialized Employee...")
            System.out.println("Name: " + empl2.name)
            System.out.println("Address: " + empl2.address)
            System.out.println("SSN: " + empl2.SSN)
            System.out.println("Number: " + empl2.number)
        } catch (e: IOException) {
            e.printStackTrace()
            return
        } catch (c: ClassNotFoundException) {
            System.out.println("Employee class not found")
            c.printStackTrace()
            return
        }
    }
}

class Employee : Serializable {
    lateinit var name: String
    lateinit var address: String
    var SSN: Int = 0
    var number: Int = 0

    @Suppress("unused")
    fun mailCheck() {
        System.out.println("Mailing a check to " + name
                + " " + address)
    }
}