package org.droidmate.exploration.statemodel.features.graph

import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData
import java.util.UUID

class Graph{
	var adjacencyMap: MutableMap<UUID, MutableList<Edge>> = mutableMapOf()

	private fun addDirectedEdge(source: StateData, destination: StateData, transition: ActionData, weight: Double): Edge {
		val edge = Edge(source, destination, transition, weight)
		adjacencyMap[source.uid]?.add(edge)

		return edge
	}

	@JvmOverloads
	fun add(source: StateData, destination: StateData, label: ActionData, weight: Double = 0.0): Edge {
		val edge = edge(source, destination, label)
		if (edge != null)
			return edge

		return addDirectedEdge(source, destination, label, weight)
	}

	/*fun hasNode(node: StateData): Boolean = adjacencyMap[node.uid] != null

	fun hasEdge(source: StateData, destination: StateData, label: ActionData): Boolean {
		return edge(source, destination, label) != null
	}*/

	fun edge(source: StateData, destination: StateData, label: ActionData): Edge? = edges(source)
			.firstOrNull{ it.destination.uid == destination.uid && it.transition.actionString() == label.actionString() }

	fun edges(source: StateData): List<Edge> = adjacencyMap[source.uid] ?: emptyList()

	override fun toString(): String {
		var result = ""
		for ((vertex, edges) in adjacencyMap) {
			var edgeString = ""
			for ((index, edge) in edges.withIndex()) {
				edgeString += if (index != edges.count() - 1) {
					"${edge.destination}, "
				} else {
					"${edge.destination}"
				}
			}
			result += "$vertex ---> [ $edgeString ] \n"
		}
		return result
	}

}