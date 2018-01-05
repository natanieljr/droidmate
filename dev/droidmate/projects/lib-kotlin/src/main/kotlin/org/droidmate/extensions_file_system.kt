// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate

import com.konradjamrozik.FileSystemsOperations
import com.konradjamrozik.createDirIfNotExists
import com.konradjamrozik.isDirectory
import com.konradjamrozik.toList
import java.io.File
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Files.newDirectoryStream
import java.nio.file.Path

val Path.text: String get() {
    return Files.readAllLines(this).joinToString(System.lineSeparator())
}

fun Path.deleteDir(): Boolean {
    return try {
        if (Files.exists(this))
            Files.walk(this, FileVisitOption.FOLLOW_LINKS)
                    .toList()
                    .sorted()
                    .reversed()
                    .forEach { Files.delete(it) }
        true
    } catch (e: IOException) {
        false
    }
}

fun Path.withExtension(extension: String): Path {
  require(!this.isDirectory)
  return this.resolveSibling(File(this.fileName.toString()).nameWithoutExtension + "." + extension)
}

val Path.fileNames: Iterable<String>
  get() {
    require(this.isDirectory)
    return newDirectoryStream(this).map { it.fileName.toString() }
  }

fun Path.withFiles(vararg files: Path): Path {
  files.asList().copyFilesToDirInDifferentFileSystem(this)
  return this
}

fun FileSystem.dir(dirName: String): Path {
  val dir = this.getPath(dirName)
  dir.createDirIfNotExists()
  return dir
}

fun List<Path>.copyFilesToDirInDifferentFileSystem(destDir: Path): Unit {
    FileSystemsOperations().copyFilesToDirInDifferentFileSystem(this, destDir)
}
