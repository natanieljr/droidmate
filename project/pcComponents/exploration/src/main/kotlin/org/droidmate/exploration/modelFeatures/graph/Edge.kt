package org.droidmate.exploration.modelFeatures.graph

data class Edge<S, L>(val source: Vertex<S>,
                      var destination: Vertex<S>?,
                      val order: MutableList<Int>,
                      var label: L,
                      private val labelComparison : (L, L) -> Boolean = { a, b -> a == b },
                      var count: Int,
                      val weight: Double){

	@JvmOverloads
	constructor(source: Vertex<S>,
	            destination: Vertex<S>?,
	            order: Int,
	            label: L,
	            labelComparison : (L, L) -> Boolean,
	            count: Int = 1,
	            weight: Double = 1.0): this(source, destination, mutableListOf(order), label, labelComparison, count, weight)

	@Suppress("UNCHECKED_CAST")
	override fun equals(other: Any?): Boolean {
		if (other == null || !this.javaClass.isInstance(other))
			return false

        val otherEdge = (other as Edge<S, L>)
        return labelComparison(otherEdge.label, this.label) && other.source == source && other.destination == destination
	}

    override fun hashCode(): Int {
        return arrayOf(source, destination, label).contentDeepHashCode()
    }
}