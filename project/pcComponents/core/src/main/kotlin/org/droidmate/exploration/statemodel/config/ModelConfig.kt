package org.droidmate.exploration.statemodel.config

import com.natpryce.konfig.*
import org.droidmate.exploration.statemodel.config.dump.stateFileExtension
import org.droidmate.exploration.statemodel.config.dump.traceFilePrefix
import org.droidmate.exploration.statemodel.config.path.cleanDirs
import org.droidmate.exploration.statemodel.config.path.statesSubDir
import org.droidmate.exploration.statemodel.config.path.widgetsSubDir
import org.droidmate.misc.deleteDir
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class ModelConfig private constructor(path: String, appName: String,private val config:Configuration, isLoadC: Boolean = false): Configuration by config{
	/** @path path-string locationg the base directory where all model data is supposed to be dumped */
	constructor(path: String, appName: String, isLoadC: Boolean = false): this(path, appName, resourceConfig, isLoadC)
	constructor(appName: String, isLoadC: Boolean = false) : this("out${File.separator}model", appName, isLoadC)

	val baseDir = "$path${File.separator}$appName${File.separator}"  // directory path where the model file(s) should be stored
	private val stateDst = "$baseDir${config[statesSubDir].path}${File.separator}"       // each state gets an own file named according to UUID in this directory
	private val widgetImgDst = "$baseDir${config[widgetsSubDir]}${File.separator}"  // the images for the app widgets are stored in this directory (for report/debugging purpose only)

	init {  // initialize directories (clear them if cleanDirs is enabled)
		if(!isLoadC){
			if (config[cleanDirs]) Paths.get(baseDir).deleteDir()
			Files.createDirectories(Paths.get(baseDir))
			Files.createDirectories(Paths.get(stateDst))
			Files.createDirectories(Paths.get(widgetImgDst))
			Files.createDirectories(Paths.get("${widgetImgDst}nonInteractive${File.separator}"))
		}
	}

	private val idPath: (String, String, String, String) -> String = { baseDir, id, postfix, fileExtension -> "$baseDir$id$postfix$fileExtension" }

	val widgetFile: (ConcreteId) -> String = { id -> statePath(id, postfix = defaultWidgetSuffix) }
	fun statePath(id: ConcreteId, postfix: String = "", fileExtension: String = config[stateFileExtension]): String {
		return idPath(stateDst, id.dumpString(), postfix, fileExtension)
	}

	fun widgetImgPath(id: UUID, postfix: String = "", fileExtension: String = ".png", interactive: Boolean): String {
		val baseDir = widgetImgDst + if (interactive) "" else "nonInteractive${File.separator}"
		return idPath(baseDir, id.toString(), postfix, fileExtension)
	}

	val traceFile = { date: String -> "$baseDir${config[traceFilePrefix]}$date${config[dump.traceFileExtension]}" }

	companion object {
		const val defaultWidgetSuffix = "_AllWidgets"
		private val resourceConfig by lazy {
			ConfigurationProperties.fromResource("runtime/defaultModelConfig.properties")  //FIXME use this in final version when modelConfig build parameter is available, until then use this as IntelliJ resource reload workaround
			//ConfigurationProperties.fromFile(File("project/pcComponents/core/src/main/resources/runtime/defaultModelConfig.properties").apply { println(absolutePath) })
		}
		operator fun invoke(path: String, appName: String, customFilePath: URI, isLoadC: Boolean = false):ModelConfig{
			val config = File(customFilePath.path).let{customConfigFile ->
				if (customConfigFile.exists()) ConfigurationProperties.fromFile(customConfigFile) overriding resourceConfig
				else resourceConfig
			}
			return ModelConfig(path, appName, config, isLoadC)
		}

	} /** end COMPANION **/
}


