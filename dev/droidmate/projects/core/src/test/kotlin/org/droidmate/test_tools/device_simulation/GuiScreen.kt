// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
package org.droidmate.test_tools.device_simulation

import com.google.common.base.MoreObjects
import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.apis.TimeFormattedLogcatMessage
import org.droidmate.device.datatypes.*
import org.droidmate.device.model.DeviceModel
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.errors.UnsupportedMultimethodDispatch
import org.droidmate.misc.MonitorConstants
import org.droidmate.test_tools.device.datatypes.GuiStateTestHelper
import org.droidmate.test_tools.device.datatypes.UiautomatorWindowDumpTestHelper
import org.droidmate.test_tools.device.datatypes.WidgetTestHelper
import org.droidmate.uiautomator_daemon.guimodel.*

/**
 * <p>
 * The time generator provides successive timestamps to the logs returned by the simulated device from a call to
 * {@link #perform(org.droidmate.uiautomator_daemon.guimodel.Action)}.
 *
 * </p><p>
 * If this object s a part of simulation obtained from exploration output the time generator is null, as no time needs to be
 * generated. Instead, all time is obtained from the exploration output timestamps.
 *
 * </p>
 */
class GuiScreen constructor(private val internalId: String, 
                            packageName : String = "", 
                            private val timeGenerator : ITimeGenerator? = null) : IGuiScreen {
    //private static final String packageAndroidLauncher = new DeviceConfigurationFactory(UiautomatorDaemonConstants.DEVICE_DEFAULT).getConfiguration().getPackageAndroidLauncher()
    companion object {
        const val idHome = "home"
        const val idChrome = "chrome"
        val reservedIds = arrayListOf(idHome, idChrome)
        val reservedIdsPackageNames = mapOf(
                idHome to DeviceModel.buildDefault().getAndroidLauncherPackageName(),
                idChrome to "com.android.chrome")

        fun getSingleMatchingWidget(action: ClickAction, widgets: List<Widget>): Widget {
            return widgets.find { w->w.xpath==action.xPath }!!
        }

        fun getSingleMatchingWidget(action: CoordinateClickAction, widgets: List<Widget>): Widget {
            return widgets.find { w->w.bounds.contains(action.x, action.y) }!!
        }

    }

    private val packageName: String
    private var internalGuiSnapshot: IDeviceGuiSnapshot = MissingGuiSnapshot()

    private var home: IGuiScreen? = null
    private var main: IGuiScreen? = null

    private val widgetTransitions: MutableMap<Widget, IGuiScreen> = mutableMapOf()
    private var finishedBuilding = false

    constructor(snapshot: IDeviceGuiSnapshot) : this(snapshot.id, snapshot.getPackageName()) {
        this.internalGuiSnapshot = snapshot
    }


    init {
        this.packageName = if (packageName.isNotEmpty()) packageName else reservedIdsPackageNames[internalId]!!

        assert(this.internalId.isNotEmpty())
        assert(this.packageName.isNotEmpty())
        assert((this.internalId !in reservedIds) || (this.packageName == reservedIdsPackageNames[internalId]))
        assert((this.internalId in reservedIds) || (this.packageName !in reservedIdsPackageNames.values))
    }

    override fun perform(action: Action): IScreenTransitionResult {
        assert(finishedBuilding)
        return when (action) {
            // TODO review
            is SimulationAdbClearPackage -> internalPerform(action)
            is LaunchApp -> internalPerform(action)
            is ClickAction -> internalPerform(action)
            is CoordinateClickAction -> internalPerform(action)
            is LongClickAction -> internalPerform(action)
            is CoordinateLongClickAction -> internalPerform(action)
            else -> throw UnsupportedMultimethodDispatch(action)
        }
    }

    //region internalPerform multimethod

    // This method is used: it is a multimethod.
    private fun internalPerform(clearPackage: SimulationAdbClearPackage): IScreenTransitionResult {
        return if (this.getGuiSnapshot().getPackageName() == clearPackage.packageName)
            ScreenTransitionResult(home!!, ArrayList())
        else
            ScreenTransitionResult(this, ArrayList())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun internalPerform(launch: LaunchApp): IScreenTransitionResult =
            ScreenTransitionResult(main!!, this.buildMonitorMessages())

    private fun internalPerform(action: Action): IScreenTransitionResult {
        return when (action) {
            is PressHome -> ScreenTransitionResult(home!!, ArrayList())
            is EnableWifi -> {
                assert(this == home)
                ScreenTransitionResult(this, ArrayList())
            }
            is PressBack -> ScreenTransitionResult(this, ArrayList())
            is ClickAction -> {
                val widget = getSingleMatchingWidget(action, widgetTransitions.keys.toList())
                ScreenTransitionResult(widgetTransitions[widget]!!, ArrayList())
            }
            is CoordinateClickAction -> {
                val widget = getSingleMatchingWidget(action, widgetTransitions.keys.toList())
                ScreenTransitionResult(widgetTransitions[widget]!!, ArrayList())
            }
            else -> throw UnexpectedIfElseFallthroughError("Found action $action")
        }
    }

    //endregion internalPerform multimethod

    override fun addWidgetTransition(widgetId: String, targetScreen: IGuiScreen, ignoreDuplicates: Boolean) {
        assert(!finishedBuilding)
        assert(this.internalId !in reservedIds)
        assert(ignoreDuplicates || !(widgetTransitions.keys.any { it.id.contains(widgetId) }))

        if (!(ignoreDuplicates && widgetTransitions.keys.any { it.id.contains(widgetId) })) {
            val widget = if (this.getGuiSnapshot() !is MissingGuiSnapshot)
                this.getGuiSnapshot().guiState.widgets.single { it.id == widgetId }
            else
                WidgetTestHelper.newClickableWidget(mutableMapOf("uid" to widgetId), /* widgetGenIndex */ widgetTransitions.keys.size)

            widgetTransitions[widget] = targetScreen
        }

        assert(widgetTransitions.keys.any { it.id.contains(widgetId) })
    }

    override fun addHomeScreenReference(home: IGuiScreen) {
        assert(!finishedBuilding)
        assert(home.getId() == idHome)
        this.home = home
    }

    override fun addMainScreenReference(main: IGuiScreen) {
        assert(!finishedBuilding)
        assert(main.getId() !in reservedIds)
        this.main = main
    }

    override fun buildInternals() {
        assert(!this.finishedBuilding)
        assert(this.getGuiSnapshot() is MissingGuiSnapshot)

        val widgets = widgetTransitions.keys
        when (internalId) {
            !in reservedIds -> {
                val guiState = if (widgets.isEmpty()) {
                    buildEmptyInternals()
                } else
                    GuiStateTestHelper.newGuiStateWithWidgets(
                            widgets.size, packageName, /* enabled */ true, internalId, widgets.map { it.id })

                this.internalGuiSnapshot = UiautomatorWindowDumpTestHelper.fromGuiState(guiState)

            }
            idHome -> this.internalGuiSnapshot = UiautomatorWindowDumpTestHelper.newHomeScreenWindowDump(this.internalId)
            idChrome -> this.internalGuiSnapshot = UiautomatorWindowDumpTestHelper.newAppOutOfScopeWindowDump(this.internalId)
            else -> throw UnexpectedIfElseFallthroughError("Unsupported reserved uid: $internalId")
        }

        assert(this.getGuiSnapshot().id.isNotEmpty())
    }

    private fun buildEmptyInternals(): IGuiStatus {
        val guiState = GuiStateTestHelper.newGuiStateWithTopLevelNodeOnly(packageName, internalId)
        // This one widget is necessary, as it is the only xml element from which packageName can be obtained. Without it, following
        // method would fail: UiautomatorWindowDump.getPackageName when called on
        // org.droidmate.exploration.device.simulation.GuiScreen.guiSnapshot.
        assert(guiState.widgets.size == 1)
        return guiState
    }

    override fun verify() {
        assert(!finishedBuilding)
        this.finishedBuilding = true

        assert(this.home?.getId() == idHome)
        assert(this.main?.getId() !in reservedIds)
        assert(this.getGuiSnapshot().id.isNotEmpty())
        assert(this.getGuiSnapshot().guiState.id.isNotEmpty())
        // TODO: Review later
        //assert((this.internalId in reservedIds) || (this.widgetTransitions.keys.map { it.uid }.sorted() == this.getGuiSnapshot().guiStatus.getActionableWidgets().map { it.uid }.sorted()))
        assert(this.finishedBuilding)
    }

    private fun buildMonitorMessages(): List<ITimeFormattedLogcatMessage> {
        return listOf(
                TimeFormattedLogcatMessage.from(
                        this.timeGenerator!!.shiftAndGet(mapOf("milliseconds" to 1500)), // Milliseconds amount based on empirical evidence.
                        MonitorConstants.loglevel.toUpperCase(),
                        MonitorConstants.tag_mjt,
                        "4224", // arbitrary process ID
                        MonitorConstants.msg_ctor_success),
                TimeFormattedLogcatMessage.from(
                        this.timeGenerator.shiftAndGet(mapOf("milliseconds" to 1810)), // Milliseconds amount based on empirical evidence.
                        MonitorConstants.loglevel.toUpperCase(),
                        MonitorConstants.tag_mjt,
                        "4224", // arbitrary process ID
                        MonitorConstants.msgPrefix_init_success + this.packageName)
        )
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
                .add("uid", internalId)
                .toString()
    }

    override fun getId(): String = this.internalId

    override fun getGuiSnapshot(): IDeviceGuiSnapshot = this.internalGuiSnapshot

    override fun addWidgetTransition(widgetId: String, targetScreen: IGuiScreen) {
        addWidgetTransition(widgetId, targetScreen, false)
    }
}