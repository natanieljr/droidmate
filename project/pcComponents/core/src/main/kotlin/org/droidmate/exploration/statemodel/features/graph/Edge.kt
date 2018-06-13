package org.droidmate.exploration.statemodel.features.graph

data class Edge<S, T>(val source: Vertex<S, T>,
				val destination: Vertex<S, T>,
				val order: MutableList<Int>,
				val transition: T,
				var count: Int,
				val weight: Double){

	@JvmOverloads
	constructor(source: Vertex<S, T>,
				destination: Vertex<S, T>,
				order: Int,
				transition: T,
				count: Int = 1,
				weight: Double = 0.0): this(source, destination, mutableListOf(order), transition, count, weight)
}