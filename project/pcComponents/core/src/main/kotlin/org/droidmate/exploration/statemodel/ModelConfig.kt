package org.droidmate.exploration.statemodel

import com.natpryce.konfig.*
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.stateFileExtension
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.traceFileExtension
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.traceFilePrefix
import org.droidmate.configuration.ConfigProperties.ModelProperties.path.cleanDirs
import org.droidmate.configuration.ConfigProperties.ModelProperties.path.defaultBaseDir
import org.droidmate.configuration.ConfigProperties.ModelProperties.path.statesSubDir
import org.droidmate.configuration.ConfigProperties.ModelProperties.path.widgetsSubDir
import org.droidmate.configuration.ConfigProperties.Output.droidmateOutputDirPath
import org.droidmate.misc.deleteDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

	val traceFile = { date: String -> "$baseDir${File.separator}${config[traceFilePrefix]}$date${config[traceFileExtension]}" }

	companion object {
		const val defaultWidgetSuffix = "_AllWidgets"
		private const val nonInteractiveDir = "nonInteractive"

		private val resourceConfig by lazy {
			ConfigurationProperties.fromResource("runtime/defaultModelConfig.properties")
		}

		@JvmOverloads operator fun invoke(appName: String, isLoadC: Boolean = false, cfg: Configuration? = null): ModelConfig {
			val (config, path) = if (cfg != null)
				Pair(cfg overriding resourceConfig, Paths.get(cfg[droidmateOutputDirPath].toString()).resolve("model"))
			else
				Pair(resourceConfig, Paths.get(resourceConfig[defaultBaseDir].toString()))

			return ModelConfig(path, appName, config, isLoadC)
		}

	} /** end COMPANION **/
}

val emptyUUID: UUID = UUID.nameUUIDFromBytes(byteArrayOf())
typealias ConcreteId = Pair<UUID, UUID>
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun ConcreteId.toString() = "${first}_$second"  // mainly for nicer debugging strings
fun idFromString(s: String): ConcreteId = s.split("_").let { ConcreteId(UUID.fromString(it[0]), UUID.fromString(it[1])) }
/** custom dumpString method used for model dump & load **/
fun ConcreteId.dumpString() = "${first}_$second"
val emptyId = ConcreteId(emptyUUID, emptyUUID)

private const val datePattern = "ddMM-HHmmss"
internal fun timestamp(): String = DateTimeFormatter.ofPattern(datePattern).format(LocalDateTime.now())

