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
package org.droidmate.report.apk

import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.report.misc.plot
import org.droidmate.misc.withExtension
import java.awt.Point
import java.awt.image.BufferedImage
import java.awt.image.Raster
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import javax.imageio.ImageIO

class EffectiveActions @JvmOverloads constructor(private val pixelDensity: Int = nexus5XPixelDensity,
                                                 private val includePlots: Boolean = true,
                                                 private val fileName: String = "effective_actions.txt") : ApkReport() {
	companion object {
		/**
		 * The conversion depends on the density of the device.
		 * To get the density of the device: Resources.getSystem().getDisplayMetrics().density
		 *      Here we use by default the density of the Nexus 5X used in the experiments (https://material.io/devices/)
		 *      Density Nexus 5x = 2.6
		 *
		 * @return
		 */
		private const val nexus5XPixelDensity: Int = (24 * 2.6).toInt()
	}

	override fun safeWriteApkReport(data: ExplorationContext, apkReportDir: Path) {
		val sb = StringBuilder()
		val header = "Time_Seconds\tTotal_Actions\tTotal_Effective\n"
		sb.append(header)

		val reportData: HashMap<Long, Pair<Int, Int>> = HashMap()

		// Ignore app start
		val records = data.actionTrace.getActions().drop(1)
		val nrActions = records.size
		val startTimeStamp = records.first().startTimestamp

		var totalActions = 1
		var effectiveActions = 1

		for (i in 1 until nrActions) {
			val prevAction = records[i - 1]
			val currAction = records[i]
			val currTimestamp = currAction.startTimestamp
			val currTimeDiff = ChronoUnit.SECONDS.between(startTimeStamp, currTimestamp)

			if (actionWasEffective(prevAction, currAction))
				effectiveActions++

			totalActions++

			reportData[currTimeDiff] = Pair(totalActions, effectiveActions)

			if (i % 100 == 0)
				log.info("Processing $i")
		}

		reportData.keys.sorted().forEach { key ->
			val value = reportData[key]!!
			sb.appendln("$key\t${value.first}\t${value.second}")
		}

		val reportFile = apkReportDir.resolve(fileName)
		Files.write(reportFile, sb.toString().toByteArray())

		if (includePlots) {
			log.info("Writing out plot $")
			this.writeOutPlot(reportFile)
		}
	}

	private fun actionWasEffective(prevAction: ActionData, currAction: ActionData): Boolean {

		return if ((prevAction.actionType != WidgetExplorationAction::class.java.simpleName) ||
				(currAction.actionType != WidgetExplorationAction::class.java.simpleName))
			true
		else {
			currAction.prevState != currAction.resState
		}
	}

	private fun writeOutPlot(dataFile: Path) {
		val fileName = dataFile.fileName.withExtension("pdf")
		val outFile = dataFile.resolveSibling(fileName)

		plot(
				dataFilePath = dataFile.toAbsolutePath().toString(),
				outputFilePath = outFile.toAbsolutePath().toString())
	}

	/**
	 * Returns the percentage similarity between 2 image files
	 */
	private fun compareImage(screenshotA: ByteArray, screenshotB: ByteArray): Double {
		var percentage = 0.0
		try {
			// take buffer data from both image files
			val biA = ImageIO.read(ByteArrayInputStream(screenshotA))
			//Original code: val dbA = biA.data.dataBuffer
			val dbA = getDataWithoutStatusBar(biA).dataBuffer //Get screenshot without Status bar
			val sizeA = dbA.size

			val biB = ImageIO.read(ByteArrayInputStream(screenshotB))
			//Original code: val dbB = biB.data.dataBuffer
			val dbB = getDataWithoutStatusBar(biB).dataBuffer //Get screenshot without Status bar
			val sizeB = dbB.size
			var count = 0

			// compare data-buffer objects
			if (sizeA == sizeB) {
				(0 until sizeA)
						.filter { dbA.getElem(it) == dbB.getElem(it) }
						.forEach { count++ }

				percentage = (count * 100 / sizeA).toDouble()
			} else
				log.info("Both images have different size")

		} catch (e: Exception) {
			log.error("Failed to compare image files: ${screenshotA} and ${screenshotB}")
			e.printStackTrace()
		}

		return percentage
	}

	/**
	 * Returns a copy of the screenshot without status bar
	 * @param bi
	 */
	private fun getDataWithoutStatusBar(bi: BufferedImage): Raster {
		val raster = bi.data
		val width = raster.width
		val height = raster.height
		val startX = raster.minX

		val statusBarHeight = getStatusBarHeightInPX()

		val wr = Raster.createWritableRaster(raster.sampleModel,
				Point(raster.sampleModelTranslateX,
						raster.sampleModelTranslateY))

		var tData: Any? = null

		for (i in statusBarHeight until statusBarHeight + (height - statusBarHeight)) {
			tData = raster.getDataElements(startX, i, width, 1, tData)
			wr.setDataElements(startX, i, width, 1, tData)
		}

		return wr
	}

	/**
	 * Returns the Status Bar height in pixels
	 * The status bar height in Android is 24dp (https://material.io/guidelines/layout/structure.html#structure-app-bar)
	 * We need to convert dp to px.
	 * @return
	 */
	private fun getStatusBarHeightInPX(): Int {
		return 24 * pixelDensity
	}
}