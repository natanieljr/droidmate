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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("UnusedDeclaration") // The class is used: it holds the definition of the relevant method: see its javadoc.
public class InstrumentationSymbols
{
  /**
   * <p>
   * This method existence in the source code of monitored app is required for instrumentation of methods of the app itself.
   * Without it, the instrumentation will fail to {@code de.uds.infsec.instrumentation.annotation.Redirect @Redirect}
   * some of the methods belonging to this app, reporting failure to generate trampolines.
   *
   * </p><p>
   * Note that this doesn't help to solve any trampoline generation failures for <i>android</i> APIs: the symbols would have
   * to be inserted in the same .dex as the monitored android API, i.e. into a framework file (not the app .dex!)
   *
   * </p>
   *
   */
  @SuppressWarnings({"UnusedDeclaration", "UnnecessaryUnboxing", "ResultOfMethodCallIgnored"})
  public void androidSymbolsForTrampoline()
  {
    try
    {
      Object[] arr = new Object[0];
      Method m = arr.getClass().getMethod("toString");
      m.invoke(arr);

    } catch (InvocationTargetException e)
    {
      e.getCause();
      e.getTargetException();
    } catch (Exception ignored)
    {
    }

    // boxing/unboxing methods
    Byte.valueOf((byte) 42).byteValue();
    Character.valueOf((char) 42).charValue();
    Double.valueOf((double) 42).doubleValue();
    Float.valueOf((float) 42).floatValue();
    Integer.valueOf((int) 42).intValue();
    Long.valueOf((long) 42).longValue();
    Short.valueOf((short) 42).shortValue();
    Boolean.valueOf(false).booleanValue();
  }
}
