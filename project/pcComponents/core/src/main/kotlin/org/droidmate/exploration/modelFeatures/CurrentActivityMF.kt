package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import org.droidmate.configuration.ConfigurationWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
/**
 * Model feature to obtain the current activity name.
 *
 * Output example: mFocusedActivity: ActivityRecord{b959d7e u0 com.addressbook/.AddressBookActivity t21414}
 */
class CurrentActivityMF(cfg: ConfigurationWrapper): AdbBasedMF(cfg){
	override val coroutineContext = CoroutineName("CurrentActivityMF")

	private val log: Logger by lazy { LoggerFactory.getLogger(CurrentActivityMF::class.java) }

	fun getCurrentActivityName(): String{
		val data = runAdbCommand(listOf("shell", "dumpsys", "activity", "|", "grep", "mFocusedActivity"))

		return data.firstOrNull().orEmpty()
	}

}