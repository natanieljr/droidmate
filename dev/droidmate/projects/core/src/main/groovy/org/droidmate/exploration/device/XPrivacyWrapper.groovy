// Copyright (c) 2012-2016 Saarland University
// All rights reserved.
//
// Author: Konrad Jamrozik, jamrozik@st.cs.uni-saarland.de
//
// This file is part of the "DroidMate" project.
//
// www.droidmate.org
package org.droidmate.exploration.device

import org.droidmate.device.IAndroidDevice
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.droidmate.device.datatypes.AndroidDeviceAction.newLoadXPrivacyConfigDeviceAction

/**
 * Provides clean interface for communication with XPrivacy.<br/>
 * <br/>
 * <b>Technical notes</b><br/>
 * XPrivacy must be already installed on the rooted device.<br/>
 * Current implementation was developed with XPrivacy 3.6.19
 * Reference: https://github.com/M66B/XPrivacy#frequently-asked-questions
 *
 * @author Nataniel Borges Jr.
 */
class XPrivacyWrapper
{
  private final String targetFileName = "xPrivacyConfig.xml";
  private final String configurationFileName;
  private final IAndroidDevice device;

  public XPrivacyWrapper(IAndroidDevice device, String configurationFileName)
  {
    this.device = device

    assert configurationFileName != null
    this.configurationFileName = configurationFileName.trim()
  }

  public boolean isUsingXPrivacy()
  {
    return this.configurationFileName.length() > 0
  }

  public void pullConfigurationFile(String targetFileName)
  {
    String sourceFile = UiautomatorDaemonConstants.xPrivacyDirectory + this.configurationFileName
    this.device.pullFile(sourceFile, targetFileName)
  }

  private void pushConfigurationFile()
  {
    Path path = Paths.get(this.configurationFileName)
    assert Files.exists(path) && !Files.isDirectory(path)

    this.device.pushFile(path, this.targetFileName, UiautomatorDaemonConstants.xPrivacyDirectory)
  }

  public File loadConfiguration()
  {
    this.pushConfigurationFile()
    device.clickAppIcon("XPrivacy")
    device.perform(newLoadXPrivacyConfigDeviceAction(this.targetFileName))
  }
}
