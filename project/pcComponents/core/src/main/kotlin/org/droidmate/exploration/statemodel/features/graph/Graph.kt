package org.droidmate.exploration.statemodel.features.graph

import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData

class Graph: IGraph<StateData, ActionData>{
	var adjacencyMap: MutableSet<Vertex<StateData, ActionData>> = mutableSetOf()

	private var root: Vertex<StateData, ActionData>? = null

	override fun root(): Vertex<StateData, ActionData>? {
		return root
	}

	var numEdges: Int = 0
		private set

	private fun MutableSet<Vertex<StateData, ActionData>>.get(stateData: StateData): Vertex<StateData, ActionData>? {
		return this.firstOrNull { it.data.uid == stateData.uid }
	}

	override fun vertex(stateData: StateData): Vertex<StateData, ActionData>? {
		return adjacencyMap.get(stateData)
	}

	private fun addVertex(stateData: StateData): Vertex<StateData, ActionData>{
		val vertex = Vertex<StateData, ActionData>(stateData)
		if (root == null)
			root = vertex
		adjacencyMap.add(vertex)
		return vertex
	}

	private fun addDirectedEdge(source: StateData, destination: StateData, transition: ActionData, weight: Double): Edge<StateData, ActionData> {
		val sourceVertex = this.vertex(source) ?: addVertex(source)
		val destinationVertex = this.vertex(destination) ?: addVertex(destination)

		val edge = Edge(sourceVertex, destinationVertex, numEdges++, transition, 1, weight)

		sourceVertex.add(edge)

		return edge
	}

	override fun add(source: StateData, destination: StateData, label: ActionData, weight: Double): Edge<StateData, ActionData> {
		val edge = edge(source, destination, label)
		if (edge != null) {
			edge.count++
			edge.order.add(numEdges++)
			return edge
		}

		return addDirectedEdge(source, destination, label, weight)
	}

	override fun edge(order: Int): Edge<StateData, ActionData>?{
		return adjacencyMap
				.map {
					it.edges.firstOrNull { it.order.contains(order) }
				}
				.filterNotNull()
				.firstOrNull()
	}

	override fun edge(source: StateData, destination: StateData, transition: ActionData): Edge<StateData, ActionData>? = edges(source)
			.firstOrNull{ it.destination.data.uid == destination.uid && it.transition.actionString() == transition.actionString() }

	override fun edges(vertex: Vertex<StateData, ActionData>): List<Edge<StateData, ActionData>> = edges(vertex.data)

	override fun edges(source: StateData, destination: StateData): List<Edge<StateData, ActionData>> = edges(source)
			.filter { it.destination.data.uid == destination.uid }

	override fun edges(source: StateData): List<Edge<StateData, ActionData>> = adjacencyMap.get(source)?.edges ?: emptyList()

	override fun isEmpty(): Boolean {
		return this.root != null
	}

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