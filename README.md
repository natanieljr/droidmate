# DroidMate-2 ![GNU GPL v3](https://www.gnu.org/graphics/gplv3-88x31.png)[![Build Status](https://travis-ci.org/uds-se/droidmate.svg?branch=master)](https://travis-ci.org/uds-se/droidmate)

DroidMate-2, an automated execution generator for Android apps.  
Copyright (C) 2012-2018 Saarland University

This program is free software. 

* www.droidmate.org  

##### Current Maintainers

* Nataniel Borges Jr. `<nataniel dot borges at cispa dot saarland>`
* Jenny Hotzkow `<jenny dot hotzkow at cispa dot saarland>`

Date of last full review of this document: 07 Aug 2018


# Introduction 

**DroidMate-2** is a platform to easily assist both developers and researchers to customize, develop and test new test generators.

DroidMate-2 can be used without app instrumentation or operating system modifications, as a test generator on real devices and emulators for app testing or regression testing. 
Additionally, it provides sensitive resource monitoring or blocking capabilities through a lightweight app instrumentation, out-of-the-box statement coverage measurement through a fully-fledged app instrumentation and native experiment reproducibility.

This file pertains to DroidMate-2 source. You should have found it at DroidMate repository root dir, denoted in this file as `repo`.


# How DroidMate works 

DroidMate-2 automatically explores behavior of an Android app by interacting with its GUI. It repeatedly reads the device state, makes a decision and interacts with the GUI, until some termination criterion is satisfied. This process is called an **exploration** of the **Application Under Exploration (	

It can be run from command line (as en executable Jar) or extended through its API. It reads Android apps (.apk files) and outputs an app state model, generated on-the-fly, as well as a varied set of reports containing information extracted from the exploration output.

Currently, DroidMate-2 can click and long-click the AUE’s GUI, restart the AUE,  press ‘back’ button and it can terminate the exploration. Any of this is called an **exploration action**. DroidMate’s **exploration strategy pool** decides which exploration action to execute based on the current UI state, derived from the XML representation of the currently visible device GUI (**GUI snapshot**), the visual UI state (**GUI screenshot**) and the set of Android framework methods that have been called after last exploration action (**API calls**). All components of DroidMate-2 can be used out-of-the-box or extended with custom features.


## Repository structure:

Following directories are sources which can be opened  as IntelliJ projects (`File -> Open`):

| project in `repo/dev`| description |
| ------- | ----------- |
| droidmate | main sources of DroidMate. |
| droidmate_usage_examples | java project showing how to use DroidMate API |


### For information about building, running or extending DroidMate, check our [wiki](https://github.com/uds-se/droidmate/wiki) ###


##### Former Maintainers #####

* Konrad Jamrozik `<jamrozik at st dot cs dot uni-saarland dot de>`
