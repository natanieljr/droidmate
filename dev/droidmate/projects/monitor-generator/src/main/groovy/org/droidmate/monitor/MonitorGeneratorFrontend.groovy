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

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import groovy.util.logging.Slf4j
import org.droidmate.apis.ApiMethodSignature

import static java.nio.file.Files.readAllLines

@Slf4j
public class MonitorGeneratorFrontend
{

  public static void main(String[] args)
  {
    try
    {
      MonitorGeneratorResources res = new MonitorGeneratorResources(args)

      generateMonitorSrc(res)

    } catch (Exception e)
    {
      handleException(e)
    }
  }

  private static void generateMonitorSrc(MonitorGeneratorResources res)
  {
    def monitorGenerator = new MonitorGenerator(
      new RedirectionsGenerator(res.androidApi),
      new MonitorSrcTemplate(res.monitorSrcTemplatePath, res.androidApi)
    )


//    List<ApiMethodSignature> signatures = getLegacyMethodSignatures(res)
    List<ApiMethodSignature> signatures = getMethodSignatures(res)

    String monitorSrc = monitorGenerator.generate(signatures)

    new MonitorSrcFile(res.monitorSrcOutPath, monitorSrc)
  }

  private static List<ApiMethodSignature> readMonitoredApisJSON(MonitorGeneratorResources res)
  {
    String fileData = readAllLines(res.monitoredApis).join("\n")
    List<ApiMethodSignature> apiList = new ArrayList<>()
    JsonObject jsonApiList = new JsonParser().parse(fileData).getAsJsonObject();

    JsonElement apis = jsonApiList.get("apis");

    for(JsonElement item : (JsonArray)apis) {
      JsonObject objApi = (JsonObject) item;
      String className = objApi.get("className").getAsString();
      String hookedMethod = objApi.get("hookedMethod").getAsString();
      String signature = objApi.get("signature").getAsString();
      String invokeAPICode = objApi.get("invokeAPICode").getAsString();
      String defaultReturnValue = objApi.get("defaultReturnValue").getAsString();
      String exceptionType = objApi.get("exceptionType").getAsString();
      String logID = objApi.get("logID").getAsString();
      String methodName = objApi.get("methodName").getAsString();
      List<String> paramList = new ArrayList<>();
      for (JsonElement param : objApi.get("paramList").getAsJsonArray())
        paramList.add(param.getAsString());
      String returnType = objApi.get("returnType").getAsString();
      boolean isStatic = objApi.get("isStatic").getAsBoolean();
      String platformVersion = objApi.get("platformVersion").getAsString();

      if ((res.androidApi == AndroidAPI.API_23) && (platformVersion.startsWith("!API23")))
        continue

      ApiMethodSignature api = ApiMethodSignature.fromDescriptor(className, returnType, methodName, paramList,
              isStatic, hookedMethod, signature, logID, invokeAPICode, defaultReturnValue, exceptionType)
      apiList.add(api)
    }

    return apiList
  }

  static List<ApiMethodSignature> getMethodSignatures(MonitorGeneratorResources res)
  {
    List<ApiMethodSignature> signatures = readMonitoredApisJSON(res)
    return signatures
  }

  static handleException = {Exception e ->
    log.error("Exception was thrown and propagated to the frontend.", e)
    System.exit(1)
  }
}
