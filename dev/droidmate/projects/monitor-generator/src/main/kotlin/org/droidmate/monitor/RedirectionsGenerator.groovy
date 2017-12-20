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

package org.droidmate.monitor

import groovy.util.logging.Slf4j
import org.droidmate.apis.Api
import org.droidmate.apis.ApiLogcatMessage
import org.droidmate.apis.ApiMethodSignature
import org.droidmate.apis.ApiPolicy
import org.droidmate.misc.MonitorConstants

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
@Slf4j
class RedirectionsGenerator implements IRedirectionsGenerator
{

  private static final nl   = System.lineSeparator()
  private static final ind4 = "    "

  private final  AndroidAPI           androidApi
  
  RedirectionsGenerator(AndroidAPI androidApi)
  {
    this.androidApi = androidApi
  }

  private static String getObjectClassWithDots(String objectClass)
  {
    /* We use Object here instead of the proper name because sometimes the class is hidden from public Android API
       and so the generated file couldn't be compiled. The instrumentation still works with Object, though.
      */
    return "Object" //  objectClass.replace("\$", ".")
  }


  @Override
  String generateMethodTargets(List<ApiMethodSignature> signatures)
  {
    return signatures
      .findAll {!(it.objectClass.startsWith("android.test."))} // For justification, see [1] in dev doc at the end of this method.
      .collect {ApiMethodSignature ams ->

      StringBuilder out = new StringBuilder()

      ams.with {

        out << String.format("@Hook(\"%s\")", ams.hook) + nl
        out << String.format("public static %s %s", ams.returnClass, ams.name) + nl
        out << "{" + nl

        /**
         * MonitorJavaTemplate and MonitorTcpServer have calls to Log.i() and Log.v() in them, whose tag starts with
         * MonitorConstants.tag_prefix. This conditional ensures
         * such calls are not being monitored,
         * as they are DroidMate's monitor internal code, not the behavior of the app under exploration.
         */
        if (ams.objectClass == "android.util.Log" && (ams.methodName in ["v", "d", "i", "w", "e"]) && paramClasses.size() in [2, 3])
        {
          out << ind4 + ind4 + "if (p0.startsWith(\"${MonitorConstants.tag_prefix}\"))" + nl
          if (paramClasses.size() == 2)
            out << ind4 + ind4 + "  return OriginalMethod.by(new \$() {}).invokeStatic(p0, p1);" + nl
          else if (paramClasses.size() == 3)
            out << ind4 + ind4 + "  return OriginalMethod.by(new \$() {}).invokeStatic(p0, p1, p2);" + nl
          else
            assert false: "paramClasses.size() is not in [2,3]. It is ${paramClasses.size()}"
        }

        out << ind4 + "String stackTrace = getStackTrace();" + nl
        out << ind4 + "long threadId = getThreadId();" + nl
        out << ind4 + String.format("String logSignature = %s;", ams.logId) + nl
        out << ind4 + "monitorHook.hookBeforeApiCall(logSignature);" + nl
        out << ind4 + String.format("Log.%s(\"%s\", logSignature);", MonitorConstants.loglevel, MonitorConstants.tag_api) + nl
        out << ind4 + "addCurrentLogs(logSignature);" + nl

        out << ind4 + "List<Uri> uriList = new ArrayList<>();" + nl
        for(int x = 0; x < ams.paramClasses.size(); ++x)
          if (ams.paramClasses.get(x).equals("android.net.Uri"))
                out << ind4 + "uriList.add(p${x});" + nl
        out << ind4 + "ApiPolicy policy = getPolicy(\"${ams.shortSignature}\", uriList);" + nl
        // Currently, when denying, the method is not being called
        out << ind4 + "switch (policy){ " + nl
        out << ind4 + ind4 + "case Allow: " + nl
        // has an embedded return, no need for break
        out << ind4 + ind4 + ind4 + ams.invokeCode + nl
        out << ind4 + ind4 + "case Mock: " + nl
        out << ind4 + ind4 + ind4 + String.format("return %s;", ams.defaultValue) + nl
        out << ind4 + ind4 + "case Deny: " + nl
        out << ind4 + ind4 + ind4 + "${ams.exceptionType} e = new ${ams.exceptionType}(\"API ${ams.objectClass}->${ams.methodName} was blocked by DroidMate\");" + nl
        out << ind4 + ind4 + ind4 + String.format("Log.e(\"%s\", e.getMessage());", MonitorConstants.tag_api) + nl
        out << ind4 + ind4 + ind4 + "throw e;" + nl
        out << ind4 + ind4 + "default:" + nl
        out << ind4 + ind4 + ind4 + "throw new RuntimeException(\"Policy for api ${ams.objectClass}->${ams.methodName} cannot be determined.\");" + nl
        out << ind4 + "}" + nl

        out << "}" + nl
        out << ind4 + nl
      }

      return out.toString()
    }.join("")
    /*
    
    Note: Redirection fails on classes from android.test.*
    Snippet of observed exception stack trace:

    (...)
    java.lang.ClassNotFoundException: Didn't find class "android.test.SyncBaseInstrumentation" on path:
    DexPathList[[zip file "/data/local/tmp/monitor.apk"]
    (...)

    */
  }

  private static String getObjectClassAsMethodName(String objectClass)
  {
    return objectClass.replace("\$", "_").replace(".", "_")
  }

  private static List<String> buildParamVarNames(ApiMethodSignature ams)
  {
    return ams.paramClasses.isEmpty() ? [] : (0..ams.paramClasses.size() - 1).collect {"p$it"}
  }

  private static String buildFormalParams(ApiMethodSignature ams, List<String> paramVarNames)
  {
    return ams.paramClasses.isEmpty() ? "" : (ams.isStatic ? "" : ", ") + (0..ams.paramClasses.size() - 1).collect {
      ams.paramClasses[it] + " " + paramVarNames[it]
    }.join(", ")
  }

  private
  static String buildApiLogcatMessagePayload(ApiMethodSignature ams, List<String> paramValues, String threadIdVarName, String stackTraceVarName)
  {

    return ApiLogcatMessage.toLogcatMessagePayload(
      new Api(ams.objectClass, ams.methodName, ams.returnClass, ams.paramClasses, paramValues, threadIdVarName, stackTraceVarName),
      /* useVarNames */ true)
  }

  private static String buildCommaSeparatedParamVarNames(ApiMethodSignature ams, List<String> paramVarNames)
  {
    return ams.paramClasses.isEmpty() ? (ams.isStatic ? ", 0" : "") :
      ", " + (0..ams.paramClasses.size() - 1).collect {paramVarNames[it]}.join(", ")
  }

  private static String degenerify(String returnClass)
  {
    String degenerified
    // Generic types contain space in their name, e.g. "<T> T".
    if (returnClass.contains(" "))
      degenerified = returnClass.dropWhile {it != " "}.drop(1) // Will return only "T" in the above-given example.
    else
      degenerified = returnClass // No generics, return type as-is.
    
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
  static private final instrCallMethodTypeMap = [
    "void"   : "Void",
    "boolean": "Boolean",
    "byte"   : "Byte",
    "char"   : "Character",
    "float"  : "Float",
    "int"    : "Int",
    "long"   : "Long",
    "short"  : "Short",
    "double" : "Double"
  ]


}
