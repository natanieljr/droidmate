package org.droidmate.explorationModel

import org.droidmate.explorationModel.interaction.StateData
import org.droidmate.explorationModel.interaction.Widget

fun Widget.splittedDumpString(sep: String) = this.dataString(sep).split(sep).map { it.trim() }
fun StateData.widgetsDump(sep: String) = this.widgets.map { it.dataString(sep) }
