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

import org.droidmate.misc.DroidmateException
import java.time.LocalDateTime

/**
 * See {@link IApiLogcatMessage}
 */
class ApiLogcatMessage(val message: ITimeFormattedLogcatMessage,
                       val api: IApi) : IApiLogcatMessage {
    companion object {
        private const val serialVersionUID: Long = 1

        @JvmStatic
        private val FRAGMENT_SPLITTER_CHAR = ';'
        @JvmStatic
        private val KEYVALUE_SPLITTER_CHAR = ':'
        @JvmStatic
        private val PARAM_SPLITTER_CHAR = ' '

        /**
         * Character used for escaping values so that are not considered during the parsing process
         */
        private val VALUESTRING_ENCLOSCHAR = '\''
        private val ESCAPE_CHAR = '\\'

        @JvmStatic
        fun from(logcatMessage: ITimeFormattedLogcatMessage): ApiLogcatMessage {
            assert(logcatMessage.messagePayload.isNotEmpty())

            val monitoredApiCallData = from(logcatMessage.messagePayload)

            return ApiLogcatMessage(logcatMessage, monitoredApiCallData)
        }

        /**
         * All non primitive values have to be escaped by {@link ApiLogcatMessage#VALUE_ESCAPECHAR}! (except null).
         * Separate fragments by {@link ApiLogcatMessage#FRAGMENT_SPLITTER}.
         * Separate keyword and values by {@link ApiLogcatMessage#KEYVALUE_SPLITTER}.
         * Separate param types and values by {@link ApiLogcatMessage#PARAM_SPLITTER}.
         * <p>
         * Example string to parse:
         * <pre><code>
         *   objCls: "java.net.URLConnection";mthd: "&lt;init>";retCls: "void";params: java.net.URL "http://www.google.com"
         * </code></pre>
         */
        @JvmStatic
        fun from(logcatMessagePayload: String): IApi {
            assert(logcatMessagePayload.isNotEmpty())

            val payload = ApiLogcatMessagePayload(logcatMessagePayload)
            return Api(payload.objectClass, payload.methodName, payload.returnClass, payload.paramTypes, payload.paramValues, payload.threadId, payload.stackTrace)
        }

        /**
         * <p>
         * If {@code useVarNames} is true, the output string, when treated as code, will evaluate
         * {@code api.paramValues} and {@code api.stackTrace} as variable names instead of constants in a string.
         *
         * </p><p>
         * As an example, if {@code useVarNames} is false, the method will return a string like (not an actual implementation, but similar):
         * <pre><code>"lorem ipsum param1: param1value param2: param2value stackTrace: contents"</code></pre>
         * but if set to true it will return:
         * <pre><code>"lorem ipsum param1: "+param1Value+" param2: "+param2Value+" stackTrace: "+stackTrace+""</pre></code>
         *
         * </p>
         */
        @JvmStatic
        @JvmOverloads
        fun toLogcatMessagePayload(api: IApi, useVarNames: Boolean = false): String {
            assert(api.paramTypes.size == api.paramValues.size)

            val processedParamTypes = api.paramTypes.map { it.replace(" ", ClassFileFormat.genericTypeEscape) }
            val processedParamValues = api.paramValues.map {
                if (useVarNames)
                    "\"+/ + it + /+\""
                else
                    it
            }

            val actualThreadId = if (useVarNames) "\"+/ + api.threadId + /+\"" else api.threadId
            val actualStackTrace = if (useVarNames) "\"+/ + api.stackTrace + /+\"" else api.stackTrace

            return ApiLogcatMessagePayload(
                    actualThreadId,
                    api.objectClass,
                    api.methodName,
                    api.returnClass,
                    processedParamTypes,
                    processedParamValues,
                    actualStackTrace).toString()
        }
    }

    override val time: LocalDateTime
        get() = message.time
    override val level: String
        get() = message.level
    override val tag: String
        get() = message.tag
    override val pidString: String
        get() = message.pidString
    override val messagePayload: String
        get() = message.messagePayload
    override val toLogcatMessageString: String
        get() = message.toLogcatMessageString
    override val objectClass: String
        get() = api.objectClass
    override val methodName: String
        get() = api.methodName
    override val returnClass: String
        get() = api.returnClass
    override val paramTypes: List<String>
        get() = api.paramTypes
    override val paramValues: List<String>
        get() = api.paramValues
    override val threadId: String
        get() = api.threadId
    override val stackTrace: String
        get() = api.stackTrace

    override fun getIntents(): List<String> = api.getIntents()
    override fun parseUri(): String = api.parseUri()

    override fun getStackTraceFrames(): List<String> = api.getStackTraceFrames()

    override val uniqueString: String
        get() = api.uniqueString

    override fun toString(): String = message.toString()

    private class ApiLogcatMessagePayload {
        companion object {
            private val keyword_TId = "TId"
            private val keyword_objCls = "objCls"
            private val keyword_mthd = "mthd"
            private val keyword_retCls = "retCls"
            private val keyword_params = "params"
            private val keyword_stacktrace = "stacktrace"

            private val keywords = arrayListOf(keyword_TId, keyword_objCls, keyword_mthd,
                    keyword_retCls, keyword_params, keyword_stacktrace)

        }

        val threadId: String
        val objectClass: String
        val methodName: String
        val returnClass: String
        val paramTypes: List<String>
        val paramValues: List<String>
        val stackTrace: String

        constructor(threadId: String, objectClass: String, methodName: String, returnClass: String,
                    paramTypes: List<String>, paramValues: List<String>, stackTrace: String) {
            this.threadId = threadId
            this.objectClass = objectClass
            this.methodName = methodName
            this.returnClass = returnClass
            this.paramTypes = paramTypes
            this.paramValues = paramValues
            this.stackTrace = stackTrace
        }

        constructor(payload: String) {
            /* WISH instead of this complex process of extracting the API method signature from a serialized string, the monitor should
        send through TCP a list of strings, not a string. See: org.droidmate.lib_android.MonitorJavaTemplate.addCurrentLogs

        Currently such implementation is in place because in the past the API logs were read from logcat, not from TCP socket.
        So far I decided just adapt the new TCP interface to send the same data type as it went through logcat. I did it because
        then editing the monitor source file was a pain. Since then I streamlined the process so it should be easier to fulfill
        this wish now.
         */
            val elements = splitPayload(payload)

            addThreadIdIfNecessary(elements)
            val keywordToValues = computeKeywordToValues(elements, payload)
            val params = splitAndValidateParams(keywordToValues)


            this.threadId = keywordToValues[keyword_TId]!!.single()
            this.objectClass = keywordToValues[keyword_objCls]!!.single()
            this.methodName = keywordToValues[keyword_mthd]!!.single()
            this.returnClass = keywordToValues[keyword_retCls]!!.single()
            this.paramTypes = params.first
            this.paramValues = params.second.map { s -> unescape(s) }
            this.stackTrace = keywordToValues[keyword_stacktrace]!!.single()
        }

        fun unescape(s: String): String {
            return s.replace(ESCAPE_CHAR.toString() + VALUESTRING_ENCLOSCHAR, VALUESTRING_ENCLOSCHAR.toString())
        }

        fun splitPayload(payload: String): MutableList<String> {
            val res: MutableList<String> = ArrayList()
            val builder = StringBuilder()
            var inValueString = false
            var wasEscaped = false

            payload.forEach { c ->
                if (!inValueString && (c == FRAGMENT_SPLITTER_CHAR || c == KEYVALUE_SPLITTER_CHAR || c == PARAM_SPLITTER_CHAR)) {
                    if (builder.isNotEmpty()) {
                        res.add(builder.toString())
                        builder.setLength(0)
                    }
                } else if (!inValueString && Character.isWhitespace(c)) {
                    // we ignore whitespace outside of value strings
                } else if (!wasEscaped && c == VALUESTRING_ENCLOSCHAR) {
                    if (inValueString) {
                        inValueString = false
                        res.add(builder.toString())
                        builder.setLength(0)
                    } else {
                        inValueString = true
                    }
                } else {
                    wasEscaped = c == ESCAPE_CHAR

                    builder.append(c)
                }
            }

            return res
        }

        private fun addThreadIdIfNecessary(elements: MutableList<String>) {
            if (elements.first() != keyword_TId) {
                elements.add(0, "?")
                elements.add(0, keyword_TId)
            }
        }

        private fun computeKeywordToValues(elements: List<String>, payload: String): Map<String, List<String>> {
            val keyValuePairs = keywords.map { keyword ->

                val index = elements.indexOfFirst { it == keyword }
                assert(index >= 0)
                val indexLast = elements.indexOfLast { it == keyword }

                if (index != indexLast)
                    throw DroidmateException("An API logcat message payload contains the keyword $keyword more than once. " +
                            "DroidMate doesn't support such case yet. The offending payload:\n$payload")

                val ret = Pair(index, keyword)
                ret
            }
            val indexToKeyword = hashMapOf(*keyValuePairs.toTypedArray())

            val keywordIndexes = indexToKeyword.keys.toList()

            val keywordToValues = hashMapOf(*keywords
                    .map { keyword -> Pair<String, MutableList<String>>(keyword, ArrayList()) }
                    .toTypedArray())

            elements.forEachIndexed { i, element ->
                if (element !in keywords) {
                    val elementKeywordIndex = keywordIndexes.filter { it < i }.max()!!
                    keywordToValues[indexToKeyword[elementKeywordIndex]]!!.add(element)
                }
            }
            return keywordToValues
        }

        private fun splitAndValidateParams(keywordToValues: Map<String, List<String>>): Pair<List<String>, List<String>> {
            val item = keywordToValues[keyword_params]
            if ((item == null) || ((item.size == 1) && (item[0] == "")))
                return Pair(ArrayList(), ArrayList())

            assert(item.size % 2 == 0)

            val paramTypes = item.filterIndexed { index, _ -> index % 2 == 0 }
            val paramValues = item.filterIndexed { index, _ -> index % 2 == 1 }
            paramTypes.forEach { assert(it.matches(ClassFileFormat.javaTypePattern.toRegex())) }
            return Pair(paramTypes, paramValues)
        }

        override fun toString(): String {
            return String.format("""$keyword_TId:%s;$keyword_objCls:$VALUESTRING_ENCLOSCHAR%s$VALUESTRING_ENCLOSCHAR;$keyword_mthd:$VALUESTRING_ENCLOSCHAR%s$VALUESTRING_ENCLOSCHAR;$keyword_retCls:$VALUESTRING_ENCLOSCHAR%s$VALUESTRING_ENCLOSCHAR;$keyword_params:%s;$keyword_stacktrace:$VALUESTRING_ENCLOSCHAR%s$VALUESTRING_ENCLOSCHAR""",
                    this.threadId, this.objectClass, this.methodName, this.returnClass, this.getParams(), this.stackTrace)
        }

        private fun getParams(): String
                = this.paramTypes.mapIndexed { index, paramType ->
            var paramVal = this.paramValues[index]

            if (!paramVal.equals("null"))
                paramVal = VALUESTRING_ENCLOSCHAR + paramVal.replace("\\\'", "'") + VALUESTRING_ENCLOSCHAR
            //else
            //    paramVal = VALUESTRING_ENCLOSCHAR + "" + VALUESTRING_ENCLOSCHAR

            "$VALUESTRING_ENCLOSCHAR$paramType$VALUESTRING_ENCLOSCHAR $paramVal"
        }.joinToString(PARAM_SPLITTER_CHAR.toString())
    }
}


