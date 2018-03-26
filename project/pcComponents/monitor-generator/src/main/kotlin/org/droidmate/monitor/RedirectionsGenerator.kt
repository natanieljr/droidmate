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

package org.droidmate.monitor

import org.droidmate.apis.Api
import org.droidmate.apis.ApiLogcatMessage
import org.droidmate.apis.ApiMethodSignature
import org.droidmate.misc.MonitorConstants

@Suppress("unused")
/**
 * <p>
 * Class that add the instrumentation code to {@link MonitorJavaTemplate}
 *
 * </p><p>
 * To diagnose method signatures here that cannot be handled by ArtHook (which is used for Android 6), observe logcat output
 * during launch of main activity of an inlined app containing monitor generated using this class.
 *
 * A log similar to the following one will appear on it:
 * <pre>
 * 06-29 19:17:21.637 16375-16375/org.droidmate.fixtures.apks.monitored W/ArtHook: java.lang.RuntimeException: Can't find original method (redir_android_net_wifi_WifiManager_startScan1)
 * </pre>
 *
 * </p><p>
 * Information about update to Android 6.0:
 *
 * </p><p>
 * Using AAR on ANT Script:<br/>
 *    http://community.openfl.org/t/integrating-aar-files/6837/2
 *    http://stackoverflow.com/questions/23777423/aar-in-eclipse-ant-project
 *
 * Using legacy org.apache.http package on Android 6.0<br/>
 *    http://stackoverflow.com/questions/33357561/compiling-google-download-library-targing-api-23-android-marshmallow
 *    http://stackoverflow.com/questions/32064633/how-to-include-http-library-in-android-project-using-m-preview-in-eclipse-ant-bu
 *    (Not working, just for information) http://stackoverflow.com/questions/31653002/how-to-use-the-legacy-apache-http-client-on-android-marshmallow
 * </p>
 *
 */
class RedirectionsGenerator constructor(private val androidApi: AndroidAPI) : IRedirectionsGenerator {
	companion object {
		@JvmStatic
		private val nl = System.lineSeparator()
		@JvmStatic
		private val ind4 = "    "

		@Suppress("UNUSED_PARAMETER")
		private fun getObjectClassWithDots(objectClass: String): String {
			/* We use Object here instead of the proper name because sometimes the class is hidden from public Android API
				 and so the generated file couldn't be compiled. The instrumentation still works with Object, though.
				*/
			return "Object" //  objectClass.replace("\$", ".")
		}

		@JvmStatic
		private fun getObjectClassAsMethodName(objectClass: String): String {
			return objectClass.replace("\$", "_").replace(".", "_")
		}

		@JvmStatic
		private fun buildParamVarNames(ams: ApiMethodSignature): List<String> {
			return if (ams.paramClasses.isEmpty())
				emptyList()
			else
				(0 until ams.paramClasses.size).map { "p$it" }
		}

		@JvmStatic
		private fun buildFormalParams(ams: ApiMethodSignature, paramVarNames: List<String>): String {
			return if (ams.paramClasses.isEmpty())
				""
			else
				(if (ams.isStatic)
					""
				else
					", ") +
						(0 until ams.paramClasses.size).map {
							ams.paramClasses[it] + " " + paramVarNames[it]
						}.joinToString(", ")
		}

		@JvmStatic
		private fun buildApiLogcatMessagePayload(ams: ApiMethodSignature,
		                                         paramValues: List<String>,
		                                         threadIdVarName: String,
		                                         stackTraceVarName: String): String {

			return ApiLogcatMessage.toLogcatMessagePayload(
					Api(ams.objectClass, ams.methodName, ams.returnClass, ams.paramClasses,
							paramValues, threadIdVarName, stackTraceVarName), true)
		}

		@JvmStatic
		private fun buildCommaSeparatedParamVarNames(ams: ApiMethodSignature, paramVarNames: List<String>): String {
			return if (ams.paramClasses.isEmpty())
				if (ams.isStatic)
					", 0"
				else
					""
			else
				", " + (0 until ams.paramClasses.size).map { paramVarNames[it] }.joinToString(", ")
		}

		@JvmStatic
		private fun degenerify(returnClass: String): String {
			// Generic types contain space in their name, e.g. "<T> T".
			var degenerified = if (returnClass.contains(" "))
				returnClass.dropWhile { it != ' ' }.drop(1) // Will return only "T" in the above-given example.
			else
				returnClass // No generics, return type as-is.

			// This conversion is necessary to avoid error of kind "error: incompatible types: Object cannot be converted to boolean"
			if (degenerified == "boolean")
				degenerified = "Boolean"
			if (degenerified == "int")
				degenerified = "Integer"
			if (degenerified == "float")
				degenerified = "Float"
			if (degenerified == "double")
				degenerified = "Double"
			if (degenerified == "long")
				degenerified = "Long"
			if (degenerified == "byte")
				degenerified = "Byte"
			if (degenerified == "short")
				degenerified = "Short"
			if (degenerified == "char")
				degenerified = "Character"
			return degenerified
		}

		/*
			The generated source will be compiled with java 1.5 which requires this mapping.
			It is compiled with java 1.5 because it is build with the old ant-based android SDK build and java 1.5
			is what the ant build file definition in Android SDK defines.
		 */
		@JvmStatic
		private val instrCallMethodTypeMap = hashMapOf("void" to "Void",
				"boolean" to "Boolean",
				"byte" to "Byte",
				"char" to "Character",
				"float" to "Float",
				"int" to "Int",
				"long" to "Long",
				"short" to "Short",
				"double" to "Double")
	}

