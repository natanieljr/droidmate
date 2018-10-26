package org.droidmate.explorationModel

fun Widget.splittedDumpString(sep: String) = this.dataString(sep).split(sep).map { it.trim() }
fun StateData.widgetsDump(sep: String) = this.widgets.map { it.dataString(sep) }
