package org.droidmate.exploration.statemodel.config

import com.natpryce.konfig.*
import org.droidmate.configuration.ConfigProperties
import org.droidmate.exploration.statemodel.config.dump.stateFileExtension
import org.droidmate.exploration.statemodel.config.dump.traceFilePrefix
import org.droidmate.exploration.statemodel.config.path.cleanDirs
import org.droidmate.exploration.statemodel.config.path.defaultBaseDir
import org.droidmate.exploration.statemodel.config.path.statesSubDir
import org.droidmate.exploration.statemodel.config.path.widgetsSubDir
import org.droidmate.misc.deleteDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class ModelConfig private constructor(path: Path, appName: String,private val config:Configuration, isLoadC: Boolean = false): Configuration by config{
	/** @path path-string locationg the base directory where all model data is supposed to be dumped */

	constructor(path: Path, appName: String, isLoadC: Boolean = false): this(path.toAbsolutePath(), appName, resourceConfig, isLoadC)

	val baseDir = path.resolve(appName)  // directory path where the model file(s) should be stored
	val stateDst = baseDir.resolve(config[statesSubDir].path)       // each state gets an own file named according to UUID in this directory
	private val widgetImgDst = baseDir.resolve(config[widgetsSubDir].path)  // the images for the app widgets are stored in this directory (for report/debugging purpose only)

	init {  // initialize directories (clear them if cleanDirs is enabled)
		if(!isLoadC){
			if (config[cleanDirs]) (baseDir).deleteDir()
			Files.createDirectories((baseDir))
			Files.createDirectories((stateDst))
			Files.createDirectories((widgetImgDst))
			Files.createDirectories((widgetImgDst.resolve(nonInteractiveDir)))
		}
	}

	private val idPath: (Path, String, String, String) -> String = { baseDir, id, postfix, fileExtension -> baseDir.toString() + "${File.separator}$id$postfix$fileExtension" }

	val widgetFile: (ConcreteId,Boolean,String) -> String = { id,isHomeScreen,topPackageName ->
		statePath(id, postfix = defaultWidgetSuffix+(if(isHomeScreen) "_HS" else "") + "_PN-$topPackageName") }
	fun statePath(id: ConcreteId, postfix: String = "", fileExtension: String = config[stateFileExtension]): String {
		return idPath(stateDst, id.dumpString(), postfix, fileExtension)
	}

	fun widgetImgPath(id: UUID, postfix: String = "", fileExtension: String = ".png", interactive: Boolean): String {
		val baseDir = if (interactive) widgetImgDst else widgetImgDst.resolve(nonInteractiveDir)
		return idPath(baseDir, id.toString(), postfix, fileExtension)
	}

	val traceFile = { date: String -> "${baseDir.toString()}${File.separator}${config[traceFilePrefix]}$date${config[dump.traceFileExtension]}" }

	companion object {
		const val defaultWidgetSuffix = "_AllWidgets"
		private const val nonInteractiveDir = "nonInteractive"

		private val resourceConfig by lazy {
			ConfigurationProperties.fromResource("runtime/defaultModelConfig.properties")
		}

		@JvmOverloads operator fun invoke(appName: String, isLoadC: Boolean = false, cfg: Configuration? = null): ModelConfig{
			val (config, path) = if (cfg != null) Pair(cfg overriding resourceConfig, cfg[ConfigProperties.Output.droidmateOutputDirPath].resolve("model"))
				else Pair(resourceConfig, resourceConfig[defaultBaseDir])

			return ModelConfig(Paths.get(path.path).toAbsolutePath(), appName, config, isLoadC)
		}

	} /** end COMPANION **/
}


