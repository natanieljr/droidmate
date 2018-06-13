package org.droidmate.exploration.statemodel.features.graph

import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData

interface IGraph<S, T>{
	fun root(): Vertex<S, T>?

	fun add(source: StateData, destination: StateData, label: ActionData, weight: Double = 0.0): Edge<S, T>

	fun vertex(stateData: StateData): Vertex<S, T>?

	fun edges(source: StateData, destination: StateData): List<Edge<S, T>>

	fun edges(vertex: Vertex<S, T>): List<Edge<S, T>>

	fun edges(source: S): List<Edge<S, T>>

	fun edge(source: S, destination: S, transition: T): Edge<S, T>?

	fun edge(order: Int): Edge<S, T>?

	fun isEmpty(): Boolean
}