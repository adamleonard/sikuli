/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;

import edu.mit.blocks.workspace.*;

import org.sikuli.script.Debug;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Date;
import java.util.Collections;

import java.util.concurrent.*;

/**
 * A simple undo manager for a block workspace that implements UndoableEdit
 * 
 * OpenBlocks doesn't support Swing's UndoManager,
 * and due to its infrastructure, it would be difficult to add support.
 * 
 *  Instead, it offers a custom, limited UndoManager called workspace.ReundoManager
 *  Unfortunately, it is very buggy, since it
 *  //FIXME: FINISH COMMENT.... hmm.. 
 *
 */

class BlocksUndoManager extends AbstractUndoableEdit implements WorkspaceListener {
	
	//Workspace events are grouped together into a single undo event
    //if they occur close together in time
    //specifically, if a given event occured less than
    //MILISECONDS_BETWEEN_EVENT_GROUPS before the previous event
	private static int MILISECONDS_BETWEEN_EVENT_GROUPS = 500;

    // Member Variables

    protected BlocksPane workspace;
    protected Document lastStableState;
    protected Stack<Document> undoStack;
    protected Stack<Document> redoStack;
    
    //This lock object prevents events raised during undoing/redoing
    //from being registered as user events. Set it to true to lock
    //out event registering
    private boolean lock = false;
    
    private boolean mouseDown = false;
    private boolean delayedStateSaveDueToMouseDown = false;

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture eventGroupingTask; 


    public BlocksUndoManager(BlocksPane aWorkspace) {
    	workspace = aWorkspace;

    	//we should not register undo events while the mouse is down
    	//for example, when dragging a block, a mouse press will send an event indicating the block was completely removed from the model
    	//it does not reappear until mouse up.
    	//so, register a global mouse event handler to figure out when we might be dragging anything, and delay state saves until after the mouse is up
    	class GlobalMouseDownListener implements AWTEventListener {
    		public void eventDispatched(AWTEvent event) {
                if(event instanceof MouseEvent){
                    MouseEvent mouseEvent = (MouseEvent)event;
                    if(mouseEvent.getID() == MouseEvent.MOUSE_PRESSED) {
        				mouseDown = true;
                    }
                    else if(mouseEvent.getID() == MouseEvent.MOUSE_RELEASED) {
                    	mouseDown = false;

        				if(delayedStateSaveDueToMouseDown) {
        		    		Runnable runnable = getStateSaveRunnable();
        					if(eventGroupingTask != null)
        						eventGroupingTask.cancel(false);
        					eventGroupingTask = scheduler.schedule(runnable, MILISECONDS_BETWEEN_EVENT_GROUPS, TimeUnit.MILLISECONDS);
        				}
                    }
                }
    		}
    	}
    	
    	Toolkit.getDefaultToolkit().addAWTEventListener(new GlobalMouseDownListener(), AWTEvent.MOUSE_EVENT_MASK);
    	
        this.reset();
    }

    public void reset() {
    	undoStack = new Stack<Document>();
    	redoStack = new Stack<Document>();

        //Initial state, nothing in the undo stack, and no current state
        //There is a workspace completed loading event that fires so that
        //the current state will be valid. (hence the is null test below.
    	lastStableState = null;
    }

    private Runnable getStateSaveRunnable() {
		final Runnable runnable = new Runnable() {
			public void run() {
				
				if(mouseDown) {
					delayedStateSaveDueToMouseDown = true;
					return;
				}
				
				//commit the lastStableState so if the user hits undo now, we will undo right before this group of actions
				undoStack.push(lastStableState);
				
				//remember the current state, which should be the last event in the group, and thus should be stable
				lastStableState = workspace.getSaveNode(false);
				
				//now that we've taken an action that was not an undo, redo should be disabled
				redoStack.empty();
				
				//update the menu items
				SikuliIDE.getInstance().updateUndoRedoStates();
			}
		};
		return runnable;
    }

    public void workspaceEventOccurred(WorkspaceEvent event) {
    	if (!lock) {            

    		if(!workspace.isWorkspaceLoaded()) {
    			//if the workspace hasn't finished loading, then we shouldn't record undo history
    			//however, we do need to know the initial stable state of the workspace,
    			//as it appears right after the workspace finishes loading.
    			//So, update lastStableState each time we get an event.
    			//The intermediate updates won't actually be stable, but we won't use them
    			//The last update we get before the workspace is loaded will be stable,
    			//so the first undo will undo to that state
    			lastStableState = workspace.getSaveNode(false);
    			return;

    		}
    		
    		//Group events that occur around the same time
    		
    		//This is needed because a single user action may trigger many workspace notifications
    		//Due to a poor architecture, the intermediate actions might not be stable, in that if we
    		//undo an intermediate state, the workspace may be inconsistant and exceptions are rampent (esp. with connections)
    		//However, the *last* workspace event we get for a single user action should be stable
    		//(That last event may or may not be called a userEvent by OpenBlocks, so we can't rely on that property to find stable events)
    		
    		//This grouping is also nice, because just like a text editor, if the user does a bunch of things quickly, a single undo will undo all of them
    		Runnable runnable = getStateSaveRunnable();
    		
    		//wait until MILISECONDS_BETWEEN_EVENT_GROUPS have elapsed from the previous event before updating the undo history
    		if(eventGroupingTask != null)
    			eventGroupingTask.cancel(false);
    		eventGroupingTask = scheduler.schedule(runnable, MILISECONDS_BETWEEN_EVENT_GROUPS, TimeUnit.MILLISECONDS);
    	}
    }

    public void undo() {
        if (canUndo() && !lock) {
            lock = true;
            {
                Document oldState = undoStack.pop();
                workspace.loadProjectFromElement(oldState.getDocumentElement());

                redoStack.push(lastStableState);
                lastStableState = oldState;
            }
            lock = false;
        }
    }

    public void redo() {
        if (canRedo() && !lock) {
            lock = true;
            {
                Document oldState = redoStack.pop();
                workspace.loadProjectFromElement(oldState.getDocumentElement());

                undoStack.push(lastStableState);
                lastStableState = oldState;
            }
            lock = false;
        }
    }

    public boolean canUndo() {
        return (undoStack.size() > 0);
    }

    public boolean canRedo() {
        return (redoStack.size() > 0);
    }

    public String getUndoPresentationName() {
        return "";
    }

    public String getRedoPresentationName() {
        return "";
    }
	
}
