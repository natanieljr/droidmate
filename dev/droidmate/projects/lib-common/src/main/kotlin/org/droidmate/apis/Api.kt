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

import java.io.Serializable

/**
 * See {@link IApi}
 */
class Api @JvmOverloads constructor(override val objectClass: String = "fixture.Dummy",
                                    override val methodName: String = "fixture.Dummy.method",
                                    override val returnClass: String = "fixture.Dummy",
                                    override val paramTypes: List<String> = ArrayList(),
                                    override val paramValues: List<String> = ArrayList(),
                                    override val threadId: String = "?",
                                    override val stackTrace: String = "N/A") : IApi, Serializable {
    companion object {
        val monitorRedirectionPrefix = "org.droidmate.monitor.Monitor.redir"

        // !!! DUPLICATION WARNING !!! org.droidmate.lib_android.MonitorJavaTemplate.stack_trace_frame_delimiter
        val stack_trace_frame_delimiter = "->"

        private const val serialVersionUID: Long = 1

        @JvmStatic
        private fun mergeFileNames(intentData: String): String {
            val m = "////storage/(.*)/(.+)\\.(\\w{2,4})/$".toRegex().findAll(intentData).toList()
            return if (m.isNotEmpty()) {
                val matchedParts = m[0].groupValues
                assert(matchedParts.size == 4)
                val body = matchedParts[1]
                //val filename = matchedParts[2]
                val ext = matchedParts[3]
                "///storage/$body/<filename>.$ext"
            } else
                intentData
        }

        @JvmStatic
        private fun extractIntentTargetPackageName(intentAttributes: List<String>): String {
            val componentAttr = intentAttributes.filter { it.startsWith("component=") }
            val packageAttr = intentAttributes.filter { it.startsWith("package=") }
            assert(componentAttr.size <= 1)
            assert(packageAttr.size <= 1)

            val componentPackage = extractComponentPackage(componentAttr)
            val packagePackage = extractPackagePackage(packageAttr)

            if (componentPackage.isNotEmpty() && packagePackage.isNotEmpty())
                assert(componentPackage == packagePackage)

            return if (componentPackage.isNotEmpty())
                componentPackage
            else
                packagePackage
        }

        @JvmStatic
        private fun extractPackagePackage(packageAttr: List<String>): String {
            var packagePackage = ""
            if (!packageAttr.isEmpty()) {
                val m = "package=(.+)".toRegex().findAll(packageAttr[0]).toList()
                assert(m.isNotEmpty())
                val matchedParts = m[0].groupValues
                assert(matchedParts.size == 2)
                packagePackage = matchedParts[1]
            }
            return packagePackage
        }

        @JvmStatic
        private fun extractComponentPackage(componentAttr: List<String>): String {
            var componentPackage = ""
            if (!componentAttr.isEmpty()) {
                val m = "component=(.+)/(?:.+)".toRegex().findAll(componentAttr[0]).toList()
                assert(m.isNotEmpty())
                val matchedParts = m[0].groupValues
                assert(matchedParts.size == 2)
                componentPackage = matchedParts[1]
            }
            return componentPackage
        }

        // Implementation based on android.content.Intent#toUriInner
        private fun stripExtras(attributes: List<String>): List<String> {
            val m = "(?:S|B|b|c|d|f|i|l|s).(.*)=(.*)/".toRegex()
            return attributes.filter { !m.matches(it) }
        }
    }

    private val stackTraceFrames: MutableList<String> = ArrayList()

    override fun getStackTraceFrames(): List<String> {
        if (stackTraceFrames.isEmpty())
            stackTraceFrames.addAll(stackTrace.split(stack_trace_frame_delimiter))
        return stackTraceFrames
    }

    override val uniqueString: String
        get() {
            var uriSuffix = ""
            var intentSuffix = ""

            if (objectClass.contains("ContentResolver") && paramTypes.indexOfFirst { it == "android.net.Uri" } != -1)
      uriSuffix = " uri: " + parseUri()

            if (paramTypes.indexOfFirst { it == "android.content.Intent" } != -1)
                intentSuffix = " intent: " + getIntents()[0]

    if (methodName == "<init>")
      return "$objectClass: $returnClass $methodName"
    else
        return "$objectClass: $returnClass $methodName(${paramTypes.joinToString(",")})$uriSuffix$intentSuffix"
  }

    override fun parseUri(): String {
        assert(paramTypes.filter { it == "android.net.Uri" }.size == 1)

        val uriIndex = paramTypes.indexOfFirst { it == "android.net.Uri" }
        var uri = paramValues[uriIndex]

        assert(uri.startsWith("content://") ||
                uri.startsWith("android.resource://") ||
                uri.startsWith("file://") ||
                uri.startsWith("http://") ||
                uri.startsWith("https://") ||
                // Added due to "com.steam.photoeditor", it uses the following resource:
                // /data/user/0/com.steam.photoeditor/cache/admobVideoStreams/48E464ED6F3F22EED4D9314B276E580C
                uri.startsWith("/") ||
                // Added due to "com.myfitnesspal.android", it uses the following resource:
                // startinfo://new
                uri.startsWith("startinfo://"))

        var uriParts = uri.split("\\?")
        assert(uriParts.size <= 2)
        uri = uriParts[0]

        uriParts = uri.split("/")
        if (uriParts.last().toIntOrNull() != null)
            uri = uriParts.take(uriParts.size - 1).joinToString("/") + "/<number>"

        return uri
    }

  /**
   * Parses the sole {@code android.content.Intent} parameter of the API call. The Intent is expected to be in a format as
   * returned by {@code android.content.Intent #toUri(1)}.
   *
   * @return A two-element list: [1. unique string of the intent, 2. the package name of targeted intent's recipient or null]
   */
  override fun getIntents(): List<String> {
      assert(paramTypes.filter { it == "android.content.Intent" }.size == 1)

      val intentIndex = paramTypes.indexOfFirst { it == "android.content.Intent" }
      var intent = paramValues[intentIndex]

      if (intent == "null")
          return arrayListOf("null", "null")

      if (!intent.contains("#Intent;"))
          return ArrayList()

      intent = intent.removePrefix("intent:")

      val m = "(.*)#Intent;(.*)end".toRegex().findAll(intent).toList()
      assert(m.isNotEmpty())

      val matchedParts = m[0].groupValues.toMutableList()
      assert(matchedParts.size == 3)
      matchedParts.removeAt(0) // Drops the field with the entire matched string.

      val data = "data=" + mergeFileNames(matchedParts[0])

      var attributes = matchedParts[1].split(";")
      attributes = stripExtras(attributes)

      val intentTargetPackageName = extractIntentTargetPackageName(attributes)

      val uniqueString = arrayListOf(data).addAll(attributes).toString()

      return arrayListOf(uniqueString, intentTargetPackageName)
  }
}
