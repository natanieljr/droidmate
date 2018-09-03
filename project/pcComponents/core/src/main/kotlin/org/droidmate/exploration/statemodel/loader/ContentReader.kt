package org.droidmate.exploration.statemodel.loader

import org.droidmate.configuration.ConfigProperties
import org.droidmate.exploration.statemodel.ConcreteId
import org.droidmate.exploration.statemodel.ModelConfig
import org.droidmate.exploration.statemodel.dumpString
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList


internal open class ContentReader(val config: ModelConfig){
	@Suppress("UNUSED_PARAMETER")
	fun log(msg: String)
	{}
//		 = println("[${Thread.currentThread().name}] $msg")

	open fun getFileContent(path: Path, skip: Long): List<String>? = path.toFile().let { file ->  // TODO this and P_processLines would be moved to Processor common function
		log("\n getFileContent skip=$skip, path= ${path.toUri()} \n")

		if (!file.exists()) { return null } // otherwise this state has no widgets

		return BufferedReader(FileReader(file)).use {
			it.lines().skip(skip).toList()
		}
	}

	open fun getStateFile(stateId: ConcreteId): Triple<Path,Boolean,String>{
		val contentPath = Files.list(Paths.get(config.stateDst.toUri())).use { it.toList() }.first {
			it.fileName.toString().startsWith( stateId.dumpString()+ ModelConfig.defaultWidgetSuffix) }
		return contentPath.fileName.toString().let {
			Triple(contentPath, it.contains("HS"), it.substring(it.indexOf("_PN-")+4,it.indexOf(config[ConfigProperties.ModelProperties.dump.stateFileExtension])))
		}
	}

	fun getHeader(path: Path): List<String>{
		return getFileContent(path,0)?.first()?.split(config[ConfigProperties.ModelProperties.dump.sep])!!
	}
	suspend inline fun <T> processLines(path: Path, skip: Long = 1, crossinline lineProcessor: suspend (List<String>) -> T): List<T> {
		log("call P_processLines for ${path.toUri()}")
		getFileContent(path,skip)?.let { br ->	// skip the first line (headline)
			assert(br.count() > 0) { "ERROR on model loading: file ${path.fileName} does not contain any entries" }
			return br.map { line -> lineProcessor(line.split(config[ConfigProperties.ModelProperties.dump.sep]).map { it.trim() }) }
		} ?: return emptyList()
	}

}