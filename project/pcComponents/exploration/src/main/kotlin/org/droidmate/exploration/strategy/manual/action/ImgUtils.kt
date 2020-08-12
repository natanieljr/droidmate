package org.droidmate.exploration.strategy.manual.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.reporter.highlightWidget
import org.droidmate.exploration.modelFeatures.reporter.shapeColor
import org.droidmate.exploration.modelFeatures.reporter.shapeStroke
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO

val log: Logger = getLogger("ImageUtils")
suspend fun showTargetsInImg(
	eContext: ExplorationContext<*,*,*>,
	toBeHighlighted: List<Widget>,
	imgFile: File, autoGenScreen: Boolean = true,
	imgSrcDir: Path = eContext.model.config.imgDst,
	drawOval: Boolean = false,
	unique: Boolean = false // for strongly fragmented elements only draw the biggest area
) {
	withContext(Dispatchers.IO) {
		val trace = eContext.explorationTrace
		// wait until file was pulled from device (done in ExploreCommand)
		eContext.imgTransfer.coroutineContext[Job]?.children?.forEach { it.join() }

		val filePath = if(autoGenScreen)
			imgSrcDir.resolve("${trace.last()?.actionId}.jpg").toString()
		// the ExtendedTrace did not yet finish to move/ copy the file to the new location
		//trace.dstImgDir.resolve("${trace.interactions}.jpg").toString()
		else ""
		val screenFile = File(filePath)

		log.debug("try to fetch image from $filePath")
		if(!screenFile.exists()) { // need to re-fetch we have no screenshot
			log.warn("no screenshot to highlight targets")

//			var noScreen: Boolean
//			do {
//				println("please manually put an screenshot at location ${screenFile.absolutePath} and press enter to highlight targets in that file or enter 'n' to proceed without screenshot.\n$> ")
//				noScreen = readLine()?.trim()?.toLowerCase() == "n"
//			} while (!screenFile.exists() && !noScreen)

//			if(noScreen)
				return@withContext
		}
		//FIXME error load "PNG" for pixel device with Android 9 seams unreliable
		log.debug("load img ${screenFile.path}")
		var stateImg = ImageIO.read(screenFile)
		if (stateImg == null) stateImg = ImageIO.read(screenFile)
		if (stateImg == null) {
			println("error on image load"); return@withContext
		}
		shapeColor = Color.cyan
		highlightWidget(stateImg, toBeHighlighted, unique = unique)
		if(drawOval) stateImg.createGraphics().apply { // highlight the boundaries if layout problems prevent us from detecting underlying elements
			stroke = shapeStroke
			toBeHighlighted.forEachIndexed { i,w ->
				font = Font("TimesRoman", Font.PLAIN, 40)
				paint = Color.CYAN.darker()
				with(w.boundaries) {
					// draw the label number for the element
					drawOval(w.boundaries)
					val text = "$i: "
					if (text.length > 20)
						font = Font("TimesRoman", Font.PLAIN, 30)
					paint = Color.red // text in different color for better visibility
					drawString(text, leftX + 10, bottomY-8)
				}
			}
		}
		log.info("write image to ${imgFile.absolutePath}")
		ImageIO.write(stateImg, screenFile.extension, imgFile)

	}
}

fun Graphics.drawOval(bounds: Rectangle){
	this.drawOval(bounds.leftX,bounds.topY,bounds.width,bounds.height)
}

