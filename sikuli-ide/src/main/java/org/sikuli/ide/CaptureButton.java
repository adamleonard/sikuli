/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide;


import java.awt.*;
import java.awt.event.*;
import java.net.*;
import javax.swing.*;
import javax.swing.text.*;

import org.sikuli.script.Debug;
import org.sikuli.script.CapturePrompt;
import org.sikuli.script.ScreenImage;
import org.sikuli.script.Observer;
import org.sikuli.script.Subject;

class CaptureButton extends ToolbarButton implements ActionListener, Cloneable, Observer{
   protected Element _line;
   protected SikuliCodePane _codePane;
   protected CaptureController _captureController;

   public String toString(){
      return "\"__SIKULI-CAPTURE-BUTTON__\"";
   }

   public CaptureButton(){
      super();
      URL imageURL = SikuliIDE.class.getResource("/icons/camera-icon.png");
      setIcon(new ImageIcon(imageURL));
      UserPreferences pref = UserPreferences.getInstance();
      String strHotkey = Utils.convertKeyToText(
            pref.getCaptureHotkey(), pref.getCaptureHotkeyModifiers() );
      setToolTipText(SikuliIDE._I("btnCaptureHint", strHotkey));
      setText(SikuliIDE._I("btnCaptureLabel"));
      //setBorderPainted(false);
      //setMaximumSize(new Dimension(26,26));
      addActionListener(this);
      _line = null;
      _captureController = new CaptureController();
      _captureController.addObserver(this);
   }

   public CaptureButton(SikuliCodePane codePane, Element elmLine){
      this();
      _line = elmLine;
      _codePane = codePane;
      setUI(UIManager.getUI(this));
      setBorderPainted(true);
      setCursor(new Cursor (Cursor.HAND_CURSOR));
      setText(null);
      URL imageURL = SikuliIDE.class.getResource("/icons/capture.png");
      setIcon(new ImageIcon(imageURL));
   }

   public boolean hasNext(){  return false;  }
   public CaptureButton getNextDiffButton(){ return null; }
   public void setParentPane(SikuliCodePane parent){
      _codePane = parent;
   }

   public void setDiffMode(boolean flag){}
   
   public void setSrcElement(Element elmLine){
      _line = elmLine;
   }

   public Element getSrcElement(){  return _line;  }

   public void update(Subject s){
	   if(s instanceof CaptureController){
		   CaptureController captureController = (CaptureController)s;
		   String screenshotPath = captureController.getScreenshotPath();
		   
		   SikuliCodePane codePaneForScreenshot = _codePane;
		   if(codePaneForScreenshot == null)
			   codePaneForScreenshot = SikuliIDE.getInstance().getCurrentCodePane();
		   
		   if(screenshotPath != null && codePaneForScreenshot != null) {
			   codePaneForScreenshot.insertScreenshot(screenshotPath, getSrcElement());
		   }
	   }
   }

   public void captureWithAutoDelay(){
      _captureController.captureWithAutoDelay();
   }

   public void capture(final int delay){
	   _captureController.capture(delay);
   }

   public void actionPerformed(ActionEvent e) {
      Debug.log(2, "capture!");
      captureWithAutoDelay();
   }
}
