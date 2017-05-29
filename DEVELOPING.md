  
  Copyright (c) 2012-2016 Saarland University  
  All rights reserved.

  Author: Konrad Jamrozik, github.com/konrad-jamrozik
  
  This file is part of the "DroidMate" project.

  www.droidmate.org

  Date of last full review: 11 May 2017

# Working with DroidMate code base

## Setting up IntelliJ

DroidMate is developed with IntelliJ IDEA using the directory-based project format (`.idea`  directory). To work with DroidMate, IntelliJ has to be configured with all the dependencies used for daily building (e.g. JDK) plus it has to have the following:

* Gradle plugin.
* Android Support plugin.
* Kotlin plugin.

After opening an IntelliJ project (e.g. `repo/dev/droidmate`), run `Refresh all Gradle projects` from `Gradle` plugin toolbar. After this you should be able to `Build -> Make Project` and run the tests (see section below).

If you run into problems, please see the IntelliJ section in `repo/TROUBLESHOOTING.md`.


### IntelliJ settings

My settings.jar can be obtained from [this GitHub repo](https://github.com/konrad-jamrozik/utilities/tree/master/resources). To import them to IntelliJ click: `File -> Import Settings...`

### Setting up IntelliJ for running single tests

In `Run/Debug configurations` in `Defaults` section set `JUnit` `Working directory` to the absolute path to `repo/dev/droidmate`. 
Otherwise single tests run from IntelliJ won't work as expected.

### DroidMate dependencies documentation and sources

When developing DroidMate one wants to have access to the sources and documentation of the dependencies used in the source code.

When building for the first time, Gradle downloads from maven repos the dependencies to local cache, 
together with docs and sources, readily accessible from IDE.

To get access to Android SDK sources form IDE, download `Sources for Android SDK` for `Android 6.0` using Android SDK Manager.

If you still do not have access to some sources and docs, manually add them in IntelliJ `Project sturcture -> Platform settings`

## IntelliJ projects

Following directories are sources which can be opened  as IntelliJ projects (`File -> Open`):

| project in `repo/dev`| description |
| ------- | ----------- |
| droidmate | main sources of DroidMate. |
| apk_fixtures_src | sources of apk fixtures used in the `droidmate` project tests. |
| droidmate_usage_examples | java project showing how to use DroidMate API |

Note that `apk_fixtures_src` is being built as part of the `droidmate` build. 


## Running DroidMate from IntelliJ

DroidMate has a set of predefined run configurations, summarized here. They exist to help you get started with running DroidMate 
from IDE while developing it. If you want to use DroidMate API from your Java program, without editing DroidMate sources, 
please see `repo/RUNNING.md`.

### Application run configs

The `Explore apks` run configs show you example ways of running DroidMate. You can ignore run configs in `Data extraction` and
`Reporting` folders. They are either deprecated or experimental. In both cases they are not supported.

### Gradle run configs

Use `clean` to reset everything, `build install` to build everything and install to local maven repository, and `testDevice`
 to run tests requiring device.
 
### JUnit run configs

`FastRegressionTestSuite` is the main test suite of DroidMate, run by the `:projects:command:test` Gradle task.  
`Explores monitored apk on a real device api23` is being run by `:projects:command:testDevice` Gradle task.

The root of all test suites is `org.droidmate.test_suites.AllTestSuites`.

# Technical documentation 

If you want to understand how to use DroidMate API, please refer to `repo/RUNNING.md`.

The entry class of DroidMate is `DroidmateFrontend` and so it is recommended to start code base exploration from this class.  
You can find it in:

`repo/dev/droidmate/projects/core/src/main/groovy/org/droidmate/frontend/DroidmateFrontend.groovy`

### Tests as documentation ###

Tests of DroidMate serve also as example use cases. If given class has a corresponding test class, it will have a `Test` suffix. So `DroidmateFrontend` has a `DroidmateFrontendTest` class with tests for it. You can navigate to tests of given class (if any) in IntelliJ with `ctrl+shift+T` (`Navigate -> Test` in keymap). Tests always live in `<project dir>/src/test`. Tests of core functionality are located in the `core` project.

Run the tests from IntelliJ as described in section above to be able to navigate to them directly. If you run a Gradle build, you can see the test report in:
`repo/dev/droidmate/projects/core/build/reports/tests/index.html`

## Editing the list of monitored APIs

The list of monitored APIs is located in

`repo/dev/droidmate/projects/resources/monitored_apis.json`

To monitor a new API create an entry with the following information:
* **className**: Fully-qualified name of the class which contains the method (String)
* **methodName**: Name of the method, without brackets and parameters (String)
* **paramList**: List of parameter types in JSON format (String)
* **returnType**: Type of the API's return (String)
* **isStatic**: If the API is static or not (boolean)
* **exceptionType**: Type of exception that will be generated when blocking the API. Must be a runtime exception (String)
* **defaultReturnValue**: Value that will be returned when mocking the API. Can be a value or function. (String)
* **hookenMethod**: Signature of the method that will be hooked, without parameters (String)
* **logID**: API identifier for logging (String)
* **platformVersion**: For which platform version the API should be activated (String)
* **signature**: Signature of the hook method (String)
* **jniSignature**: JNI signature of the method. Optional (String)
* **invokeAPICode**: Code to invoke the API. Parameters are named `p0,p1,...,pn` and the current object is called `this`.

**Example**:

	{
		"className": "android.hardware.Camera", 
		"methodName": "open", 
		"paramList": ["int"], 
		"returnType": "android.hardware.Camera", 
		"isStatic": true, 
		"hookedMethod": "android.hardware.Camera->open", 
		"exceptionType": "SecurityException", 
		"defaultReturnValue": "null", 
		"logID": " \"TId: \"+threadId+\" objCls: android.hardware.Camera mthd: open retCls: android.hardware.Camera params: int \"+convert(p0)+\" stacktrace: \"+stackTrace", 
		"platformVersion": "All", 
		"signature": "redir_android_hardware_Camera_open_97(int p0)", 
		"jniSignature": "Landroid/hardware/Camera;->open(I)Landroid/hardware/Camera; static", 
		"invokeAPICode": "Object returnVal =  OriginalMethod.by(new $() {}).invokeStatic (p0);\n        
		                 return (android.hardware.Camera) returnVal;"
	}

A script to convert from the old API list format to new one are available at:
- https://github.com/natanieljr/droidmate-monitoredAPIs-converter

After you make your changes, do a build (see `repo/BUILDING.md`).

To test if DroidMate successfully monitored your modified API list, observe the logcat output
while the explored application is started. You will see 100+ messages
tagged `Instrumentation`. If there were any failures, the messages will say so.

## Applying policies on individual APIs

It is now possible to apply indiviual policies for each monitored API. The following API policies are available:
* __Allow__: Executes the code contained in the `invokeAPICode` tag.
* __Deny__: Generates an exception when the API is accessed. The exception type is defined by the `exceptionType` tag.
* __Mock__: Return the value defined in the `defaultReturnValue` tag. 

The API policies should be added to the file located at:

`repo/dev/droidmate/projects/resources/api_policies.txt`

This file contains instructions and examples of policies.

In order to change policies without recompiling DroidMate, it is possible to use the switch `replaceExtractedResources` to prevent DroidMate from overriding the policy definition file in the `temp_extracted_resources` directory. In this case it is just necessary to update the file in the directory and DroidMate will activate the new policies.

## Providing your own hooks to the monitored APIs

Please see the javadocs in `repo/dev/droidmate/projects/monitor-hook/src/main/java/org/droidmate/monitor/IMonitorHook.java`.