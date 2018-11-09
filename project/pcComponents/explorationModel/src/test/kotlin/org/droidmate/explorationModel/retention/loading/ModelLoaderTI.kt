package org.droidmate.explorationModel.retention.loading

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.produce
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.ConcreteId
import org.droidmate.explorationModel.config.ConfigProperties
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.config.idFromString
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.StateData
import org.droidmate.explorationModel.interaction.Widget
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


/** test interface for the model loader, which cannot be done with mockito due to coroutine incompatibility */
internal interface ModelLoaderTI{
	var testTraces: List<Collection<Interaction>>
	var testStates: Collection<StateData>

	fun execute(testTraces: List<Collection<Interaction>>, testStates: Collection<StateData>, watcher: LinkedList<ModelFeatureI> = LinkedList()): Model
	suspend fun parseWidget(widget: Widget): Widget?

	/** REMARK these are state dependend => use very carefully in Unit-Tests */
//	val actionParser: suspend (List<String>) -> Pair<Interaction, StateData>
	suspend fun parseState(stateId: ConcreteId): StateData

}

class TestReader(config: ModelConfig): ContentReader(config){
	lateinit var testStates: Collection<StateData>
	lateinit var testTraces: List<Collection<Interaction>>
	private val traceContents: (idx: Int) -> List<String> = { idx ->
		testTraces[idx].map { actionData -> actionData.actionString().also { log(it) } } }

	private fun headerPlusString(s:List<String>,skip: Long):List<String> = LinkedList<String>().apply {
		add(Widget.widgetHeader(config[ConfigProperties.ModelProperties.dump.sep]))
		addAll(s)
	}.let {
		it.subList(skip.toInt(),it.size)
	}

	override fun getFileContent(path: Path, skip: Long): List<String>? = path.fileName.toString().let { name ->
		log("getFileContent for ${path.toUri()}")
		when {
			(name.startsWith(config[ConfigProperties.ModelProperties.dump.traceFilePrefix])) ->
				traceContents(name.removePrefix(config[ConfigProperties.ModelProperties.dump.traceFilePrefix]).toInt())
			name.contains("fa5d6ec4-129e-cde6-cfbf-eb837096de60_829a5484-73d6-ba71-57fc-d143d1cecaeb") ->
				headerPlusString(debugString.split("\n"), skip)
			else ->
				headerPlusString(testStates.find { s -> s.stateId == idFromString(name.removeSuffix(ModelConfig.defaultWidgetSuffix)) }!!.widgetsDump(config[ConfigProperties.ModelProperties.dump.sep]),skip)
		}
	}

	override fun getStateFile(stateId: ConcreteId): Pair<Path, Boolean>{
		val s = testStates.find { s -> s.stateId == stateId }
		if(s == null)
			println("debug error")
		return s!!.let{
			Pair(Paths.get(config.widgetFile(stateId,it.isHomeScreen)),it.isHomeScreen)
		}
	}

}

internal class ModelLoaderT(override val config: ModelConfig): ModelParserP(config,enableChecks = true), ModelLoaderTI {

	/** creating test environment */
	override val enableChecks: Boolean = true
	override val compatibilityMode: Boolean = false
	override val enablePrint: Boolean = false
	override val reader: TestReader = TestReader(config)
	override val isSequential: Boolean = false
	override fun log(msg: String) = println("TestModelLoader[${Thread.currentThread().name}] $msg")

	/** implementing ModelParser default methods */
	override val widgetParser by lazy { WidgetParserP(model,parentJob, compatibilityMode, enableChecks) }
	override val stateParser  by lazy { StateParserP(widgetParser, reader, model, parentJob, compatibilityMode, enableChecks) }

	/** custom test environment */
//	override val actionParser: suspend (List<String>) -> Pair<Interaction, StateData> = processor
//	override val actionParser: suspend (List<String>) -> Pair<Interaction, StateData> = { args -> processor(args).await() }
	override var testStates: Collection<StateData>
		get() = reader.testStates
		set(value) { reader.testStates = value}
	override var testTraces: List<Collection<Interaction>>
		get() = reader.testTraces
		set(value) { reader.testTraces = value}

	override fun traceProducer() = GlobalScope.produce(Dispatchers.Default, capacity = 5, block = {
		log("Produce trace paths")
		testTraces.forEach { log(it.toString() + "\n") }
		for (i in 0 until testTraces.size) {
			send(Paths.get(config[ConfigProperties.ModelProperties.dump.traceFilePrefix] + i.toString()))
		}
	})

