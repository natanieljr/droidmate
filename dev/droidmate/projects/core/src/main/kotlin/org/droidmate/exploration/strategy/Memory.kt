// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.exploration.strategy

import org.droidmate.android_sdk.IApk
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.droidmate.storage.FSTLocalDateTimeSerializer
import org.droidmate.storage.FSTURISerializer
import org.nustaq.serialization.FSTConfiguration
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Exploration memory containing action log (memory records), all explored widget contexts and
 * last explored widget
 *
 * @author Nataniel P. Borges Jr.
 */
class Memory : Serializable {


    /**
     * List of [GUI states and actions][IMemoryRecord] which were sent to the device
     */
    private val memoryRecords: MutableList<IMemoryRecord> = ArrayList()

    /**
     * List of distinct [UI contexts][WidgetContext] which have been found during the exploration
     */
    private var foundWidgetContexts: MutableList<WidgetContext> = ArrayList()

    /**
     * Last widget the exploration has interacted with (or null when no widget has been interacted with)
     */
    var lastWidgetInfo: WidgetInfo? = null

    /**
     * Application linked to the exploration
     */
    private val apk: IApk?

    /**
     * Constructor which does not set an APK. Used when the [strategy pool][IStrategyPool] creates initializes the memory
     */
    constructor () {
        this.apk = null
    }

    /**
     * Constructor with an APK. Used when the [strategy pool][IStrategyPool] begins an exploration
     */
    constructor (apk: IApk) {
        this.apk = apk
    }

    // region exploration progress

    /**
     * Creates a log entry containing the [action sent to the device][action],
     * [type of the strategy which create the action][type], [state of the UI when the action was created][widgetContext]
     * and [moment in which the strategy started selecting an action][startTimestamp] to send to the device
     */
    fun logProgress(action: ExplorationAction, type: ExplorationType, widgetContext: WidgetContext,
                    startTimestamp: LocalDateTime) {
        val endTimestamp = LocalDateTime.now()
        val decisionTime = ChronoUnit.MILLIS.between(startTimestamp, endTimestamp)
        val record = MemoryRecord(action, type, widgetContext, startTimestamp, endTimestamp, decisionTime)
        logger.debug("Writing memory record $record")
        memoryRecords.add(record)
    }

    fun addResultToLastAction(result: IExplorationActionRunResult) {
        val last = this.memoryRecords.last()
        last.actionResult = result
    }

    /**
     * Get data stored during this information
     *
     * @return List of memory records (or empty list when empty)
     */
    fun getRecords(): List<IMemoryRecord> {
        return this.memoryRecords
    }

    fun getSize(): Int {
        return this.memoryRecords.size
    }

    /**
     * Checks if any action has been performed
     *
     * @return If the memory is empty
     */
    fun isEmpty(): Boolean {
        return this.memoryRecords.isEmpty()
    }

    /**
     * Returns the information of the last action performed
     *
     * @return Information of the last action performed
     */
    fun getLastAction(): IMemoryRecord? {
        return this.memoryRecords.lastOrNull()
    }

    /**
     * Get the application to which this memory refers to
     */
    fun getApk(): IApk {
        return apk!!
    }

    // endregion

    // region widget info

    /**
     * Get the widget context refering to the [current UI][guiState] and to the
     * [top level package element on UIAutomator dump][exploredAppPackageName].
     *
     * Creates a new unique context when it doesn't exist.
     *
     * @return Unique widget context which refers to the current screen
     */
    fun getWidgetContext(guiState: IGuiState, exploredAppPackageName: String): WidgetContext {
        val widgetInfo = guiState.widgets
                //.filter { it.canBeActedUpon() }
                .map { widget -> WidgetInfo.from(widget) }

        val newContext = WidgetContext(widgetInfo, guiState, exploredAppPackageName)
        var context = this.foundWidgetContexts
                .firstOrNull { p -> p.uniqueString == newContext.uniqueString }

        if (context == null) {
            context = newContext
            this.foundWidgetContexts.add(context)
        }

        return context
    }

    fun areAllWidgetsExplored(): Boolean {
        return (!this.isEmpty()) &&
                this.foundWidgetContexts.isNotEmpty() &&
                this.foundWidgetContexts.all { context ->
                    context.actionableWidgetsInfo.all { it.actedUponCount > 0 }
                }
    }

    // endregion

    companion object {
        private val logger = LoggerFactory.getLogger(Memory::class.java)
        private const val serialVersionUID: Long = 2//5863474084614689736

        private val serializationConfig = FSTConfiguration.createJsonConfiguration(true, false)
                .apply {
                    registerSerializer(URI::class.java, FSTURISerializer(), false)
                    registerSerializer(LocalDateTime::class.java, FSTLocalDateTimeSerializer(), false)
                }


        @Throws(IOException::class)
        fun serialize(memoryData: List<Memory>, outPath: Path) {
            logger.info("Serializing experiment memory to $outPath")

            val fileOut = FileOutputStream(outPath.toFile())
            val out = serializationConfig.getObjectOutput(fileOut)
            out.writeObject(memoryData)
            out.close()
            fileOut.close()
            logger.info("Experiment memory successfully serialized in $outPath")
        }

        @Throws(IOException::class)
        fun deserialize(memoryFile: Path): List<Memory> {
            logger.info("Deserializing experiment memory from $memoryFile")

            val fileIn = FileInputStream(memoryFile.toFile())
            val reader = serializationConfig.getObjectInput(fileIn)
            val memoryData = (reader.readObject() as List<*>).filterIsInstance<Memory>()
            reader.close()
            fileIn.close()
            logger.info("Experiment memory successfully deserialized from $memoryFile")

            return memoryData
        }
    }
}