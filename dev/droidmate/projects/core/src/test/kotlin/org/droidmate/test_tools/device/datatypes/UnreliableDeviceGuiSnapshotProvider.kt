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
package org.droidmate.test_tools.device.datatypes

import org.droidmate.device.datatypes.IDeviceGuiSnapshot

class UnreliableDeviceGuiSnapshotProvider(private val originalGuiSnapshot: IDeviceGuiSnapshot) : IUnreliableDeviceGuiSnapshotProvider {
    override fun provide(): IDeviceGuiSnapshot {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun pressOkOnAppHasStopped() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCurrentWithoutChange(): IDeviceGuiSnapshot {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    // TODO Fix tests
    /*companion object {
        private val log = LoggerFactory.getLogger(UnreliableDeviceGuiSnapshotProvider::class.java)
    }

    private val emptyGuiSnapshot = UiautomatorWindowDumpTestHelper.newEmptyWindowDump()
    private val appHasStoppedOKDisabledGuiSnapshot = UiautomatorWindowDumpTestHelper.newAppHasStoppedDialogOKDisabledWindowDump()
    private val appHasStoppedGuiSnapshot = UiautomatorWindowDumpTestHelper.newAppHasStoppedDialogWindowDump()
    private val guiSnapshotsSequence = arrayListOf(
            emptyGuiSnapshot,
            appHasStoppedOKDisabledGuiSnapshot,
            appHasStoppedGuiSnapshot
    )

    private var currentGuiSnapshot: IDeviceGuiSnapshot = guiSnapshotsSequence.first()

    private var okOnAppHasStoppedWasPressed = false

    override fun pressOkOnAppHasStopped() {
        assert(!this.okOnAppHasStoppedWasPressed)
        assert(guiSnapshotsSequence.last() == currentGuiSnapshot)
        this.okOnAppHasStoppedWasPressed = true

        this.currentGuiSnapshot = originalGuiSnapshot
    }

    override fun getCurrentWithoutChange(): IDeviceGuiSnapshot {
        return this.currentGuiSnapshot
    }

    override fun provide(): IDeviceGuiSnapshot {
        log.trace("provide($currentGuiSnapshot)")

        val out = this.currentGuiSnapshot

        if (currentGuiSnapshot != guiSnapshotsSequence.last() && currentGuiSnapshot != originalGuiSnapshot)
            this.currentGuiSnapshot = this.guiSnapshotsSequence[guiSnapshotsSequence.indexOf(currentGuiSnapshot) + 1]

        return out
    }*/
}
