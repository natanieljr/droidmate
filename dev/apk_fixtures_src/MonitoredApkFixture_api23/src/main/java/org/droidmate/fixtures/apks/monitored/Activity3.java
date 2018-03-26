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


public class Activity3 extends HelperActivity
{
  private static final String TAG = Activity3.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity3);
  }

  public void launchActivity1(View view)
  {
    Intent intent = new Intent(this, Activity1.class);
    Log.i(TAG, "Launching activity 1");
    startActivity(intent);
  }

  public void launchActivity4(View view)
  {
    Intent intent = new Intent(this, Activity4.class);
    Log.i(TAG, "Launching activity 4");
    startActivity(intent);
  }

  public void callAPIAndLaunchHome(View view)
  {
    callAPI_ContentResolver_getCurrentSyncs(TAG);
    launchHome(TAG);
  }

  public void callAPIAndCrashActivity(View view)
  {
    callAPI_ConnectivityManager_getActiveNetworkInfo(TAG);
    crashActivity(TAG);
  }

  public void launchGooglePlay(View view)
  {
    super.launchGooglePlay(TAG);
  }
}
