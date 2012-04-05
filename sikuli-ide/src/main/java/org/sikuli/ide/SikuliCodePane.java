/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide;

import java.awt.*;
import javax.swing.*;
import java.io.*;
import javax.swing.text.*;

public interface SikuliCodePane <T extends JComponent> {
	
	/**
	 * Writes the Python representation of this code pane to the given Writer
	 * The result should be runnable with JPython 
	 */
	public void writePython(Writer writer) throws IOException;
	
	public void insertScreenshot(String path, Element src);
	
	public UndoManager getUndoManager();
	
	public boolean isDirty();
	public void setDirty(boolean flag);
	
	public boolean close() throws IOException;
	
	public String getCurrentShortFilename();
	public File getCurrentFile();
		
	public String saveFile() throws IOException;
	public String saveAsFile() throws IOException;
	
	public String exportAsZip() throws IOException, FileNotFoundException;
	public void loadFile(String filename) throws IOException;
	public String getSrcBundle();
	public File copyFileToBundle(String filename);
	public File getFileInBundle(String filename);
	
	public int search(String str, int pos, boolean forward);
	
	public T getComponent();

}