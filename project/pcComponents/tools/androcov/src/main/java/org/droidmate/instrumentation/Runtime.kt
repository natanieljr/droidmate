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

package org.droidmate.instrumentation

import com.google.common.base.Suppliers
import soot.Scene
import soot.SootClass
import soot.SootMethod
import soot.jimple.InvokeStmt
import soot.jimple.Jimple
import soot.jimple.StringConstant

import java.nio.file.Path
import java.util.function.Supplier

/**
 * Originally copied to a large extent from the aggregator project.
 */
class Runtime private constructor(private val portFile: Path) {

    private val runtimeClass = Suppliers.memoize { Scene.v().getSootClass("$PACKAGE.Runtime") }

    private val statementPointMethod = Suppliers.memoize { getRuntimeClass().getMethodByName("statementPoint") }

    private fun getRuntimeClass(): SootClass {
        return runtimeClass.get()
    }

    /**
     * Returns the static statement logging method that can be monitored by DroidMate to extract runtime method invocations.
     *
     * @return The logging method
     */
    private fun getStatementPointMethod(): SootMethod {
        return statementPointMethod.get()
    }

    fun makeCallToStatementPoint(method: String): InvokeStmt {
        return Jimple.v().newInvokeStmt(
            Jimple.v().newStaticInvokeExpr(
                this.getStatementPointMethod().makeRef(), StringConstant.v(method), StringConstant.v(portFile.toString())))
    }

    companion object {

        val PACKAGE: String = org.droidmate.runtime.Runtime::class.java.getPackage().name

        private var instance: Runtime? = null

        fun v(portFile: Path): Runtime {
            return instance ?: Runtime(portFile)
        }
    }

}
