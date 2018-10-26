package org.droidmate.explorationModel

import junit.framework.TestCase

interface TestI {
	fun<T> expect(res:T, ref: T){
		val refSplit = ref.toString().split(";")
		val diff = res.toString().split(";").mapIndexed { index, s ->
			if(refSplit.size>index) s.replace(refSplit[index],"#CORRECT#")
			else s
		}
		TestCase.assertTrue("expected \n${ref.toString()} \nbut result was \n${res.toString()}\n DIFF = $diff", res == ref)
	}
}