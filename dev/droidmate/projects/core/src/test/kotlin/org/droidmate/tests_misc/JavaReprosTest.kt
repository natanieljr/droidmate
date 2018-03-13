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

package org.droidmate.tests_misc;

import de.erichseifert.vectorgraphics2d.PDFGraphics2D
import org.junit.Test
import java.awt.Font
import java.io.FileOutputStream
import java.io.IOException

class JavaReprosTest
{

  /**
   * Repro for: https://github.com/eseifert/vectorgraphics2d/issues/41
   * 
   * See also: https://answers.acrobatusers.com/The-font-LucidaGrande-bad-BBox-Error-opening-OCR-documents-Acrobat-XI-Pro-Mac-q36986.aspx
   */
  
  // Gradle dependency: testCompile 'de.erichseifert.vectorgraphics2d:VectorGraphics2D:0.10'
  @Test
  @Throws(IOException::class)
  fun BBoxError(){
      val pdf = PDFGraphics2D(0.0, 0.0, 100.0, 100.0)
      System.out.println(pdf.font)
      // Prints out: java.awt.Font[family=Dialog,name=Dialog,style=plain,size=12]

      var file = FileOutputStream("test1.pdf")
      file.write(pdf.bytes)
      file.close()

      // Open test1.pdf with Adobe Acrobat Reader DC (15.016.20045) on Windows 10
      // See a dialog box: "The font 'Dialog.plain' contains bad /BBox."

      pdf.font = Font(Font.SANS_SERIF, Font.BOLD, 14)
      System.out.println(pdf.font)
      // Prints out: java.awt.Font[family=SansSerif,name=SansSerif,style=bold,size=14]
      file = FileOutputStream("test2.pdf")
      file.write(pdf.bytes)
      file.close()

      // Open test2.pdf with Adobe Acrobat Reader DC (15.016.20045) on Windows 10
      // See a dialog box: "The font 'Dialog.plain' contains bad /BBox."
      // I.e. the message is the same, even though the font was changed.

      // Both test1.pdf and test2.pdf in "Document Propeties -> Fonts" show:
      //   Dialog.plain
      //     Type: TrueType
      //     Encoding: Ansi
      //     Actual font: Adobe Sans MM
      //     Actual Font Type: Type 1
  }
}

