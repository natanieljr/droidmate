package org.droidmate.exploration.modelFeatures.graph

data class Vertex<S>(val data: S,
					 private val stateComparison: (S, S) -> Boolean /*= { a, b -> a == b}*/ ) {
	override fun toString(): String {
		return "$data"
	}

	@Suppress("UNCHECKED_CAST")
	override fun equals(other: Any?): Boolean {
		return other != null &&
				this.javaClass.isInstance(other) &&
				stateComparison((other as Vertex<S>).data, this.data)
	}

    override fun hashCode(): Int = data!!.hashCode()

}