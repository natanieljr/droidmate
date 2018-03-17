// Copyright (c) 2012-2016 Saarland University
// All rights reserved.
//
// Author: Konrad Jamrozik, jamrozik@st.cs.uni-saarland.de
//
// This file is part of the "DroidMate" project.
//
// www.droidmate.org
package org.droidmate_usage_examples;

import org.droidmate.exploration.actions.ExplorationAction;
import org.droidmate.exploration.strategy.*;
import org.droidmate.exploration.strategy.widget.Explore;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * <p>
 * Constructs {@link ExampleExplorationStrategy}. This is a very minimalistic custom exploration strategy used to show you
 * how to inject into DroidMate your own custom strategy. Simply clicks on a random widget on the screen.
 *
 * </p><p>
 * For an example of how to actually write an exploration strategy, see the strategies in the packages:
 *   org.droidmate.exploretion.strategy.random
 *   org.droidmate.exploretion.strategy.back
 *   org.droidmate.exploretion.strategy.reset
 *   org.droidmate.exploretion.strategy.explore
 *
 * </p><p>
 * </p>
 */
class ExampleExplorationStrategy extends Explore {
    @NotNull
    @Override
    public ExplorationAction chooseAction(WidgetContext widgetContext) {
        Random random = new Random();
        List<WidgetInfo> widgets = widgetContext.getActionableWidgetsInfo();
        int i = random.nextInt(widgets.size());
        WidgetInfo widget = widgets.get(i);
        return ExplorationAction.newWidgetExplorationAction(widget.getWidget());
    }

    @NotNull
    @Override
    public StrategyPriority getFitness(WidgetContext widgetContext) {
        return StrategyPriority.PURELY_RANDOM_WIDGET;
    }
}