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

class ApiMethodSignature constructor(var objectClass: String,
                                     var returnClass: String,
                                     var methodName: String,
                                     var paramClasses: List<String>,
                                     var isStatic: Boolean = false,
                                     var hook: String,
                                     var name: String,
                                     var logId: String,
                                     var invokeCode: String,
                                     var defaultValue: String,
                                     var exceptionType: String) {
    /**
     * <p>
     * Parsing done according to:
     *
     * </p><p>
     * <code>
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3<br/>
     * </code>
     * </p><p>
     * Additional reference:
     * </p><p>
     * <code>
     * http://docs.oracle.com/javase/6/docs/api/java/lang/Class.html#getName%28%29<br/>
     * http://stackoverflow.com/questions/5085889/l-array-notation-where-does-it-come-from<br/>
     * http://stackoverflow.com/questions/3442090/java-what-is-this-ljava-lang-object<br/>
     * </code>
     * </p>
     */
    companion object {
        private val sourceToBaseTypeMap = hashMapOf(
                "byte" to "B",
                "char" to "C",
                "double" to "D",
                "float" to "F",
                "int" to "I",
                "long" to "J",
                "short" to "S",
                "boolean" to "Z",
                "void" to "V")

        @JvmStatic
        fun fromDescriptor(objectClass: String,
                           returnClass: String,
                           methodName: String,
                           paramClasses: List<String>,
                           isStatic: Boolean,
                           hook: String,
                           name: String,
                           logId: String,
                           invokeCode: String,
                           defaultValue: String,
                           exceptionType: String): ApiMethodSignature {
            return ApiMethodSignature(objectClass, returnClass, methodName, paramClasses,
                    isStatic, hook, name, logId, invokeCode, defaultValue, exceptionType)
        }

        @JvmStatic
        private fun convertToJniNotation(input: String): String {
            return if (sourceToBaseTypeMap.containsKey(input))
                sourceToBaseTypeMap[input]!!
            else
                "L" + input.replace(".", "/") + ";"
        }
    }

    fun assertValid() {
        assert(objectClass.isNotEmpty())
        assert(returnClass.isNotEmpty())
        assert(methodName.length > 0)
        assert(!methodName.startsWith("<") || (methodName.endsWith(">")))
        assert(hook.isNotEmpty())
        assert(name.isNotEmpty())
        assert(logId.isNotEmpty())
        assert(invokeCode.isNotEmpty())
    }

    fun getDistinctSignature(): List<Any>
            = arrayListOf(objectClass, methodName, paramClasses)

    fun getShortSignature(): String {
        val paramString = if (paramClasses.isNotEmpty()) paramClasses.joinToString(", ") else ""
        return "$objectClass.$methodName($paramString)"
    }

    fun isConstructor(): Boolean = methodName == "<init>"

    fun getObjectClassJni(): String = convertToJniNotation(objectClass)

    fun getParamsJni(): String {
        return paramClasses.map { convertToJniNotation(it) }.joinToString("")
    }

    @Suppress("unused")
    private fun debugPrintln(methodSignature: String, objectClass: String, returnClass: String, methodName: String, paramsList: List<String>, isStatic: Boolean) {
        println("signature   $methodSignature")
        println("objectClass $objectClass")
        println("returnClass $returnClass")
        println("methodName  $methodName")
        println("paramsList  $paramsList")
        println("isStatic    $isStatic")
        println("")
    }
}
