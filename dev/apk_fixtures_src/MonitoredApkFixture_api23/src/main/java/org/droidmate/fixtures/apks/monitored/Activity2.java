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


public class Activity2 extends HelperActivity
{
  private static final String TAG = Activity2.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity2);
  }

  public void callAPIAndLaunchActivity3(View view)
  {
    callAPI_Camera_open(TAG);

    Intent intent = new Intent(this, Activity3.class);
    Log.i(TAG, "Launching activity 3");
    startActivity(intent);
  }

  public void crashActivity(View view)
  {
    crashActivity(TAG);
  }

  public void callAPIAndLaunchGooglePlay(View view)
  {
    callAPI_AudioManager_isBluetoothA2dpOn(TAG);

    launchGooglePlay(TAG);
  }

}
