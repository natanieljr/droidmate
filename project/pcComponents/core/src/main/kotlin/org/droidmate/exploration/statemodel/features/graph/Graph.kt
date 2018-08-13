package org.droidmate.exploration.statemodel.features.graph

class Graph<S, L>(root: S,
				  private val stateComparison : (S, S) -> Boolean = { a, b -> a == b },
				  private val labelComparison : (L, L) -> Boolean = { a, b -> a == b }): IGraph<S, L>{
	private val adjacencyMap: MutableMap<Vertex<S>, MutableList<Edge<S, L>>> = mutableMapOf()

	override val root: Vertex<S> = createVertex(root)

	var numEdges: Int = 0
		private set

	private fun createVertex(data: S): Vertex<S> {
		val vertex = Vertex(data, stateComparison)

		adjacencyMap[vertex] ?: run {
			adjacencyMap[vertex] = mutableListOf()
		}

		return vertex
	}

    fun contains(data: S): Boolean = getVertex(data) != null

    fun getVertex(data: S): Vertex<S>? = getVertices().find { stateComparison(it.data, data) }

	private fun addDirectedEdge(source: S, destination: S?, label: L, weight: Double): Edge<S, L> {
		val sourceVertex = createVertex(source)
		val destinationVertex = destination?.run { createVertex(destination) }

		val edge = Edge(sourceVertex, destinationVertex, numEdges++, label, labelComparison, 0, weight)

		adjacencyMap[sourceVertex]?.add(edge)

		return edge
	}

	private fun updateDirectedEdge(edge: Edge<S, L>): Edge<S, L>{
		edge.count++
		edge.order.add(numEdges++)
		return edge
	}

	override fun add(source: S, destination: S?, label: L, updateIfExists: Boolean, weight: Double): Edge<S, L> {
		val edge = edge(source, destination, label)

		return if (edge != null)
			if (updateIfExists)
				updateDirectedEdge(edge)
			else
				edge
		else
			addDirectedEdge(source, destination, label, weight)
	}

	override fun update(source: S, prevDestination: S?, newDestination: S, prevLabel: L, newLabel: L): Edge<S, L>?{
		return edge(source, prevDestination, prevLabel)?.apply {
			destination = createVertex(newDestination)
			label = newLabel
		}
	}

    override fun getVertices(): Set<Vertex<S>> = adjacencyMap.keys.toSet()

	override fun edge(order: Int): Edge<S, L>?{
		return adjacencyMap
				.map {
					it.value.firstOrNull { it.order.contains(order) }
				}
				.filterNotNull()
				.firstOrNull()
	}

    override fun edges(): List<Edge<S, L>> = adjacencyMap.values.flatten()

	override fun edges(source: Vertex<S>): List<Edge<S, L>> = adjacencyMap[source] ?: emptyList()

	override fun edges(source: S): List<Edge<S, L>> = edges(createVertex(source))

	override fun edges(source: S, destination: S?): List<Edge<S, L>> = edges(source)
			.filter { it.destination == destination }

	override fun edge(source: S, destination: S?, label: L): Edge<S, L>? = edges(source, destination)
			.firstOrNull{ it.label == label }

	override fun isEmpty(): Boolean {
		return adjacencyMap[root]?.isEmpty() ?: true
	}

	override fun ancestors(destination: S): List<Vertex<S>> {
		val targetVertex = createVertex(destination)
		return adjacencyMap
				.filter { p -> p.value.any { it.destination ==  targetVertex} }
				.map { it.key }
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