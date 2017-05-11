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
import org.droidmate.apis.ApiMethodSignature
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

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

  private static List<ApiMethodSignature> readMonitoredApisXML(MonitorGeneratorResources res)
  {
    List<ApiMethodSignature> apiList = new ArrayList<>()
    String fileData = readAllLines(res.monitoredApis).join("\n")
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(new ByteArrayInputStream(fileData.getBytes()));

    //optional, but recommended
    //read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
    doc.getDocumentElement().normalize();

    NodeList nPolicies = doc.getElementsByTagName("apiPolicy");

    for (int x = 0; x < nPolicies.getLength(); x++) {
      Node nPolicy = nPolicies.item(x)
      Node nApi = nPolicy.childNodes.item(1)

      Element eApi = (Element) nApi

      String versionRestriction = eApi.getElementsByTagName("version").item(0).getTextContent()

      // If API should be ignored on Andorid 23, skip it
      if ((res.androidApi == AndroidAPI.API_23) && (versionRestriction.startsWith("!API23")))
        continue

      // Components from the API tag
      String objectClass = eApi.getElementsByTagName("class").item(0).getTextContent()
      String methodName = eApi.getElementsByTagName("method").item(0).getTextContent()
      String returnClass = eApi.getElementsByTagName("return").item(0).getTextContent()
      boolean isStatic = eApi.getElementsByTagName("static").item(0).getTextContent().equalsIgnoreCase("True")
      List<String> params = new ArrayList<>()

      Element eParams = (Element)eApi.getElementsByTagName("params").item(0)
      for (Node nParam: eParams.getElementsByTagName("param")) {
        params.add(nParam.getTextContent())
      }

      // Componenets from the Policy tag
      Element ePolicy = (Element) nPolicy;
      String policy = ePolicy.getElementsByTagName("policy").item(0).getTextContent()
      String hook = ePolicy.getElementsByTagName("hook").item(0).getTextContent()
      String name = ePolicy.getElementsByTagName("name").item(0).getTextContent()
      String logId = ePolicy.getElementsByTagName("logId").item(0).getTextContent()
      String invokeCode = ePolicy.getElementsByTagName("invoke").item(0).getTextContent()
      String defaultValue = ePolicy.getElementsByTagName("defaultValue").item(0).getTextContent()

      ApiMethodSignature api = ApiMethodSignature.fromDescriptor(objectClass, returnClass, methodName, params, isStatic,
                                                                 policy, hook, name, logId, invokeCode, defaultValue)
      apiList.add(api)
    }

    return apiList
  }

  public static List<ApiMethodSignature> getMethodSignatures(MonitorGeneratorResources res)
  {
    List<ApiMethodSignature> signatures = readMonitoredApisXML(res)
    return signatures
  }

  static handleException = {Exception e ->
    log.error("Exception was thrown and propagated to the frontend.", e)
    System.exit(1)
  }
}
