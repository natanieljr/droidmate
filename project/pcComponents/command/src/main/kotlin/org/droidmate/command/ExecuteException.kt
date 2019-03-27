package org.droidmate.command

import org.droidmate.device.android_sdk.Apk
import org.droidmate.device.android_sdk.ExplorationException
import org.droidmate.exploration.ExplorationContext
import org.droidmate.misc.DroidmateException

/** used when an exploration fails with an exception
 * @eContexts will contain all exploration contexts (one for each apk) which DM-2 was able to generate so far
 * */
data class ExecuteException(val exceptions: Map<Apk?, List<DroidmateException>>,
                            val eContexts: List<ExplorationContext>
                            = emptyList()): Throwable(message = exceptions.values.first().first().message)