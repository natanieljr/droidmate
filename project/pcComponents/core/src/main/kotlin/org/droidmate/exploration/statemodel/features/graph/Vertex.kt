package org.droidmate.exploration.statemodel.features.graph

data class Vertex<S, T> @JvmOverloads constructor(val data: S, val edges: MutableList<Edge<S, T>> = mutableListOf()){

	fun add(edge: Edge<S, T>){
		if (!edges.contains(edge))
			edges.add(edge)
	}

	override fun equals(other: Any?): Boolean {
		return other != null &&
				data == other
	}
}