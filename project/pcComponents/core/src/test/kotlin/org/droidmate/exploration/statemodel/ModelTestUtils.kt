package org.droidmate.exploration.statemodel

internal fun Widget.splittedDumpString(sep: String) = this.dataString(sep).split(sep).map { it.trim() }
internal fun StateData.widgetsDump(sep: String) = this.widgets.map { it.dataString(sep) }
