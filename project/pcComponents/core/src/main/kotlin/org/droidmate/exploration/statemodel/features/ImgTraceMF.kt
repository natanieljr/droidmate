// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
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
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.NonCancellable.isActive
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.configuration.ConfigProperties
import org.droidmate.exploration.statemodel.ModelConfig
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.misc.deleteDir
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.coroutines.experimental.CoroutineContext

/** use this function to create a sequence of screen images in which the interacted target is highlighted by a red boarder */
class ImgTraceMF(val cfg: ModelConfig) : ModelFeature() {
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ImgTraceMF"), parent = job)

	private val targetDir = (cfg.baseDir.resolve("imgTrace"))
	init {
		job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
		targetDir.deleteDir()
		Files.createDirectories(targetDir)
	}

	var i: AtomicInteger = AtomicInteger(0)
	override suspend fun onNewInteracted(targetWidget: Widget?, prevState: StateData, newState: StateData){
		// check if we have any screenshots to process
		if(!cfg[ConfigProperties.ModelProperties.imgDump.states]) return

		val step = i.incrementAndGet()-1
		val screenFile = java.io.File(cfg.statePath(prevState.stateId, fileExtension = ".png"))

		while(isActive && !screenFile.exists()) delay(100)
		while(isActive && !screenFile.canRead()) delay(100)
		if(!isActive) return
		if(!screenFile.exists()) return // thread was canceled but no file to process is ready yet

		val targetFile = File("${targetDir.toAbsolutePath()}${File.separator}$step.png")
		if(targetWidget == null) {		// move file to trace directory
			screenFile.copyTo(targetFile, overwrite = true)
			return
		}

		val stateImg = ImageIO.read(screenFile)
		stateImg.createGraphics().apply{
			paint = Color.red
			stroke = BasicStroke(10F)
			drawOval(targetWidget.bounds)
		}
		ImageIO.write(stateImg,"png",targetFile)
	}

	private fun Graphics.drawOval(bounds: Rectangle){
		this.drawOval(bounds.x,bounds.y,bounds.width,bounds.height)
	}
}