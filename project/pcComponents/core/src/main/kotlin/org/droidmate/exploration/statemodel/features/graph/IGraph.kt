package org.droidmate.exploration.statemodel.features.graph

interface IGraph<S, L>{
	val root: Vertex<S>

	fun add(source: S, destination: S?, label: L, updateIfExists: Boolean = true, weight: Double = 1.0): Edge<S, L>

	fun update(source: S, prevDestination: S?, newDestination: S, prevLabel: L, newLabel: L): Edge<S, L>?

	fun edge(order: Int): Edge<S, L>?

	fun edge(source: S, destination: S?, label: L): Edge<S, L>?

	fun edges(source: Vertex<S>): List<Edge<S, L>>

	fun edges(source: S, destination: S?): List<Edge<S, L>>

	fun edges(source: S): List<Edge<S, L>>

	fun ancestors(destination: S): List<Vertex<S>>

	fun isEmpty(): Boolean
}