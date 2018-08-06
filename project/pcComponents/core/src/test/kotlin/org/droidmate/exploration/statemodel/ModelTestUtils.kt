package org.droidmate.exploration.statemodel

fun Widget.splittedDumpString(sep: String) = this.dataString(sep).split(sep).map { it.trim() }
fun StateData.widgetsDump(sep: String) = this.widgets.map { it.dataString(sep) }
