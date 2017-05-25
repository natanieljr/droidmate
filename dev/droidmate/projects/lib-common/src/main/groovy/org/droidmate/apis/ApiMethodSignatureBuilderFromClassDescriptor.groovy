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

import static ClassFileFormat.convertJNItypeNotationToSourceCode
import static ClassFileFormat.matchClassFieldDescriptors

class ApiMethodSignatureBuilderFromClassDescriptor implements IApiMethodSignatureBuilder
{

  /**
   * Example:
   * <pre>Landroid/content/ContentProviderClient;->update(Landroid/net/Uri;ZLandroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Object; static</pre>
   */
  private final String objectClass
  private final String returnClass
  private final String methodName
  private final List<String> paramClasses
  private final boolean isStatic
  private final String hook
  private final String name
  private final String logId
  private final String invokeCode
  private final String defaultValue
  private final String exceptionType

  ApiMethodSignatureBuilderFromClassDescriptor(String objectClass, String returnClass, String methodName,
                                               List<String> paramClasses, boolean isStatic, String hook,
                                               String name, String logId, String invokeCode, String defaultValue,
                                               String exceptionType)
  {
    this.objectClass = objectClass
    this.returnClass = returnClass
    this.methodName = methodName
    this.paramClasses = paramClasses
    this.isStatic = isStatic
    this.hook = hook
    this.name = name
    this.logId = logId
    this.invokeCode = invokeCode
    this.defaultValue = defaultValue
    this.exceptionType = exceptionType
  }

  @Override
  ApiMethodSignature build()
  {

    def out = new ApiMethodSignature(this.objectClass, this.returnClass, this.methodName, this.paramClasses, isStatic,
                                     this.hook, this.name, this.logId, this.invokeCode, this.defaultValue,
                                     this.exceptionType)
    out.assertValid()
    return out
  }

}
