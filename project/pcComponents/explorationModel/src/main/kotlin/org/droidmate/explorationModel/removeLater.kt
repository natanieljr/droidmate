package org.droidmate.explorationModel


@Suppress("MemberVisibilityCanBePrivate")
enum class P(var header: String = "") {
	UID, WdId(header = "data UID"),
	Type("widget class"),
	Interactive,
	//	Coord,
	Text,
	Desc("Description"),
	ParentID(header = "parentID"),
	Enabled,
	Visible,
	Clickable,
	LongClickable,
	Scrollable,
	Checked,
	Editable,
	Focused,
	Selected,
	IsPassword,
	BoundsX,
	BoundsY,
	BoundsWidth,
	BoundsHeight,
	ResId("Resource Id"),
	XPath,
	//	IsLeaf,
	PackageName,
	ImgId,
	UsedforStateId,
	HashId;

	init {
		if (header == "") header = name
	}

	fun idx(indexMap:Map<P,Int> = defaultIndicies): Int {
		return if (indexMap[this] == null) {
//            println("Missing field $this")
			Integer.MAX_VALUE
		} else  {
			indexMap[this]!!
		}
	}

	/**
	 * execute a given function body only if this enum entry can be contained in the line (by ordinal)
	 * [body] gets the respective string value from line as input parameter
	 **/
	fun execIfSet(line:List<String>, indexMap: Map<P, Int>, body:(String)->Unit){
		val idx = this.idx(indexMap)
		if(line.size>idx) body( line[idx] )
	}

	companion object {
		@JvmStatic
		val defaultIndicies = P.values().associate { it to it.ordinal }
}}
