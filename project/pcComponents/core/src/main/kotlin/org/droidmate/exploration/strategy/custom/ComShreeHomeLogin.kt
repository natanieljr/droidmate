package org.droidmate.exploration.strategy.custom

import org.droidmate.exploration.actions.AbstractExplorationAction
import org.droidmate.exploration.actions.ClickExplorationAction
import org.droidmate.exploration.actions.EnterTextExplorationAction
import org.droidmate.exploration.strategy.widget.Explore

class ComShreeHomeLogin : Explore(){
	override fun chooseAction(): AbstractExplorationAction {
		val hasUserNameField = this.currentState.widgets.any{ it.resourceId == "com.shree.home:id/useremail" }
		if (hasUserNameField){
			val wgt = this.currentState.widgets.first{ it.resourceId == "com.shree.home:id/useremail" }
			if (wgt.text == "Email")
				return EnterTextExplorationAction("abc@gmail.com", wgt)
		}

		val hasPasswordField = this.currentState.widgets.any{ it.resourceId == "com.shree.home:id/password" }
		if (hasPasswordField){
			val wgt = this.currentState.widgets.first{ it.resourceId == "com.shree.home:id/password" }
			if (wgt.text == "Password")
				return EnterTextExplorationAction("abc.123", wgt)
		}

		return ClickExplorationAction(this.currentState.widgets
				.first { it.resourceId == "com.shree.home:id/login_btn" })
	}

}