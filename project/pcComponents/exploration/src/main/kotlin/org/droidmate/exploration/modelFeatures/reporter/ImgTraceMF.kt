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

@file:Suppress("unused")

package org.droidmate.exploration.modelFeatures.reporter

import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.isComplete
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.misc.deleteDir
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext

var lastId = 0
/** use this function to create a sequence of screen images in which the interacted target is highlighted by a red boarder */
class ImgTraceMF(val cfg: ModelConfig) : ModelFeature() {
	override val coroutineContext: CoroutineContext = CoroutineName("ImgTraceMF")

	private val targetDir = (cfg.baseDir.resolve("ModelFeatures/imgTrace"))
	init {
		targetDir.deleteDir()
		Files.createDirectories(targetDir)
	}

	var i: AtomicInteger = AtomicInteger(0)
	override suspend fun onNewInteracted(traceId: UUID, actionIdx: Int, action: ExplorationAction, targetWidgets: List<Widget>, prevState: State, newState: State) {
		try {
			val step = i.incrementAndGet() - 1
			val screenFile = java.io.File(cfg.imgDst.resolve("$lastId.jpg").toString())
			val targetFile = File("${targetDir.toAbsolutePath()}${File.separator}$step--$lastId-$action.png")
			lastId = action.id

			if (!isActive) return
			var t=0
			while ( isActive && t<20 && (!screenFile.exists() || !screenFile.canRead())) {
				t++; delay (300)
			}  // wait until file was written in model-update

			if (!screenFile.exists()) return // thread was canceled but no file to process is ready yet

			if (prevState.widgets.isEmpty()) return  // no widgets exist which we could highlight
			withContext(Dispatchers.IO) {
				screenFile.copyTo(targetFile, overwrite = true) // copy file to trace directory

				ImageIO.read(screenFile)?.let{ stateImg ->
					val visibleAppElements = prevState.widgets.filter { !it.isKeyboard && it.canInteractWith }
					textColor = Color.white
//		shapeColor = Color.LIGHT_GRAY
//		highlightWidget(stateImg, visibleAppElements.filter { !it.hasUncoveredArea }, 0)
//		shapeColor = Color.GRAY
//		highlightWidget(stateImg, visibleAppElements.filter { it.hasUncoveredArea }, 0)

					shapeColor = Color.black
					highlightWidget(stateImg, prevState.actionableWidgets.filter { it.isKeyboard }, 0)

//		textColor = Color.red
					shapeColor = Color.gray
					highlightWidget(stateImg, visibleAppElements.filter { !it.isKeyboard }, 0)

					textColor = Color.cyan
					shapeColor = Color.cyan
					highlightWidget(stateImg, prevState.actionableWidgets.filter { it.visibleAreas.isComplete() && !it.isKeyboard }, 0)

					textColor = Color.green
					shapeColor = Color.orange
					highlightWidget(stateImg, prevState.actionableWidgets.filter { !it.visibleAreas.isComplete() && !it.isKeyboard }, 0)

					if (targetWidgets.isNotEmpty()) {
						shapeColor = Color.red
						textColor = Color.magenta
						highlightWidget(stateImg, targetWidgets, step)
					}
					ImageIO.write(stateImg, "png", targetFile)
				}

			}
		}catch(e: Throwable){
			log.error(e.localizedMessage, e)
		}
	}
}

var shapeColor: Color = Color.red
var textColor: Color = Color.magenta.darker()
var textSize: Int = 100
fun highlightWidget(stateImg: BufferedImage, targetWidgets: List<Widget>, idxOffset: List<Int>){
	stateImg.createGraphics().apply{
		stroke = BasicStroke(10F)
		font = Font("TimesRoman", Font.PLAIN, textSize)

		val targetsPerAction = targetWidgets.asSequence().mapIndexed{ i, t -> Pair(idxOffset[i],t)}.groupBy { it.first }

		val targetCounter: MutableMap<ConcreteId,LinkedList<Pair<Int,Int>>> = HashMap() // used to compute offsets in the number string
		// compute the list of indices for each widget-target (for labeling)
		targetsPerAction.forEach{ (idx, targets) ->
			targets.forEachIndexed{ index, (_,t) ->
				targetCounter.compute(t.id) { _, indices -> (indices ?: LinkedList()).apply { add(Pair(idx,index)) }}
			}
		}
		// highlight all targets and update text labels
		targetWidgets.forEach{ w: Widget ->
			paint = shapeColor// reset color for next shape drawing

			stroke = BasicStroke(1F)
//			w.visibleAreas.forEach { drawRectangle(it) }
			stroke = BasicStroke(10F)
			with(w.visibleBounds) {
				drawRectangle(this)
				// draw the label number for the element
				val text = targetCounter[w.id]!!.joinToString(separator = ", ") { if (it.first != 0) "${it.first}.${it.second}" else "${it.second}" }
				if (text.length > 20) font = Font("TimesRoman", Font.PLAIN, 20)
				paint = textColor// for better visibility use a different color then the boarder
				drawString(text, leftX + (width / 10), topY + (height / 10))
			}
			font = Font("TimesRoman", Font.PLAIN, textSize) // reset font to bigger font
			if(w.isVisible) shapeColor = shapeColor.darker()
		}
	}
}
fun highlightWidget(stateImg: BufferedImage, targetWidgets: List<Widget>, idxOffset: Int = 0)
		= highlightWidget(stateImg, targetWidgets, (0 until targetWidgets.size).map { idxOffset })

private fun Int.resize() = if(this<5) 10 else this

fun Graphics.drawRectangle(bounds: Rectangle){
	this.drawRect(bounds.leftX,bounds.topY,bounds.width,bounds.height)
}
