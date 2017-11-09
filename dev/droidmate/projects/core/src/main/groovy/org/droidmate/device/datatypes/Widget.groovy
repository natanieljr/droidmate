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

package org.droidmate.device.datatypes

import groovy.transform.AutoClone
import groovy.transform.Canonical
import org.droidmate.errors.UnexpectedIfElseFallthroughError

import java.awt.*
import java.util.regex.Matcher
import org.droidmate.exploration.actions.WidgetExplorationAction.Direction

@Canonical
@AutoClone
class Widget implements Serializable
{

  private static final long serialVersionUID = 1

	static final double ONTOUCH_DISPLAY_RELATION = 0.7

  /** Id is used only for tests, for:
   * - easy determination by human which widget is which when looking at widget string representation
   * - For asserting actual widgets match expected.
   * */
  String id = null

  int       index         = -1
  String    text          = ""
  String    resourceId    = ""
  String    className     = ""
  String    packageName   = ""
  String    contentDesc   = ""
  Boolean   checkable     = false
  Boolean   checked       = false
  Boolean   clickable     = false
  Boolean   enabled       = false
  Boolean   focusable     = false
  Boolean   focused       = false
  Boolean   scrollable    = false
  Boolean   longClickable = false
  Boolean   password      = false
  Boolean   selected      = false
  Rectangle bounds        = new Rectangle()
  Widget    parent        = null;

  /* WISH this actually shouldn't be necessary as the [dump] call is supposed to already return only the visible part, as
    it makes call to [visible-bounds] to obtain the "bounds" property of widget. I had problems with negative coordinates in
    com.indeed.android.jobsearch in.
    [dump] com.android.uiautomator.core.UiDevice.dumpWindowHierarchy
    [visible-bounds] com.android.uiautomator.core.AccessibilityNodeInfoHelper.getVisibleBoundsInScreen
    */
  /**
   * The widget is associated with a rectangle representing visible device display. This is the same visible display from whose
   * GUI structure this widget was parsed.
   *
   * The field is necessary to determine if at least one pixel of the widget is within the visible display and so, can be clicked.
   *
   * Later on DroidMate might add the ability to scroll first to make invisible widgets visible.
   */
  Rectangle deviceDisplayBounds = null

	public double getAreaSize(){
		return bounds.height * bounds.width
	}
	
	public double getDeviceAreaSize(){
		if (deviceDisplayBounds != null)
			return deviceDisplayBounds.height * deviceDisplayBounds.width
		else
			return -1
	}
	
  public Point center()
  {
    return new Point(bounds.getCenterX() as int, bounds.getCenterY() as int)
  }

  @Override
  public String toString()
  {
    assert deviceDisplayBounds != null

    return "Widget: $className ID: $id, text: $text, $boundsString, clickable: $clickable enabled: $enabled checkable: $checkable deviceDisplayBounds: [x=${deviceDisplayBounds.x},y=${deviceDisplayBounds},dx=${deviceDisplayBounds.width},dy=${deviceDisplayBounds.height}]"
  }

  public String getBoundsString()
  {
    return "[x=${bounds.x as int},y=${bounds.y as int},dx=${bounds.width as int},dy=${bounds.height as int}]"
  }

  public String getStrippedResourceId()
  {
    return this.resourceId - (this.packageName + ":")
  }

	public String getResourceIdName(){
		return this.resourceId - (this.packageName + ":id/")
	}

  public String toShortString()
  {
    String classSimpleName = className.substring(className.lastIndexOf(".") + 1)
    return "Wdgt:$classSimpleName/\"$text\"/\"$resourceId\"/[${bounds.centerX as int},${bounds.centerY as int}]"
  }

  public String toTabulatedString(boolean includeClassName = true)
  {
    String classSimpleName = className.substring(className.lastIndexOf(".") + 1)
    String pCls = classSimpleName.padRight(20, ' ')
    String pResId = resourceId.padRight(64, ' ')
    String pText = text.padRight(40, ' ')
    String pContDesc = contentDesc.padRight(40, ' ')
    String px = "${bounds.centerX as int}".padLeft(4, ' ')
    String py = "${bounds.centerY as int}".padLeft(4, ' ')

    String clsPart = includeClassName ? "Wdgt: $pCls / " : ""

    return "${clsPart}resId: $pResId / text: $pText / contDesc: $pContDesc / click xy: [$px,$py]"
  }

