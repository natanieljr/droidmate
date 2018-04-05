package org.droidmate.exploration.statemodel.features

/** used to increase the counter value of a map **/
internal inline fun <reified K> MutableMap<K, Int>.incCnt(id: K): MutableMap<K, Int> = this.apply {
	compute(id, { _, c ->
		c?.inc() ?: 1
	})
}

/** use this function on a list, grouped by it's counter, to retrieve all entries which have the smallest counter value
 * e.g. numExplored(state).entries.groupBy { it.value }.listOfSmallest */
inline fun <reified K> Map<Int, List<K>>.listOfSmallest(): List<K>? = this[this.keys.fold(Int.MAX_VALUE, { res, c -> if (c < res) c else res })]

/** @return the sum of all counter over all context values [T] **/
inline fun <reified K, reified T> Map<K, Map<T, Int>>.sumCounter(id: K): Int = get(id)?.values?.sum() ?: 0
/** @return a specific counter value for context [T] */
inline fun <reified K, reified T> Map<K, Map<T, Int>>.getCounter(kId: K, vId: T): Int = get(kId)?.get(vId) ?: 0