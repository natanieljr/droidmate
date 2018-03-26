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
package org.droidmate.fixtures.apks.monitored;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;


public class Activity4 extends HelperActivity
{
  private static final String TAG = Activity4.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity4);
  }

  public void callAPIAndRelaunchActivity4(View view)
  {
    callAPI_TelephonyManager_getCellLocation(TAG);

    Intent intent = new Intent(this, Activity4.class);
    Log.i(TAG, "Re-launching activity 4");
    startActivity(intent);
  }
}
