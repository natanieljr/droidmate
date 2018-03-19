package org.droidmate.device.datatypes.statemodel

import org.droidmate.device.datatypes.Widget
import org.droidmate.device.datatypes.Widget.Companion.widgetHeader
import java.io.File
import java.util.*

/**
 * States have two components, the Id determined by its Widgets image, text and description and the ConfigId defined by the WidgetsProperties
 */
typealias ConcreteId = Pair<UUID,UUID>
fun stateIdFromString(s:String) = s.split("_").let{ ConcreteId(UUID.fromString(it[0]),UUID.fromString(it[1]))}
fun ConcreteId.toString() = "${first}_$second"
val emptyId = ConcreteId(emptyUUID, emptyUUID)

class StateData private constructor(val widgets: List<Widget>,
                                    val topNodePackageName: String="", val androidLauncherPackageName: String="", //TODO check if androidLauncherPackageName really necessary
                                    val isHomeScreen: Boolean = false,
                                    val isAppHasStoppedDialogBox: Boolean = false,
                                    val isRequestRuntimePermissionDialogBox: Boolean = false,
                                    val isCompleteActionUsingDialogBox: Boolean = false,
                                    val isSelectAHomeAppDialogBox: Boolean = false,
                                    val isUseLauncherAsHomeDialogBox: Boolean = false ){

  constructor(widgets: Collection<Widget>, topNodePackageName:String, androidLauncherPackageName:String,
              isHomeScreen: Boolean, isAppHasStoppedDialogBox: Boolean,
              isRequestRuntimePermissionDialogBox: Boolean = false, isCompleteActionUsingDialogBox: Boolean,
              isSelectAHomeAppDialogBox: Boolean = false, isUseLauncherAsHomeDialogBox: Boolean
  )
        :this(widgets.sortedBy { it.uid.toString() },topNodePackageName, androidLauncherPackageName, isHomeScreen,
      isAppHasStoppedDialogBox,isRequestRuntimePermissionDialogBox,isCompleteActionUsingDialogBox,
      isSelectAHomeAppDialogBox,isUseLauncherAsHomeDialogBox)

  val uid: UUID
  val configId:UUID
	val stateId get() = ConcreteId(uid,configId)
	/** id computed like uid while ignoring all edit fields */
	val iEditId: UUID

  val actionableWidgets by lazy { widgets.filter { it.canBeActedUpon() } }
	val hasEdit: Boolean

	init{	// ignore nonInteractive parent views from ID computations to better re-identify state unique ids but consider them for the configIds
		val ids = widgets.fold( Triple(emptyUUID, emptyUUID, emptyUUID),
				{ (id, configId, iEdit):Triple<UUID,UUID,UUID>, widget ->
			Triple( addRelevantId(id, widget), configId + widget.propertyConfigId, addRelevantNonEdit(iEdit, widget) ) })
		uid = ids.first
		configId = ids.second
		iEditId = ids.third
		hasEdit = uid != iEditId
	}
	/** this function is used to add any widget.uid if it fulfills specific criteria (i.e. it can be acted upon, has text content or it is a leaf) */
	private fun addRelevantId(id:UUID,w:Widget):UUID = if(w.isLeaf || w.canBeActedUpon() || w.hasContent()) id+w.uid else id
	private fun addRelevantNonEdit(id:UUID,w:Widget):UUID = if(w.isEdit) addRelevantId(id,w) else id

	fun idWhenIgnoring(widgets:Collection<Widget>):UUID = widgets.fold(emptyUUID, {id,w ->
		if(!widgets.contains(w)) addRelevantId(id,w) else id
	})

	fun hasActionableWidgets() = actionableWidgets.isNotEmpty()

  /** write CSV
   *
   * [uid] => state_id as file name
   */
  fun dump(config: ModelDumpConfig){
	  File(config.widgetFile(stateId)).bufferedWriter().use { all ->
		  all.write(widgetHeader)

		  widgets.forEach {
			  all.newLine()
			  all.write(it.dataString)
		  }}
  }

  companion object {
    // this is basically extending the primary constructor
    @JvmStatic operator fun invoke(widgets: Set<Widget>): StateData = StateData(widgets.sortedBy { it.uid.toString() })

    // to load the model from previously stored files
    fun fromFile(widgets: Set<Widget>): StateData = StateData(widgets.sortedBy { it.uid.toString() })
	  @JvmStatic fun emptyState(): StateData = StateData(emptySet())//.apply { wordMatrix = emptyArray() }
  }

	override fun equals(other: Any?): Boolean {
		return when(other){
			is StateData -> uid == other.uid && configId == other.configId
			else -> false
		}
	}

	override fun hashCode(): Int {
		return uid.hashCode()+configId.hashCode()
	}

  override fun toString(): String {
    return "StateData[uuid=$uid, configId=$configId, widgets=${widgets.size}]"
  }
}
