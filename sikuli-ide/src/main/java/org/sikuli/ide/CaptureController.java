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

class CaptureController implements Observer, Subject{
   protected boolean _isCapturing;
   protected String _imagePath;
   Observer _obs;

   public String toString(){
      return "\"__SIKULI-CAPTURE-CONTROLLER\"";
   }

   public CaptureController(){
      super();
   }
   
   public void addObserver(Observer o){
	   _obs = o;
   }
   public void notifyObserver(){
	   _obs.update(this);
   }
   
   public String getScreenshotPath()
   {
	   return _imagePath;
   }

   private String getFilenameFromUser(String hint){
      return (String)JOptionPane.showInputDialog(
            null,
            I18N._I("msgEnterScreenshotFilename"),
            I18N._I("dlgEnterScreenshotFilename"),
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            hint);
   }

   public void update(Subject s){
      if(s instanceof CapturePrompt){
         SikuliIDE ide = SikuliIDE.getInstance();
 		 ide.setVisible(true);
 		_isCapturing = false;

         CapturePrompt cp = (CapturePrompt)s;
         ScreenImage simg = cp.getSelection();
         if(simg==null){
        	 notifyObserver();
            return;
         }
         cp.close();
         SikuliCodePane pane = ide.getCurrentCodePane();
         int naming = UserPreferences.getInstance().getAutoNamingMethod();
         String filename;
         if(naming == UserPreferences.AUTO_NAMING_TIMESTAMP)
            filename = Utils.getTimestamp();
         else if(naming == UserPreferences.AUTO_NAMING_OCR){
            filename = NamingPane.getFilenameFromImage(simg.getImage());
            if(filename == null || filename.length() == 0)
               filename = Utils.getTimestamp();
         }
         else{
            String hint = NamingPane.getFilenameFromImage(simg.getImage());
            filename = getFilenameFromUser(hint);
            if(filename == null){
            	notifyObserver();
               return;
            }

         }
         String fullpath = 
        		 Utils.slashify(Utils.saveImage(simg.getImage(), filename, pane.getSrcBundle()), false);
         if(fullpath != null){
        	 _imagePath = fullpath;
         }
         notifyObserver();
      }
   }

   public void captureWithAutoDelay(){
      UserPreferences pref = UserPreferences.getInstance();
      int delay = (int)(pref.getCaptureDelay() * 1000.0) +1;
      capture(delay);
   }

   public void capture(final int delay){
      if(_isCapturing)
         return;
      SikuliIDE.getInstance().setVisible(false);
      _isCapturing = true;
      _imagePath = null;
      Thread t = new Thread("capture"){
         public void run(){
            try{
               Thread.sleep(delay);
            }
            catch(Exception e){}
            CapturePrompt p =  new CapturePrompt(null, CaptureController.this);
            p.prompt();
            try{
               Thread.sleep(500);
            }
            catch(Exception e){}
            p.requestFocus();
         }
      };
      t.start();
   }
}
