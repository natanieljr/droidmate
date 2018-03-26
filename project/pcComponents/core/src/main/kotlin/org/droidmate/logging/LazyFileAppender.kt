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


package org.droidmate.logging

import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.recovery.ResilientFileOutputStream
import ch.qos.logback.core.util.FileUtil
import java.io.File
import java.io.IOException

import java.nio.channels.FileLock


/**
 * Copy-pasted and then modified to suit my needs from:
 * https://github.com/tony19/logback-android/blob/master/logback-core/src/main/java/ch/qos/logback/core/FileAppender.java
 */
@Suppress("RedundantVisibilityModifier", "MemberVisibilityCanPrivate")
public class LazyFileAppender<E> : OutputStreamAppender<E>() {

	/**
	 * Append to or truncate the file? The default value for this variable is
	 * <code>true</code>, meaning that by default a <code>LazyFileAppender</code> will
	 * append to an existing file and not truncate it.
	 */
	var append = true

	/**
	 * The name of the active log file.
	 */
	var fileName: String = ""

	private var prudent = false
	private var initialized = false
	private var lazyInit = false

	/**
	 * The <b>File</b> property takes a string value which should be the name of
	 * the file to append to.
	 */
	public fun setFile(file: String) {
		// Trim spaces from both ends. The users probably does not want
		// trailing spaces in file names.
		fileName = file.trim()
	}

	/**
	 * Returns the value of the <b>Append</b> property.
	 */
	public fun isAppend(): Boolean = append

	/**
	 * This method is used by derived classes to obtain the raw file property.
	 * Regular users should not be calling this method. Note that RollingFilePolicyBase
	 * requires public getter for this property.
	 *
	 * @return the value of the file property
	 */
	public fun rawFileProperty(): String = fileName

	/**
	 * Returns the value of the <b>File</b> property.
	 *
	 * <p>
	 * This method may be overridden by derived classes.
	 *
	 */
	public fun getFile(): String = fileName

	/**
	 * If the value of <b>File</b> is not <code>null</code>, then
	 * {@link #openFile} is called with the values of <b>File</b> and
	 * <b>Append</b> properties.
	 */
	override fun start() {
		var errors = 0

		// Use getFile() instead of direct access to fileName because
		// the function is overridden in RollingFileAppender, which
		// returns a value that doesn't necessarily match fileName.
		val file = getFile()

		if (file.isNotEmpty()) {
			addInfo("File property is set to [$file]")

			if (prudent) {
				if (!isAppend()) {
					append = true
					addWarn("Setting \"Append\" property to true on account of \"Prudent\" mode")
				}
			}

			if (!lazyInit) {
				try {
					openFile(file)
				} catch (e: IOException) {
					errors++
					addError("openFile($file,$append) failed", e)
				}
			} else {
				// We'll initialize the file output stream later. Use a dummy for now
				// to satisfy OutputStreamAppender.start().
				outputStream = NOPOutputStream()
			}
		} else {
			errors++
			addError("\"File\" property not set for appender named [$name]")
		}
		if (errors == 0) {
			super.start()
		}
	}

	/**
	 * <p>
	 * Sets and <i>opens</i> the file where the log output will go. The specified
	 * file must be writable.
	 *
	 * <p>
	 * If there was already an opened file, then the previous file is closed
	 * first.
	 *
	 * <p>
	 * <b>Do not use this method directly. To configure a LazyFileAppender or one of
	 * its subclasses, set its properties one by one and then call start().</b>
	 *
	 * @param filename
	 *          The path to the log file.
	 *
	 * @return true if successful; false otherwise
	 */
	@Throws(IOException::class)
	private fun openFile(filename: String): Boolean {
		var successful = false
		synchronized(lock)
		{
			val file = File(filename)
			if (FileUtil.isParentDirectoryCreationRequired(file)) {
				val result = FileUtil.createMissingParentDirectories(file)
				if (!result) {
					addError("Failed to create parentID directories for ["
							+ file.absolutePath + "]")
				}
			}

			val resilientFos = ResilientFileOutputStream(file, append)
			resilientFos.context = context
			outputStream = resilientFos
			successful = true
		}
		return successful
	}

	/**
	 * @see #setPrudent(boolean)
	 *
	 * @return true if in prudent mode
	 */
	public fun isPrudent(): Boolean = prudent

	/**
	 * When prudent is set to true, file appenders from multiple JVMs can safely
	 * write to the same file.
	 *
	 * @param prudent
	 */
	public fun setPrudent(prudent: Boolean) {
		this.prudent = prudent
	}

	/**
	 * Gets the enable status of lazy initialization of the file output
	 * stream
	 *
	 * @return true if enabled; false otherwise
	 */
	public fun getLazy(): Boolean = lazyInit

	/**
	 * Enables/disables lazy initialization of the file output stream.
	 * This defers the file creation until the first outgoing message.
	 *
	 * @param enabled true to enable lazy initialization; false otherwise
	 */
	public fun setLazy(enable: Boolean) {
		lazyInit = enable
	}

	@Throws(IOException::class)
	private fun safeWrite(event: E) {
		val resilientFOS = outputStream as ResilientFileOutputStream
		val fileChannel = resilientFOS.channel ?: return
		var fileLock: FileLock? = null
		try {
			fileLock = fileChannel.lock()
			val position = fileChannel.position()
			val size = fileChannel.size()
			if (size != position) {
				fileChannel.position(size)
			}

			super.writeOut(event)
		} finally {
			if (fileLock != null) {
				fileLock.release()
			}
		}
	}

	override fun writeOut(event: E) {
		if (prudent) {
			safeWrite(event)
		} else {
			super.writeOut(event)
		}
	}

	override fun subAppend(event: E) {
		if (!initialized && lazyInit) {
			initialized = true
			try {
				openFile(getFile())
			} catch (e: IOException) {
				this.started = false
				addError("openFile($fileName,$append) failed", e)
			}
		}

		super.subAppend(event)
	}

}
