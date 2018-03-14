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
package org.droidmate.storage

import org.nustaq.serialization.FSTObjectInput
import java.io.InputStream
import java.util.*

/**
 * <p>An {@link ObjectInputStream} that can account for changes in fully qualified names of the read/deserialized classes.
 * If this stream reads a class whose fully qualified name is a key in the {@link LegacyObjectInputStream#classNameMapping},
 * then it will be instead read as class whose fully qualified name is given in the value of that key.
 *
 * </p><p>
 * Assumption here is that the name of the read class has changed since it has been serialized.
 *
 * </p><p>
 * The name changed from:<br/>
 * A fully qualified name (now obsolete), as given in the mapping key<br/>
 * -to-<br/>
 * a fully qualified name (current), as given in the value of that key.
 * </p>
 */
class LegacyObjectInputStream constructor(ins: InputStream) : FSTObjectInput(ins) {
    companion object {
        @JvmStatic
        private val classNameMapping: Map<String, String> = initClassNameMapping()

        @JvmStatic
        private fun initClassNameMapping(): Map<String, String> {
            val classNameMapping = HashMap<String, String>()
            classNameMapping.put("org.droidmate.exceptions.DeviceExceptionMissing", "DeviceExceptionMissing")
            return Collections.unmodifiableMap(classNameMapping)
        }
    }

    override fun getClassForName(name: String?): Class<*> {
        return if (classNameMapping.containsKey(name)) {
            Class.forName(classNameMapping[name])
        } else
            Class.forName(name)
    }

    /*@Throws(IOException::class, ClassNotFoundException::class)
    override fun readClassDescriptor(): ObjectStreamClass {
        val contentDesc = super.readClassDescriptor()
        return if (classNameMapping.containsKey(contentDesc.name)) {
            ObjectStreamClass.lookup(Class.forName(classNameMapping[contentDesc.name]))
        } else
            contentDesc
    }*/
}