	override fun generateMethodTargets(signatures: List<ApiMethodSignature>): String {
		return signatures
				.filterNot { it.objectClass.startsWith("android.test.") } // For justification, see [1] in dev doc at the end of this method.
				.map { ams ->

					val out = StringBuilder()

					with(ams) {

						out.append(String.format("@Hook(\"%s\")", ams.hook) + nl)
						out.append(String.format("public static %s %s", ams.returnClass, ams.name) + nl)
						out.append("{" + nl)

						/**
						 * MonitorJavaTemplate and MonitorTcpServer have calls to Log.i() and Log.v() in them, whose tag starts with
						 * MonitorConstants.tag_prefix. This conditional ensures
						 * such calls are not being monitored,
						 * as they are DroidMate's monitor internal code, not the behavior of the app under exploration.
						 */
						if (ams.objectClass == "android.util.Log" && (ams.methodName in arrayListOf("v", "d", "i", "w", "e")) && paramClasses.size in arrayListOf(2, 3)) {
							out.append(ind4 + ind4 + "if (p0.startsWith(\"${MonitorConstants.tag_prefix}\"))" + nl)
							if (paramClasses.size == 2)
								out.append(ind4 + ind4 + "  return OriginalMethod.by(new \$() {}).invokeStatic(p0, p1);" + nl)
							else if (paramClasses.size == 3)
								out.append(ind4 + ind4 + "  return OriginalMethod.by(new \$() {}).invokeStatic(p0, p1, p2);" + nl)
							else
								assert(false, { "paramClasses.size() is not in [2,3]. It is ${paramClasses.size}" })
						}

						out.append(ind4 + "String stackTrace = getStackTrace();" + nl)
						out.append(ind4 + "long threadId = getThreadId();" + nl)
						out.append(ind4 + String.format("String logSignature = %s;", ams.logId) + nl)
						out.append(ind4 + "monitorHook.hookBeforeApiCall(logSignature);" + nl)
						out.append(ind4 + String.format("Log.%s(\"%s\", logSignature);", MonitorConstants.loglevel, MonitorConstants.tag_api) + nl)
						out.append(ind4 + "addCurrentLogs(logSignature);" + nl)

						out.append(ind4 + "List<Uri> uriList = new ArrayList<>();" + nl)

						(0 until ams.paramClasses.size).forEach { x ->
							if (ams.paramClasses.get(x).equals("android.net.Uri"))
								out.append(ind4 + "uriList.add(p${x});" + nl)
						}
						out.append(ind4 + "ApiPolicy policy = getPolicy(\"${ams.getShortSignature()}\", uriList);" + nl)
						// Currently, when denying, the method is not being called
						out.append(ind4 + "switch (policy){ " + nl)
						out.append(ind4 + ind4 + "case Allow: " + nl)
						// has an embedded return, no need for break
						out.append(ind4 + ind4 + ind4 + ams.invokeCode + nl)
						out.append(ind4 + ind4 + "case Mock: " + nl)
						out.append(ind4 + ind4 + ind4 + String.format("return %s;", ams.defaultValue) + nl)
						out.append(ind4 + ind4 + "case Deny: " + nl)
						out.append(ind4 + ind4 + ind4 + "${ams.exceptionType} e = new ${ams.exceptionType}(\"API ${ams.objectClass}->${ams.methodName} was blocked by DroidMate\");" + nl)
						out.append(ind4 + ind4 + ind4 + String.format("Log.e(\"%s\", e.getMessage());", MonitorConstants.tag_api) + nl)
						out.append(ind4 + ind4 + ind4 + "throw e;" + nl)
						out.append(ind4 + ind4 + "default:" + nl)
						out.append(ind4 + ind4 + ind4 + "throw new RuntimeException(\"Policy for api ${ams.objectClass}->${ams.methodName} cannot be determined.\");" + nl)
						out.append(ind4 + "}" + nl)

						out.append("}" + nl)
						out.append(ind4 + nl)
					}

					out.toString()
				}.joinToString("")
	}

	/*

	Note: Redirection fails on classes from android.test.*
	Snippet of observed exception stack trace:

	(...)
	java.lang.ClassNotFoundException: Didn't find class "android.test.SyncBaseInstrumentation" on path:
	DexPathList[[zip file "/data/local/tmp/monitor.apk"]
	(...)

	*/
}
