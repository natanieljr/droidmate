package org.droidmate.device.datatypes.statemodel

import org.droidmate.device.datatypes.Widget
import org.droidmate.device.datatypes.Widget.Companion.widgetHeader
import java.io.File
import java.util.*

/**
 * States have two components, the Id determined by its Widgets image, text and description and the ConfigId defined by the WidgetsProperties
 */
typealias StateId = Pair<UUID,UUID>
fun stateIdFromString(s:String) = s.split("_").let{ StateId(UUID.fromString(it[0]),UUID.fromString(it[1]))}
fun StateId.toString() = "${first}_$second"
val emptyId = StateId(emptyUUID, emptyUUID)

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
	val stateId get() = StateId(uid,configId)
  val actionableWidgets get() = widgets.filter { it.canBeActedUpon() }


	init{	// ignore nonInteractive parent views from ID computations to better re-identify state unique ids but consider them for the configIds
		val ids = widgets.fold(emptyId,{ (id,configId):StateId, widget -> StateId( addRelevantId(id, widget), configId + widget.propertyConfigId ) })
		uid = ids.first
		configId = ids.second
	}
	/** this function is used to add any widget.uid if it fulfills specific criteria (i.e. it can be acted upon, has text content or it is a leaf) */
	private fun addRelevantId(id:UUID,w:Widget):UUID = if(w.isLeaf() || w.canBeActedUpon() || w.hasContent()) id+w.uid else id

  /** write CSV
   *
   * [uid] => state_id as file name
   */
  fun dump(config: ModelDumpConfig){
//    if(wordCloud.isEmpty()){ File(config.statePath(uid)).createNewFile(); return }
//    File(config.statePath(uid)).bufferedWriter().use { out ->
//      wordMatrix.forEachIndexed { i: Int, vector: Deferred<List<Double>> ->
//        out.write(wordCloud.elementAt(i))
//        out.write(sep)
//        out.write(vector.await().joinToString(separator = sep))
//        out.newLine()
//      }
//    }
    File(config.widgetFile(stateId)).bufferedWriter().use { all ->
//    File(config.statePath(uid, postfix = "_widgets",fileExtension = "txt")).bufferedWriter().use { sum ->  // short content-summarizing text file
//      File(config.statePath(uid,postfix = config.sTextWidget)).bufferedWriter().use { tw ->
//        File(config.statePath(uid,postfix = config.sWidget)).bufferedWriter().use { w ->
//          tw.write(widgetHeader)
//          w.write(widgetHeader)
          all.write(widgetHeader)

          widgets.forEach {
            all.newLine()
            all.write(it.dataString)
//            if(it.wordCloud.isNotEmpty()){
//              tw.newLine()
//              tw.write(it.dataString)
//              sum.write(it.wordCloud.toString().padEnd(41))
//              sum.write(";\t\tinteractive=${it.interactive};\t\t${it.uuid}")
//              sum.newLine()
//            }
//            else{
//              w.newLine()
//              w.write(it.dataString)
//            }
//          }
//        }
//      }
    }}
  }

  companion object {
    // this is basically extending the primary constructor
    operator fun invoke(widgets: Set<Widget>): StateData = StateData(widgets.sortedBy { it.uid.toString() })

    // to load the model from previously stored files
    fun fromFile(widgets: Set<Widget>, filePath:File): StateData = StateData(widgets.sortedBy { it.uid.toString() })
    fun emptyState(): StateData = StateData(emptySet())//.apply { wordMatrix = emptyArray() }
  }

  override fun toString(): String {
    return "StateData[uuid=$uid, configId=$configId, widgets=${widgets.size}]"
  }
}
