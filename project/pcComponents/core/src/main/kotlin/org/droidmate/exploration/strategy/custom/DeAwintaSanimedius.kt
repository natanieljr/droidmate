package org.droidmate.exploration.strategy.custom

import org.droidmate.exploration.actions.AbstractExplorationAction
import org.droidmate.exploration.actions.ClickExplorationAction
import org.droidmate.exploration.actions.EnterTextExplorationAction
import org.droidmate.exploration.strategy.widget.ExplorationStrategy

class DeAwintaSanimedius : ExplorationStrategy(){
	override fun chooseAction(): AbstractExplorationAction {
		val hasUserNameField = this.currentState.widgets.any{ it.text == "E-Mail*" }
		if (hasUserNameField){
			val wgt = this.currentState.widgets.first{ it.text == "E-Mail*" }
			return EnterTextExplorationAction("abc@gmail.com", wgt)
		}

		val hasPasswordField = this.currentState.widgets.any{ it.text == "Password*" }
		if (hasPasswordField){
			val wgt = this.currentState.widgets.first{ it.text == "Password*" }
			return EnterTextExplorationAction("abc.123", wgt)
		}

		return ClickExplorationAction(this.currentState.widgets
				.first { it.contentDesc == "Login" })
	}

}