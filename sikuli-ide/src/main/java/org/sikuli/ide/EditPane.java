/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide;

import java.io.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;

/* Pane that contains all the comonents needed for a single editor:
 * - Command pane on the left for some editors (currently, just the Python text pane)
 * - Scroll pane on the right containing:
 * 		- A SikuliCodePane (currently either SikuliTextPane or BlocksPane)
 */

public class EditPane extends JPanel {
   private JComponent _commandPane;
   private JScrollPane _scrollPane;
   private SikuliCodePane _codePane;
   
   public EditPane(SikuliCodePane codePane, JComponent commandPane) {
		super(new BorderLayout(0,0));
		
		_commandPane = commandPane;
		_codePane = codePane;

		_scrollPane = new JScrollPane(_codePane.getComponent());

         this.add(_scrollPane, BorderLayout.CENTER);
         
         if(_commandPane != null)
        	 this.add(_commandPane, BorderLayout.WEST);
   }
   
   public EditPane(SikuliCodePane codePane) {
	   this(codePane, null);
   }

   public JComponent getCommandPane() {
	   return _commandPane;
   }
   
   public JScrollPane getScrollPane() {
	   return _scrollPane;
   }
   
   public SikuliCodePane getCodePane() {
	   return _codePane;
   }
}

