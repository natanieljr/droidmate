package org.droidmate.explorationModel.config

import com.natpryce.konfig.*

object ConfigProperties {
	internal object Output : PropertyGroup() {
		val outputDir by uriType
	}

	object ModelProperties : PropertyGroup() {
		object path : PropertyGroup() {
			val defaultBaseDir by uriType
			val statesSubDir by uriType
			val widgetsSubDir by uriType
			val cleanDirs by booleanType
		}

		object dump : PropertyGroup() {
			val sep by stringType
			val onEachAction by booleanType

			val stateFileExtension by stringType

			val traceFileExtension by stringType
			val traceFilePrefix by stringType
		}

		object imgDump : PropertyGroup() {
			val states by booleanType
			val widgets by booleanType

			object widget : PropertyGroup() {
				val nonInteractable by booleanType
				val interactable by booleanType
				val onlyWhenNoText by booleanType
			}
		}

		object Features : PropertyGroup() {
			val statementCoverage by booleanType
			val statementCoverageDir by uriType
		}
	}
}