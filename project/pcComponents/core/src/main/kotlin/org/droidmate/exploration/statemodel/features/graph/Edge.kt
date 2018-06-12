package org.droidmate.exploration.statemodel.features.graph

import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData

data class Edge(var source: StateData, var destination: StateData, val transition: ActionData, val weight: Double)