  boolean canBeActedUpon()
  {
    boolean canBeActedUpon = this.enabled && (this.clickable || this.checkable || this.longClickable || this.scrollable) && isVisibleOnCurrentDeviceDisplay()
    return canBeActedUpon
  }

  boolean isVisibleOnCurrentDeviceDisplay()
  {
    assert deviceDisplayBounds != null

    if (deviceDisplayBounds == null)
      return true;

    return bounds.intersects(deviceDisplayBounds)
  }

  Point getClickPoint()
  {
    if (deviceDisplayBounds == null)
      return new Point(this.center().x as int, this.center().y as int)

    assert bounds.intersects(deviceDisplayBounds)

    def clickRectangle = bounds.intersection(deviceDisplayBounds)

    return new Point(clickRectangle.centerX as int, clickRectangle.centerY as int)
  }
	
	def getSwipePoints(Direction direction, double percent){
		
		assert bounds.intersects(deviceDisplayBounds)
		
		Rectangle swipeRectangle = bounds.intersection(deviceDisplayBounds)
		double offsetHor = (swipeRectangle.getWidth() *  (1 - percent ) ) / 2
		double offsetVert =(swipeRectangle.getHeight() * (1 - percent ) ) / 2
		switch (direction)
		{
			case Direction.LEFT:
				return [ new Point((swipeRectangle.getMaxX() - offsetHor ) as int, swipeRectangle.getCenterY() as int ), new Point((swipeRectangle.getMinX() + offsetHor) as int, swipeRectangle.getCenterY() as int )  ]
				break
			case Direction.RIGHT:
				return [ new Point((this.bounds.getMinX() + offsetHor ) as int, this.bounds.getCenterY() as int ) , new Point((this.bounds.getMaxX() - offsetHor) as int, this.bounds.getCenterY() as int ) ]
				break
			case Direction.UP:
				return [ new Point(this.bounds.getCenterX() as int, (this.bounds.getMaxY() - offsetVert ) as int ), new Point(this.bounds.getCenterX() as int, ( this.bounds.getMinY() + offsetVert ) as int )]
				break
			
			case Direction.DOWN:
				return [ new Point(this.bounds.getCenterX() as int, ( this.bounds.getMinY() + offsetVert ) as int ), new Point(this.bounds.getCenterX() as int, ( this.bounds.getMaxY() - offsetVert ) as int ) ]
				break
			default:
				throw new UnexpectedIfElseFallthroughError()
		}
  }

  /**
   * <p>
   * Parses into a {@link java.awt.Rectangle} the {@code bounds} string, having format as output by
   * {@code android.graphics.Rect #toShortString(java.lang.StringBuilder)},
   * that is having form {@code [Xlow ,Ylow][Xhigh,Yhigh]}
   *
   * </p><p>
   * Such rectangle bounds format is being used internally by<br/>
   * {@code com.android.uiautomator.core.UiDevice #dumpWindowHierarchy(java.lang.String)}
   *
   * </p>
   */
  public static Rectangle parseBounds(String bounds)
  {
    assert bounds?.size() > 0

    Matcher boundsMatcher =
      // The input is of form "[xLow,yLow][xHigh,yHigh]" and the regex below will capture four groups: xLow yLow xHigh yHigh
      bounds =~ /\[(-?\p{Digit}+),(-?\p{Digit}+)\]\[(-?\p{Digit}+),(-?\p{Digit}+)\]/
    if (!boundsMatcher.matches())
      throw new InvalidWidgetBoundsException("The window hierarchy bounds matcher was unable to match $bounds against the regex")

    java.util.List<String> matchedGroups = boundsMatcher[0] as java.util.List<String>

    int lowX = matchedGroups[1] as int
    int lowY = matchedGroups[2] as int
    int highX = matchedGroups[3] as int
    int highY = matchedGroups[4] as int

    return new Rectangle(lowX, lowY, highX - lowX, highY - lowY);
  }

}
