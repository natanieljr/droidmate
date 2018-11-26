package org.droidmate.explorationModel.retention.loading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.config.ConfigProperties
import org.droidmate.explorationModel.emptyId
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.coroutines.coroutineContext
import kotlin.streams.toList


open class ContentReader(val config: ModelConfig){
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

	open fun getStateFile(stateId: ConcreteId): Pair<Path,Boolean>{
		val contentPath = Files.list(Paths.get(config.stateDst.toUri())).use { it.toList() }.first {
			it.fileName.toString().startsWith( stateId.toString()) }
		return Pair(contentPath, contentPath.fileName.toString().contains("HS")//, it.substring(it.indexOf("_PN")+4,it.indexOf(config[ConfigProperties.ModelProperties.dump.stateFileExtension]))
		)
	}

	fun getHeader(path: Path): List<String>{
		return getFileContent(path,0)?.first()?.split(config[ConfigProperties.ModelProperties.dump.sep])!!
	}
	suspend inline fun <T> processLines(path: Path, skip: Long = 1, crossinline lineProcessor: suspend (List<String>,CoroutineScope) -> T): List<T> {
		log("call P_processLines for ${path.toUri()}")
		getFileContent(path,skip)?.let { br ->	// skip the first line (headline)
			assert(br.count() > 0 // all 'non-empty' states have to have entries for their widgets
					|| skip==0L || !path.fileName.startsWith(emptyId.toString()))
				{ "ERROR on model loading: file ${path.fileName} does not contain any entries" }
			val scope = CoroutineScope(coroutineContext+ Job())
			return br.map { line ->
				lineProcessor(line.split(config[ConfigProperties.ModelProperties.dump.sep]).map { it.trim() },scope) }
		} ?: return emptyList()
	}

}