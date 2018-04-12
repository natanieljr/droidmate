package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.exploration.statemodel.config.ModelConfig
import org.droidmate.exploration.statemodel.config.dump
import org.droidmate.test_tools.DroidmateTestCase
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

private val config = ModelConfig("JUnit",true)

/** verify the ModelLoader correctly initializes/loads a model by using
 * - mocked model (mock the dump-file content read)
 * - loading real model dump files & verifying resulting model
 * - dumping and loading the same model => verify equality
 * - test watcher are correctly updated during model loading
 *
 * REMARK for mockito to work it is essential that all mocked/spied classes and methods have the `open` modifier
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ModelLoadTester: DroidmateTestCase(), TestModel by DefaultTestModel(), ModelLoaderTI by ModelLoaderT(config) {
	private val testState = StateData(setOf(testWidget))
	private val actions = listOf(TestModel.TestAction(testWidget,testState.stateId))
	private val states = listOf(testState)

	@Test
	fun widgetParsingTest() = runBlocking{
		parseWidget(testWidget).await()!!.let{
			expect(it.dataString(config[dump.sep]),testWidget.dataString(config[dump.sep]))
		}
	}

	@Test fun loadTest(){
		val model = execute(actions,states)
		runBlocking {
			expect(model.getState(testState.stateId)!!.widgetsDump("\t"),testState.widgetsDump("\t"))
			model.getWidgets().let { widgets ->
				expect(widgets.size, 1)
				expect(widgets.first().dataString("\t"),testWidget.dataString("\t"))
			}
		}
		model.getPaths().let{ traces ->
			expect(traces.size,1)
			traces.first().getActions().let{ _actions ->
				expect(_actions.size,1)
				expect(_actions.first().actionString(),actions.first().actionString())
			}
		}
		println(model)
	}

}

