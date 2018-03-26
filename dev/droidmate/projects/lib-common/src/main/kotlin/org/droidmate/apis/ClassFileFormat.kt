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
package org.droidmate.apis

/** See:  http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2 */
class ClassFileFormat {
    companion object {
        enum class baseTypeToSourceMap(val dataType: String) {
            B("byte"),
            C("char"),
            D("double"),
            F("float"),
            I("int"),
            J("long"),
            S("short"),
            Z("boolean"),
            V("void")
        }

        /*
  The javaJavaIdentifier stuff is based on:
  http://stackoverflow.com/questions/5205339/regular-expression-matching-fully-qualified-java-classes
  http://docs.oracle.com/javase/8/docs/api/java/lang/Character.html#isJavaIdentifierPart-char-
  */
        val genericTypeEscape = "_"
        private val addedSymbols = "[<>\\?\\[\\]$genericTypeEscape]"
        private val idStart = "(?:\\p{javaJavaIdentifierStart}|$addedSymbols)"
        private val idParts = "(?:\\p{javaJavaIdentifierPart}|$addedSymbols)*"
        private val javaClassIdPattern = "(?:$idStart$idParts\\.)+$idStart$idParts"
        private val javaPrimitiveTypePattern = baseTypeToSourceMap.values().joinToString("|") { it.dataType }
        val javaTypePattern = "(?:$javaPrimitiveTypePattern|$javaClassIdPattern)(?:\\[\\])*"

        @JvmStatic
        fun matchClassFieldDescriptors(classFieldDescriptors: String): List<String> {
            // Notation reference: http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2
            // (?: is a non-capturing group. See http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html#special
            val base = baseTypeToSourceMap.values().joinToString("|")
            val arrays = "\\[*"
            val obj = "L(?:\\w+/)*(?:(?:\\w|\\$)*\\w)+;"

            //Matcher matcher = classFieldDescriptors =~ /$arrays(?:$base|$object)/

            //val arrays = "[*"
            //val obj = "L(?:\\w+/)*(?:(?:\\w|\$)*\\w)+;"

            val matcher = "$arrays(?:$base|$obj)".toRegex()

            val out: MutableList<String> = mutableListOf()
            val matchResult = matcher.findAll(classFieldDescriptors)

            matchResult.forEach { out.add(it.groupValues.joinToString("")) }

            assert(out.joinToString("") == classFieldDescriptors,
                    { out.joinToString("") + "\t\t" + classFieldDescriptors })
            return out
        }

        @JvmStatic
        @JvmOverloads
        fun convertJNItypeNotationToSourceCode(type: String, replaceDollarsWithDots: Boolean = false): String {
            val out = StringBuilder()
            val arraysCount = type.count { it == '[' }
            var typePrim = type.replace("[", "")

            if (typePrim.startsWith("L")) {
                assert(typePrim.endsWith(";"))
                typePrim = typePrim.substring(1, typePrim.length - 2).replace("/", ".")
                if (replaceDollarsWithDots)
                    typePrim = typePrim.replace("\$", ".")

                out.append(typePrim)
            } else {
                assert(typePrim.length == 1)
                val baseType = baseTypeToSourceMap.valueOf(typePrim)
                out.append(baseType)
            }

            (0 until arraysCount).forEach { out.append("[]") }
            return out.toString()
        }
    }
}
