// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Saarland University
// All rights reserved.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// This file is part of the "DroidMate" project.
//
// www.droidmate.org

package org.droidmate.fixtures.apks.instrumented;

public class AppInstrumentationTargets
{
  public void publicVoidMethod()
  {
  }

  void nonpublicVoidMethod()
  {
  }

  public ReturnObject advancedMethodCaller()
  {
    return advancedMethod(11, "paramStr", new ParamObject());
  }

  ReturnObject advancedMethod(int param1_int, String param2_string, ParamObject param3_paramObj)
  {
    final ReturnObject returnObject = new ReturnObject();
    returnObject.exampleOutput1_string = param2_string + "_output!";
    returnObject.exampleOutput2_int = param1_int + 1000;
    returnObject.exampleOutput3_internalObj = param3_paramObj;
    return returnObject;
  }

  //region Types for the advancedMethod

  public static class ParamObject
  {
    int exampleField = 43;

    @Override
    public String toString()
    {
      return "ParamObject{" +
        "exampleField=" + exampleField +
        '}';
    }
  }

  public class ReturnObject
  {

    String      exampleOutput1_string;
    int         exampleOutput2_int;
    ParamObject exampleOutput3_internalObj;

    @Override
    public String toString()
    {
      return "ReturnObject{" +
        "exampleOutput1_string='" + exampleOutput1_string + '\'' +
        ", exampleOutput2_int=" + exampleOutput2_int +
        ", exampleOutput3_internalObj=" + exampleOutput3_internalObj +
        '}';
    }
  }
  //endregion


}