	override fun execute(testTraces: List<Collection<Interaction>>, testStates: Collection<StateData>, watcher: LinkedList<ModelFeatureI>): Model {
//		logcat(testActions.)
		this.testTraces = testTraces
		this.testStates = testStates
		return loadModel(watcher)
	}

	override suspend fun parseWidget(widget: Widget): Widget? =
			widgetParser.getElem( widgetParser.processor(widget.splittedDumpString(config[ConfigProperties.ModelProperties.dump.sep])) )

	override suspend fun parseState(stateId: ConcreteId): StateData = stateParser.getElem( currentScope(stateParser.parseIfAbsent)(stateId) )
}

private const val debugString = "881086d0-66da-39d3-89a7-3ef465ab4971;ccff4dcd-b1ec-3ebf-b2e0-8757b1f8119f;android.view.ViewGroup;true;;;null;true;true;true;false;false;disabled;false;false;false;789;63;263;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[4];false;ch.bailu.aat\n" +
		"8945671d-0726-31ff-ae18-48c904674dbf;b940bd4d-7415-39c7-9410-fb90c9e00eb2;android.widget.LinearLayout;false;;;null;true;true;false;false;false;disabled;disabled;false;false;0;0;1080;1794;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1];false;ch.bailu.aat\n" +
		"8c578de3-7278-3da4-88d7-63ea86c5cf20;5316bbd9-b134-3beb-8afd-274c882125a5;android.widget.TextView;false;GPS;;null;true;true;false;false;false;disabled;disabled;false;false;526;89;263;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[3]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"94e3c002-756a-3f54-b9bb-f568589de199;fe8404e6-0a37-33c4-96e2-fee2823f7e7c;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;694;1080;184;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[3];false;ch.bailu.aat\n" +
		"98977b60-c508-3184-bb1b-767e07fd60b2;5130c087-5ebe-3d7b-a372-0201b7ff3260;android.view.ViewGroup;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;63;263;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[1];false;ch.bailu.aat\n" +
		"98977b60-c508-3184-bb1b-767e07fd60b2;ee538e23-0b90-31e8-ab6a-c02122f3129b;android.widget.ImageButton;false;;;null;true;true;false;false;false;disabled;false;false;false;0;63;263;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[1]/android.widget.ImageButton[1];true;ch.bailu.aat\n" +
		"ac1c14c5-a615-30ab-af76-c98660a12b69;95621660-4306-3de3-8eb5-51bb528b6d71;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;1062;1080;184;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[5];false;ch.bailu.aat\n" +
		"b6a022f4-dfee-3c05-8e07-0416456cdb3b;df8b2716-3f45-3e46-8123-a8e45fa557d1;android.widget.LinearLayout;false;;;null;true;true;false;false;false;disabled;disabled;false;false;0;63;1080;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1];false;ch.bailu.aat\n" +
		"c75b614d-195e-3620-8455-03ee9a04c998;11362e14-0fa4-389f-a1d2-ecc5e45a8913;android.view.ViewGroup;false;;;null;true;true;false;false;false;disabled;false;false;false;526;63;263;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[3];false;ch.bailu.aat\n" +
		"cb11347e-8d94-36b7-aa64-57c5107f7bda;60c45577-3722-3139-b45b-1265885870cc;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;326;1080;184;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1];false;ch.bailu.aat\n" +
		"d0834fce-c633-3785-ae74-9c8f5464f6f6;f81a0bad-addb-3b77-a6ec-ad069b701ec4;android.widget.TextView;false;Preferences;;null;true;true;false;false;false;disabled;disabled;false;false;26;1640;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[8]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"d15305d7-a4e3-3e02-889c-74a5ef542f36;a1bb0ab1-d08c-3815-9c86-6fe72e14dce0;android.widget.TextView;false;Off;;null;true;true;false;false;false;disabled;disabled;false;false;526;140;263;109;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[3]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"d15305d7-a4e3-3e02-889c-74a5ef542f36;81996af2-14cf-3328-9fbc-3a91f148e842;android.widget.TextView;false;Off;;null;true;true;false;false;false;disabled;disabled;false;false;789;140;263;109;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[4]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"d7781b21-72b7-3cc5-b085-d0e8673189cc;5b4b3359-7aed-380f-906d-73f34aa97d8b;android.widget.TextView;false;[6.8 bicycling, leisure, moderate effort];;null;true;true;false;false;false;disabled;disabled;false;false;26;423;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"d7ec5ea0-dee0-3157-b34a-cace17bbfcda;e58d79eb-d661-3adc-8c72-6419a312bdf6;android.widget.TextView;false;/storage/emulated/0/Android/data/ch.bailu.aat/files/overlay;;null;true;true;false;false;false;disabled;disabled;false;false;26;1343;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[6]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"e1fd48b6-5206-3209-b012-0234165c2338;e3d1e90c-d487-3b92-b96a-ef7e9150a691;android.view.View;false;;;null;true;true;false;false;false;disabled;disabled;false;false;263;63;263;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.View[2];true;ch.bailu.aat\n" +
		"e8104e4b-a7e2-3734-9df3-2e3ab6c01792;8893007d-ffcf-34fe-9add-1887913a4736;android.widget.TextView;false;Cockpit A;;null;true;true;false;false;false;disabled;disabled;false;false;26;536;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[2]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"ecfc2dff-e568-310a-a7db-c6d3724cfde2;ecbb3edc-49bc-3dfe-acaa-7c0b835b4f6f;android.widget.TextView;false;Activity;;null;true;true;false;false;false;disabled;disabled;false;false;26;352;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"f2c10ab9-7a63-30bb-82d7-a09cbff36b38;714d99bc-cfee-3d0f-91ca-5be05c1c0afa;android.widget.TextView;false;Track list;;null;true;true;false;false;false;disabled;disabled;false;false;26;1088;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[5]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"f7592a2a-ddc6-3c4a-9a33-9caa7ee61b8f;1f4027b6-5838-3822-8913-b50c95c6e627;android.widget.TextView;false;Search with OSM-Nominatim.;;null;true;true;false;false;false;disabled;disabled;false;false;26;1527;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[7]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"0829ea67-3405-3d66-a6bf-87096b215dc1;581aa529-492b-3bbf-8d78-2d988e9d5824;android.widget.TextView;false;Fullscreen;;null;true;true;false;false;false;disabled;disabled;false;false;26;607;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[2]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"0e07d018-f6e0-3e47-a15e-b305afb237ad;4250070d-7e4e-3a7d-af25-ea8cc7bb0964;android.widget.ScrollView;true;;;null;true;true;false;false;true;disabled;false;false;false;0;326;1080;1468;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2];false;ch.bailu.aat\n" +
		"0e07d018-f6e0-3e47-a15e-b305afb237ad;c1243b29-842e-365e-bbfe-ac955cedfe7a;android.widget.LinearLayout;false;;;null;true;true;false;false;false;disabled;disabled;false;false;0;326;1080;1468;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1];false;ch.bailu.aat\n" +
		"18a37c8e-4de4-35d2-bc57-e1e7e58c7c1f;969ef865-cd8e-39e7-b9c2-0738dc0157dd;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;878;1080;184;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[4];false;ch.bailu.aat\n" +
		"26b8a5f8-cbb6-32e9-86c8-eed4b88cc0bd;a794d91d-6079-333e-bac6-d915fa0fa10e;android.widget.TextView;false;Tracker;;null;true;true;false;false;false;disabled;disabled;false;false;789;89;263;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[4]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"2b8f408d-a314-3678-af6b-a0e581dd3fac;3180b714-3c0e-3c22-9ac4-5a88fe13178b;android.widget.TextView;false;;;null;true;true;false;false;false;disabled;disabled;false;false;26;1711;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[8]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"39ac6ebd-7390-3723-a0ed-aa7633afb074;1dbbe5d0-816d-33ed-81ab-2125f3152478;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;510;1080;184;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[2];false;ch.bailu.aat\n" +
		"46f3ea05-6caa-3126-b91f-3f70beea068c;a6830ffd-440b-3651-b2b0-a6c2e19b9b3b;android.widget.TextView;false;Map;;null;true;true;false;false;false;disabled;disabled;false;false;26;904;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[4]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"47da2762-fa16-34ff-a91c-cf89e7a94fcf;94241dfb-6fb1-3166-bf93-6d065ac59233;android.widget.TextView;false;;;null;true;true;false;false;false;disabled;disabled;false;false;526;249;263;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[3]/android.widget.TextView[3];true;ch.bailu.aat\n" +
		"47da2762-fa16-34ff-a91c-cf89e7a94fcf;dd227b51-bb42-3def-9fc4-8f75a9d880b5;android.widget.TextView;false;;;null;true;true;false;false;false;disabled;disabled;false;false;789;249;263;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1]/android.view.ViewGroup[4]/android.widget.TextView[3];true;ch.bailu.aat\n" +
		"47e0e19f-4bb0-3fc4-a430-0024d15e893d;cebcb059-2336-3a58-afc7-514b1f778543;android.widget.TextView;false;Cockpit B;;null;true;true;false;false;false;disabled;disabled;false;false;26;720;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[3]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"51fbca67-14e9-33e7-801a-05314210ae86;574013da-af06-3400-bcf0-983ab094b500;android.widget.TextView;false;Splitscreen;;null;true;true;false;false;false;disabled;disabled;false;false;26;791;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[3]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"52be7836-4733-3775-b2db-af0cfb3f00f4;7ad6e4ab-6961-3c2a-ac58-d5289b5e3a91;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;1430;1080;184;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[7];false;ch.bailu.aat\n" +
		"60ef277d-0ef8-3452-8652-96a8a88c5dd9;1ad98a16-2b21-3335-b2ae-d4034e5f7e2d;android.widget.TextView;false;;;null;true;true;false;false;false;disabled;disabled;false;false;26;975;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[4]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"67e82420-9cca-3a57-bcd7-6f0e4fcb9c33;e1c0e4b4-fc9f-3a48-a39d-32e4a3eb2b7c;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;1246;1080;184;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[6];false;ch.bailu.aat\n" +
		"68920d13-d016-3bfa-b161-9a3ac716fa35;38a64cd7-a898-3052-9cf2-b2c6cb605297;android.widget.TextView;false;Map Search;;null;true;true;false;false;false;disabled;disabled;false;false;26;1456;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[7]/android.widget.TextView[1];true;ch.bailu.aat\n" +
		"6ac1ff6b-2a02-36fa-8d06-794e3bf2f859;710abab8-dfed-3fb0-996e-ee03dd6afb01;android.widget.TextView;false;/storage/emulated/0/Android/data/ch.bailu.aat/files/activity0;;null;true;true;false;false;false;disabled;disabled;false;false;26;1159;1028;51;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[5]/android.widget.TextView[2];true;ch.bailu.aat\n" +
		"6e989f73-0582-3880-80b1-036719485c8b;b68cc81d-2a64-3589-871d-28d788212cf0;android.widget.FrameLayout;false;;;null;true;true;false;false;false;disabled;disabled;false;false;0;63;1080;1731;android:id/content;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1];false;ch.bailu.aat\n" +
		"6e989f73-0582-3880-80b1-036719485c8b;10fd09d8-338d-3953-8e11-433d10b97c78;android.widget.LinearLayout;false;;;null;true;true;false;false;false;disabled;disabled;false;false;0;63;1080;1731;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1];false;ch.bailu.aat\n" +
		"7945d0cd-90fb-3a29-b3a6-f621d3e055c9;e6f0c918-2d21-3f77-b7ff-0eed5af34f03;android.widget.LinearLayout;true;;;null;true;true;true;false;false;disabled;disabled;false;false;0;1614;1080;180;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[8];false;ch.bailu.aat\n" +
		"7a88a92c-fbb9-324e-95e8-fdee7d1c6bfa;17fbd661-dd74-329c-9ef8-49d19f152d7d;android.widget.HorizontalScrollView;false;;;null;true;true;false;false;false;disabled;false;false;false;0;63;1052;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1];false;ch.bailu.aat\n" +
		"7a88a92c-fbb9-324e-95e8-fdee7d1c6bfa;6d86439e-6934-3552-8b50-78641366fdf4;android.widget.LinearLayout;false;;;null;true;true;false;false;false;disabled;disabled;false;false;0;63;1052;263;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.LinearLayout[1]/android.widget.HorizontalScrollView[1]/android.widget.LinearLayout[1];false;ch.bailu.aat\n" +
		"7d67ebc3-437f-3daf-8b18-57993ef4dcc9;ee5c0057-4183-3fc0-a96d-03b6955f0ae3;android.widget.TextView;false;Overlay list;;null;true;true;false;false;false;disabled;disabled;false;false;26;1272;1028;71;;//android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.ScrollView[2]/android.widget.LinearLayout[1]/android.widget.LinearLayout[6]/android.widget.TextView[1];true;ch.bailu.aat"