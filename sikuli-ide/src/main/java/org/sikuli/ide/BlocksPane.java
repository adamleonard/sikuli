/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.Iterable;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.undo.*;

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
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.mit.blocks.codeblocks.*;
import edu.mit.blocks.workspace.*;
import edu.mit.blocks.workspace.typeblocking.*;
import edu.mit.blocks.renderable.*;

import org.sikuli.script.ImageLocator;
import org.sikuli.script.Location;
import org.sikuli.script.Debug;
import org.sikuli.script.ScreenImage;
import org.sikuli.script.Observer;
import org.sikuli.script.Subject;
import org.sikuli.script.ScriptRunner;

import org.python.util.PythonInterpreter; 
import org.python.core.*; 

/**
 * Example entry point to OpenBlock application creation.
 *
 * @author Ricarose Roque
 */
public class BlocksPane extends Workspace implements Observer, WorkspaceListener, SikuliCodePane {

    private Element langDefRoot;
    protected SearchBar searchBar;

    //flag to indicate if the workspace has been initialized with the contents of a file
    private boolean workspaceLoaded = false;
    //flag to indicate if a new lang definition file has been set
    private boolean langDefDirty = true;

    // last directory that was selected with open or save action
    private File lastDirectory;
    // file currently loaded in workspace
    private File selectedFile;
    
    private Thread _runningThread;
    
    private boolean autoCaptureEnabled = false;
    
    private String _srcBundlePath = null;
    
    private boolean _dirty = false;
    
    private ImageLocator _imgLocator;
    
    private BlocksUndoManager _undoManager;
    
    static InputStream SikuliToHtmlConverter = SikuliIDE.class.getResourceAsStream("/scripts/sikuli2html.py");
    static String pyConverter = Utils.convertStreamToString(SikuliToHtmlConverter);

    static InputStream SikuliBundleCleaner= SikuliIDE.class.getResourceAsStream("/scripts/clean-dot-sikuli.py");
    static String pyBundleCleaner = Utils.convertStreamToString(SikuliBundleCleaner);
    
    
    /**
     * Constructs a WorkspaceController instance that manages the
     * interaction with the codeblocks.Workspace
     *
     */
    public BlocksPane() {
    	super();
    	setPreferredSize(null); //Let the scroll view that contains us choose our size to fit
        this.addWorkspaceListener(this);
        _undoManager = new BlocksUndoManager(this);
        this.addWorkspaceListener(_undoManager);
        this.enableTypeBlocking(true);
        
        this.getActionMap().put(DefaultEditorKit.copyAction, new AbstractAction() {
        	public void actionPerformed(ActionEvent evt) {
        		copyBlocks();
        	}
        });
        
        this.getActionMap().put(DefaultEditorKit.cutAction, new AbstractAction() {
        	public void actionPerformed(ActionEvent evt) {
        		cutBlocks();
        	}
        });
        
        this.getActionMap().put(DefaultEditorKit.pasteAction, new AbstractAction() {
        	public void actionPerformed(ActionEvent evt) {
        		pasteBlocks();
        	}
        });
    }

    /**
     * Sets the file path for the language definition file, if the
     * language definition file is located in
     */
    public void setLangDefFilePath(final String filePath) {
        InputStream in = null;
        try {
            in = new FileInputStream(filePath);
            setLangDefStream(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Sets language definition file from the given input stream
     * @param in input stream to read
     */
    public void setLangDefStream(InputStream in) {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder;
        final Document doc;
        try {
            builder = factory.newDocumentBuilder();
            doc = builder.parse(in);
            langDefRoot = doc.getDocumentElement();
            langDefDirty = true;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads all the block genuses, properties, and link rules of
     * a language specified in the pre-defined language def file.
     * @param root Loads the language specified in the Element root
     */
    public void loadBlockLanguage(final Element root) {
        /* MUST load shapes before genuses in order to initialize
         connectors within each block correctly */
        BlockConnectorShape.loadBlockConnectorShapes(root);

        //load genuses
        BlockGenus.loadBlockGenera(this, root);

        //load rules
        BlockLinkChecker.addRule(this, new CommandRule(this));
        BlockLinkChecker.addRule(this, new SocketRule());
        BlockLinkChecker.addRule(this, new AnyBlockSocketRule());

        //set the dirty flag for the language definition file
        //to false now that the lang file has been loaded
        langDefDirty = false;
    }

    /**
     * Resets the current language within the active
     * Workspace.
     *
     */
    public void resetLanguage() {
        BlockConnectorShape.resetConnectorShapeMappings();
        getEnv().resetAllGenuses();
        BlockLinkChecker.reset();
    }

    /**
     * Returns the save string for the entire workspace.  This includes the block workspace, any
     * custom factories, canvas view state and position, pages
     * @return the save string for the entire workspace.
     */
    public String getSaveString() {
        try {
            Node node = getSaveNode();

            StringWriter writer = new StringWriter();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        }
        catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
        catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a DOM node for the entire workspace.  This includes the block workspace, any
     * custom factories, canvas view state and position, pages
     * @return the DOM node for the entire workspace.
     */
    public Document getSaveNode() {
    	//FIXME: re-enable validation once the schema is updated
        return getSaveNode(false);
    }

    /**
     * Returns a DOM node for the entire workspace. This includes the block
     * workspace, any custom factories, canvas view state and position, pages
     *
     * @param validate If {@code true}, perform a validation of the output
     * against the code blocks schema
     * @return the DOM node for the entire workspace.
     */
    public Document getSaveNode(final boolean validate) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Element documentElement = document.createElementNS(Constants.XML_CODEBLOCKS_NS, "cb:CODEBLOCKS");
            // schema reference
            documentElement.setAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "xsi:schemaLocation", Constants.XML_CODEBLOCKS_NS+" "+Constants.XML_CODEBLOCKS_SCHEMA_URI);

            Node workspaceNode = this.getSaveNode(document);
            if (workspaceNode != null) {
                documentElement.appendChild(workspaceNode);
            }

            document.appendChild(documentElement);
            if (validate) {
                validate(document);
            }

            return document;
        }
        catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Validates the code blocks document against the schema
     * @param document The document to check
     * @throws RuntimeException If the validation failed
     */
    private void validate(Document document) {
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            URL schemaUrl = ClassLoader.getSystemResource("edu/mit/blocks/codeblocks/codeblocks.xsd");
            Schema schema = schemaFactory.newSchema(schemaUrl);
            Validator validator = schema.newValidator();
            validator.validate(new DOMSource(document));
        }
        catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        catch (SAXException e) {
            //throw new RuntimeException(e);
        	e.printStackTrace();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a fresh workspace based on the default specifications in the language
     * definition file.  The block canvas will have no live blocks.
     */
    public void loadFreshWorkspace() {
        if (workspaceLoaded) {
            resetWorkspace();
        }
        if (langDefDirty) {
            loadBlockLanguage(langDefRoot);
        }
        this.loadWorkspaceFrom(null, langDefRoot);
        
    	Block runonceBlock = new Block(this, "runonce", "run once", true);
    	RenderableBlock runonceRenderableBlock = BlockUtilities.cloneBlock(runonceBlock);
        this.getBlockCanvas().getCanvas().add(runonceRenderableBlock, 0);
        runonceRenderableBlock.setLocation(50, 25);
        
    	Page p = this.getBlockCanvas().getPages().get(0); //FIXME: this won't work with multiple pages.
    	//add this block to that page.
    	p.blockDropped(runonceRenderableBlock);
    	
        workspaceLoaded = true;    	
    }
    
    public boolean isWorkspaceLoaded() {
    	return workspaceLoaded;
    }

    /**
     * Loads the programming project from the specified file path.
     * This method assumes that a Language Definition File has already
     * been specified for this programming project.
     * @param path String file path of the programming project to load
     */
    public void loadProjectFromPath(final String path) {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final DocumentBuilder builder;
        final Document doc;
        try {
            builder = factory.newDocumentBuilder();
            doc = builder.parse(new File(path));

            // XXX here, we could be strict and only allow valid documents...
            // validate(doc);
            final Element projectRoot = doc.getDocumentElement();
            //load the canvas (or pages and page blocks if any) blocks from the save file
            //also load drawers, or any custom drawers from file.  if no custom drawers
            //are present in root, then the default set of drawers is loaded from
            //langDefRoot
            this.reset();
            this.loadWorkspaceFrom(projectRoot, langDefRoot);
            workspaceLoaded = true;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads the programming project from the specified element. This method
     * assumes that a Language Definition File has already been specified for
     * this programming project.
     *
     * @param element element of the programming project to load
     */
    public void loadProjectFromElement(Element elementToLoad) {
        if (workspaceLoaded) {
        	resetWorkspace();
        }
        this.loadWorkspaceFrom(elementToLoad, langDefRoot);
        workspaceLoaded = true;
    }

    /**
     * Loads the programming project specified in the projectContents String,
     * which is associated with the language definition file contained in the
     * specified langDefContents.  All the blocks contained in projectContents
     * must have an associted block genus defined in langDefContents.
     *
     * If the langDefContents have any workspace settings such as pages or
     * drawers and projectContents has workspace settings as well, the
     * workspace settings within the projectContents will override the
     * workspace settings in langDefContents.
     *
     * NOTE: The language definition contained in langDefContents does
     * not replace the default language definition file set by: setLangDefFilePath() or
     * setLangDefFile().
     *
     * @param projectContents
     * @param langDefContents String XML that defines the language of
     * projectContents
     */
    public void loadProject(String projectContents, String langDefContents) {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder;
        final Document projectDoc;
        final Document langDoc;
        try {
            builder = factory.newDocumentBuilder();
            projectDoc = builder.parse(new InputSource(new StringReader(projectContents)));
            final Element projectRoot = projectDoc.getDocumentElement();
            langDoc = builder.parse(new InputSource(new StringReader(projectContents)));
            final Element langRoot = langDoc.getDocumentElement();
            if (workspaceLoaded) {
                resetWorkspace();
            }
            if (langDefContents == null) {
                loadBlockLanguage(langDefRoot);
            } else {
                loadBlockLanguage(langRoot);
            }
            this.loadWorkspaceFrom(projectRoot, langRoot);
            workspaceLoaded = true;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resets the entire workspace.  This includes all blocks, pages, drawers, and trashed blocks.
     * Also resets the undo/redo stack.  The language (i.e. genuses and shapes) is not reset.
     */
    public void resetWorkspace() {
        //clear all pages and their drawers
        //clear all drawers and their content
        //clear all block and renderable block instances
        workspaceLoaded = false;
        this.reset();
    }

    public void workspaceEventOccurred(WorkspaceEvent event) {
    	if(event.isUserEvent() && workspaceLoaded) {
    		//if the user initiated an action and that action was not opening a new file,
    		//mark the document as dirty and indicate that the undo manager may have changed states
    		setDirty(true);
    		SikuliIDE.getInstance().updateUndoRedoStates();
    	}
    	
    	if(event.getEventType() == WorkspaceEvent.BLOCK_ADDED && event.isUserEvent()) {
    		Long blockID = event.getSourceBlockID();
			assert !invalidBlockID(blockID);
			Block addedBlock = getEnv().getBlock(blockID);
			if(addedBlock.getNumSockets() > 0) {
				BlockConnector socket = addedBlock.getSocketAt(0);
			
				if(socket.getKind().equals("screenshot") && autoCaptureEnabled) {
					//if the user added a block with a screenshot connector, and auto capture is enabled, start taking a screenshot
					capture(0);
				}
			}
    	}
    	else if(event.getEventType() == WorkspaceEvent.BLOCK_STACK_COMPILED) {
    		Long blockID = event.getSourceBlockID();
			assert !invalidBlockID(blockID);
			Block clickedBlock = getEnv().getBlock(blockID);
			if(clickedBlock.getGenusName().equals("screenshot-block")) {
				this.getFocusManager().setFocus(blockID);
				capture(0);
			}
    	}
    	else if(event.getEventType() == WorkspaceEvent.BLOCKS_CONNECTED) {
    		BlockLink link = event.getSourceLink();
    		 if (link.getSocketBlockID() != null) {
    			 Block socketBlock = getEnv().getBlock(link.getSocketBlockID());
    			 String genusName = socketBlock.getGenusName();
    			 //FIXME: clean this up a lot
    			 if(genusName.equals("call-function") || 
    					 genusName.equals("call-function-import") ||
    					 genusName.equals("define-function") ||
    					 genusName.equals("python-expression") ||
    					 genusName.equals("python-statement")) {
    					
    				 //if we just connected something to a call function block,
    				 //see if we connected it to the LAST argument socket
    				 //if so, add space for another arguemnt

    				 int lastArgumentIndex = socketBlock.getNumSockets() - 1;
    				 if(genusName.equals("define-function"))
    					 lastArgumentIndex -= 1; //for define-function, the last socket contains the command children, which is not an argument 

    				 BlockConnector socket = link.getSocket();

    				 if(socketBlock.getSocketIndex(socket) == lastArgumentIndex) {

    					 //remove the parens around the socket label to indicate that it has been filled, and is thus active
    					 socket.setLabel(socket.getLabel().substring(1, socket.getLabel().length() - 1));
    					 
    					 //add another arguments socket
    					 int nextArgumentNumber = socketBlock.getNumSockets();
    					 if(genusName.equals("call-function-import") || genusName.equals("define-function"))
    						 nextArgumentNumber -= 1; //account for the import/command socket, which is not an argument
    					 if(genusName.equals("python-expression") || genusName.equals("python-statement"))
    						 nextArgumentNumber += 1; //the very first (0th) socket is an argument
    					 
    					 String argumentPrefix = "arg ";
    					 if(genusName.equals("python-expression") || genusName.equals("python-statement"))
    						 argumentPrefix = "$";
    					 
    					 String socketName = "(" + argumentPrefix + nextArgumentNumber + ")";
    					 socketBlock.addSocket(lastArgumentIndex + 1, genusName.equals("define-function") ? "variable" : "any", BlockConnector.PositionType.SINGLE, socketName, false, false, Block.NULL);
    					 socketBlock.notifyRenderable();
    				 }
    			 }
    		 }
    	}
    }

    /**
     * Action bound to "Open" action.
     */
    /*
    private class OpenAction extends AbstractAction {

        private static final long serialVersionUID = -2119679269613495704L;

        OpenAction() {
            super("Open");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser(lastDirectory);
            if (fileChooser.showOpenDialog((Component)e.getSource()) == JFileChooser.APPROVE_OPTION) {
                setSelectedFile(fileChooser.getSelectedFile());
                lastDirectory = selectedFile.getParentFile();
                String selectedPath = selectedFile.getPath();
                loadFreshWorkspace();
                loadProjectFromPath(selectedPath);
            }
        }
    }
    */

    /**
     * Action bound to "Save" button.
     */
    /*
    private class SaveAction extends AbstractAction {
        private static final long serialVersionUID = -5540588250535739852L;
        SaveAction() {
            super("Save");
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            if (selectedFile == null) {
                JFileChooser fileChooser = new JFileChooser(lastDirectory);
                if (fileChooser.showSaveDialog((Component) evt.getSource()) == JFileChooser.APPROVE_OPTION) {
                    setSelectedFile(fileChooser.getSelectedFile());
                    lastDirectory = selectedFile.getParentFile();
                }
            }
            try {
                saveToFile(selectedFile);
            }
            catch (IOException e) {
                JOptionPane.showMessageDialog((Component) evt.getSource(),
                        e.getMessage());
            }
        }
    }
    */

    /**
     * Action bound to "Save As..." button.
     */
    /*
    private class SaveAsAction extends AbstractAction {
         private static final long serialVersionUID = 3981294764824307472L;
        private final SaveAction saveAction;

        SaveAsAction(SaveAction saveAction) {
            super("Save As...");
            this.saveAction = saveAction;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            selectedFile = null;
            // delegate to save action
            saveAction.actionPerformed(e);
        }
    }
    */
    
    /**
     * Action bound to "Add Function..." button.
     */
    /*
    private class AddFunctionAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        AddFunctionAction() {
            super("Add Function...");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        	createAndShowAddFunctionDialog();
        }
    }
    */
    
    /**
     * Action bound to "Screenshot..." button.
     */
    /*
    private class ScreenshotAction extends AbstractAction {
         private static final long serialVersionUID = 1L;
         
        ScreenshotAction() {
            super("Take Screenshot");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        	capture(0);
        }
    }
    */
    
    /**
     * Action bound to "Run" button.
     */
    /*
    private class RunAction extends AbstractAction {
         private static final long serialVersionUID = 1L;
         
        RunAction() {
            super("Run");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        	compileAndRun();
        }
    }
    */
    /**
     * Action bound to "Auto Capture" checkbox.
     */
    /*
    private class AutoCaptureToggleAction extends AbstractAction {
         private static final long serialVersionUID = 1L;
         
         AutoCaptureToggleAction() {
            super("Auto Capture");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        	autoCaptureEnabled = true;
        }
    }
    */

    /**
     * Saves the content of the workspace to the given file
     * @param file Destination file
     * @throws IOException If save failed
     */
    private void writeSrcFile(boolean writeHTML) throws IOException {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(selectedFile);
            fileWriter.write(getSaveString());
        }
        finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
        
        if(writeHTML)
            convertSrcToHtml(getSrcBundle());
        
        cleanBundle(getSrcBundle());
        setDirty(false);
    }

    public void setSelectedFile(File selectedFile) {
        this.selectedFile = selectedFile;
    }

    private static boolean invalidBlockID(Long blockID) {
    	if (blockID == null) {
    		return true;
    	} else if (blockID.equals(Block.NULL)) {
    		return true;
    	} else {
    		return false;
    	}
    }
    
    public void capture(int delay) {
    	CaptureController captureController =  new CaptureController();
    	captureController.addObserver(this);
    	captureController.capture(delay);
    }
    public void update(Subject s) {
        if(s instanceof CaptureController){
        	CaptureController captureController = (CaptureController)s;
        	String screenshotPath = captureController.getScreenshotPath();
        	if(screenshotPath != null)
        		createOrEditScreenshotBlock(screenshotPath);
        }
    }
    
    private void changeScreenshotImages(Block screenshotBlock, String screenshotPath, Map<BlockImageIcon.ImageLocation, BlockImageIcon> blockImageMap)
    {
    	//modify the existing screenshot block
		RenderableBlock screenshotRenderableBlock = getEnv().getRenderableBlock(screenshotBlock.getBlockID());
		screenshotBlock.setBlockImageMap(blockImageMap);
		screenshotBlock.setProperty("screenshot-path", screenshotPath);
		
		Long screenshotParentBlockID = screenshotBlock.getPlugBlockID();
		if(!invalidBlockID(screenshotParentBlockID)) {
			//if this screenshot block has a parent, redraw the parent, and in turn, the screenshot
			RenderableBlock screenshotParentRenderableBlock = getEnv().getRenderableBlock(screenshotParentBlockID);
			
			/*
			BlockLink link = BlockLink.getBlockLink(this, screenshotParentRenderableBlock.getBlock(),
													screenshotBlock,
													screenshotParentRenderableBlock.getBlock().getSocketAt(0), 
													screenshotBlock.getPlug());
            workspace.notifyListeners(new WorkspaceEvent(
                    workspace, 
                    screenshotParentRenderableBlock.getParentWidget(),
                    link, WorkspaceEvent.BLOCKS_CONNECTED));
            */
			BlockConnector connectedSocket = null;
			for(BlockConnector aConnector : screenshotParentRenderableBlock.getBlock().getSockets()) {
				if(aConnector.getBlockID() == screenshotBlock.getBlockID()) {
					connectedSocket = aConnector;
					break;
				}
			}
			assert connectedSocket != null;
			screenshotParentRenderableBlock.blockConnected(connectedSocket, screenshotBlock.getBlockID());
			screenshotParentRenderableBlock.redrawFromTop();

		}
		else {
			//otherwise, just redraw the screenshot
			screenshotRenderableBlock.repaintBlock();
			screenshotRenderableBlock.repaint();
		}
    }
    
    private void createOrEditScreenshotBlock(String screenshotPath) {
    	ImageIcon image = new ImageIcon(screenshotPath);
    	URL screenshotURL = null;
    	try {
    		screenshotURL = new File(screenshotPath).toURL();
    	} catch(MalformedURLException e) {
    		 Debug.error(e.toString());
    		 return;
    	}
    	
    	BlockImageIcon blockImage = new BlockImageIcon(image, screenshotURL, BlockImageIcon.ImageLocation.CENTER, false, false);
    	Map<BlockImageIcon.ImageLocation, BlockImageIcon> blockImageMap = new HashMap<BlockImageIcon.ImageLocation, BlockImageIcon>();
    	blockImageMap.put(BlockImageIcon.ImageLocation.CENTER, blockImage);
    	
    	
    	FocusTraversalManager focusManager = this.getFocusManager();
    	BlockCanvas blockCanvas = this.getBlockCanvas();
    	Long parentBlockID = focusManager.getFocusBlockID();
    	if(!invalidBlockID(parentBlockID)) {
    		Block parentBlock = getEnv().getBlock(parentBlockID);
    		if(parentBlock.getGenusName().equals("screenshot-block")) {
    			changeScreenshotImages(parentBlock, screenshotPath, blockImageMap);
    			return;

    		}
    	}
    	
    	//create a new screenshot block
    	
    	Block screenshotBlock = new Block(this, "screenshot-block", "", true);
    	
    	screenshotBlock.setBlockImageMap(blockImageMap);
    	
    	RenderableBlock block = BlockUtilities.cloneBlock(screenshotBlock);
        Debug.error("Creating screenshot block: " + block.toString());
    	
    	block.getBlock().setProperty("screenshot-path", screenshotPath);

    	boolean didConnectBlock = false;

        if(!invalidBlockID(parentBlockID)) {
            RenderableBlock parentRenderableBlock = getEnv().getRenderableBlock(parentBlockID);
            if(parentRenderableBlock != null && parentRenderableBlock.isVisible()) {
	        	Debug.error("Adding as child of block: " + parentRenderableBlock.toString());
	        	//find a socket for the new screenshot
	        	Iterable<BlockConnector> sockets = parentRenderableBlock.getBlock().getSockets();
	        	int i = 0;
	        	//first, try an empty socket
	        	for(BlockConnector aConnector : sockets) {
					if(!aConnector.hasBlock() && aConnector.getKind().equals("screenshot")) {
						//empty screenshot socket! use that
						BlockLink link = BlockLink.getBlockLink(this, parentRenderableBlock.getBlock(),
								block.getBlock(),
								parentRenderableBlock.getBlock().getSocketAt(i), 
								block.getBlock().getPlug());
						link.connect();
				        
				        this.notifyListeners(new WorkspaceEvent(
	                            this, 
	                            getEnv().getRenderableBlock(link.getPlugBlockID()).getParentWidget(),
	                            link, WorkspaceEvent.BLOCKS_CONNECTED));
	                    getEnv().getRenderableBlock(link.getSocketBlockID()).moveConnectedBlocks();
	                    getEnv().getRenderableBlock(link.getSocketBlockID()).repaintBlock();
	                    getEnv().getRenderableBlock(link.getSocketBlockID()).repaint();
	                    
	                	Page p = this.getBlockCanvas().getPages().get(0);
	                	//add this block to that page.
	                	p.blockDropped(block);
	                    
	                    
	                    focusManager.setFocus(block.getBlockID());
	                    didConnectBlock = true;
				        
						break;
					}
					i ++;
				}

	        	if(!didConnectBlock) {
	        		//second, try to replace a block in an existing screenshot socket
	        		for(BlockConnector aConnector : sockets) {
	        			if(aConnector.hasBlock() && aConnector.getKind().equals("screenshot")) {
	        				changeScreenshotImages(getEnv().getBlock(aConnector.getBlockID()), screenshotPath, blockImageMap);
	        				focusManager.setFocus(block.getBlockID());
	        				didConnectBlock = true;
	        				break;
	        			}
	        		}
	        	}
            }
        }
        if(!didConnectBlock) {
            blockCanvas.getCanvas().add(block, 0);
            block.setLocation(25, 25);
            
        	Page p = this.getBlockCanvas().getPages().get(0);
        	//add this block to that page.
        	p.blockDropped(block);
        }
    }
    
    private void compileAndRun() {
    	final String source = compileToPython();
    	_runningThread = new Thread(){
            public void run(){
               File tmpFile;
               try{
                  tmpFile = File.createTempFile("sikuli-tmp",".py");
                  Debug.error("PATH:" + tmpFile.toString());
                  tmpFile.deleteOnExit();
                  try{
                     BufferedWriter bw = new BufferedWriter(
                                           new OutputStreamWriter( 
                                             new FileOutputStream(tmpFile), 
                                              "UTF8"));
                     bw.write(source, 0, source.length());
                     bw.close();
                     SikuliIDE.getInstance().setVisible(false);
                     
                     ScriptRunner srunner = new ScriptRunner(SikuliIDE.getInstance().getPyArgs());
                     try{
                        String path = SikuliIDE.getInstance().getCurrentBundlePath();
                        srunner.addTempHeader("initSikuli()");
                        srunner.runPython(path, tmpFile);
                        srunner.close();
                     }
                     catch(Exception e){
                        srunner.close();
                        throw e;
                     }
                     
                  }
                  catch(Exception e){
                        Debug.error(e.toString());
                  } 
                  finally{
                     SikuliIDE.getInstance().setVisible(true);
                     _runningThread = null;
                  }
               }
               catch(IOException e){ e.printStackTrace(); }
            }
         };
         _runningThread.start();
    }
    
    public String compileToPython() {
    	String source = "setThrowException(False)\n";
    	String topLevelCode = "";
    	for (Block aBlock : this.getBlocks()) {
    		String isRootBlockAsString = aBlock.getProperty("is-root-block");
    		if(isRootBlockAsString != null && isRootBlockAsString.equals("yes") && aBlock.getBeforeBlockID() == Block.NULL) {
    			BlockCompiler compiler = new BlockCompiler(this);
    			String result = compiler.compile(aBlock);
    			result += "\n\n";
    			
    			//the order of Python functions in source code generally doesn't matter
    			//i.e., function A can call function B if A is above B OR B is above A
    			//the exception is top-level code, which runs sequentially, binding functions as it goes
    			//so, top-level code at the top of a file cannot call functions defined later in the file
    			//so, we should ensure that top-level-code generating blocks that call functions, namely runOnce
    			//are ALWAYS at the bottom of our file, after all the function definitions
    			//FIXME: functions-inside-functions can also be problematic: function definitons need to go first there too
    			//but, we currently don't allow functions-inside-functions
    			String isTopLevelCodeAsString = aBlock.getProperty("yields-top-level-code");
    			if(isTopLevelCodeAsString != null && isTopLevelCodeAsString.equals("yes")) {
    				topLevelCode += result;
    			}
    			else {
    				source += result;
    			}
    			
    		}
    	}
    	source += topLevelCode; //add the top-level code to the end of the file
    	Debug.error("SOURCE: \n" + source);
    	return source;
    }

    /**
     * Return the lower button panel.
     */
    /*
    private JComponent getButtonPanel() {
        JPanel buttonPanel = new JPanel();
        // Open
        OpenAction openAction = new OpenAction();
        buttonPanel.add(new JButton(openAction));
        // Save
        SaveAction saveAction = new SaveAction();
        buttonPanel.add(new JButton(saveAction));
        // Save as
        SaveAsAction saveAsAction = new SaveAsAction(saveAction);
        buttonPanel.add(new JButton(saveAsAction));
        // Add Function
        AddFunctionAction addFunctionAction = new AddFunctionAction();
        buttonPanel.add(new JButton(addFunctionAction));
        // Capture Screenshot
        ScreenshotAction screenshotAction = new ScreenshotAction();
        buttonPanel.add(new JButton(screenshotAction));   
        // Run
        RunAction runAction = new RunAction();
        buttonPanel.add(new JButton(runAction));
        // Auto Capture Toggle
        AutoCaptureToggleAction autoCaptureToggleAction = new AutoCaptureToggleAction();
        buttonPanel.add(new JCheckBox(autoCaptureToggleAction));
        
        return buttonPanel;
    }
    */

    /**
     * Returns a SearchBar instance capable of searching for blocks
     * within the BlockCanvas and block drawers
     */
    /*
    public JComponent getSearchBar() {
        final SearchBar sb = new SearchBar(
                "Search blocks", "Search for blocks in the drawers and workspace", this);
        for (SearchableContainer con : getAllSearchableContainers()) {
            sb.addSearchableContainer(con);
        }
        return sb.getComponent();
    }
    */

    /**
     * Create the GUI and show it.  For thread safety, this method should be
     * invoked from the event-dispatching thread.
     */
    /*
    public void createAndShowGUI() {
        frame = new JFrame("Sikuli Blocks");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(100, 100, 1000, 1000);
        final SearchBar sb = new SearchBar("Search blocks",
                "Search for blocks in the drawers and workspace", this);
        for (final SearchableContainer con : getAllSearchableContainers()) {
            sb.addSearchableContainer(con);
        }
        final JPanel topPane = new JPanel();
        sb.getComponent().setPreferredSize(new Dimension(250, 23));
        topPane.add(sb.getComponent());
        frame.add(topPane, BorderLayout.PAGE_START);
        frame.add(getWorkspacePanel(), BorderLayout.CENTER);
        frame.add(getButtonPanel(), BorderLayout.SOUTH);
        frame.setVisible(true);
    }
    */
    
    /**
     * SikuliCodePane conformance
     */
    
    @Override
    public void writePython(Writer writer) throws IOException {
    	final String source = compileToPython();
    	writer.write(source, 0, source.length());
    }
    
    @Override
    public void insertScreenshot(String path, javax.swing.text.Element src) {
		createOrEditScreenshotBlock(path);
    }
    
	public UndoableEdit getUndoManager() {
    	return _undoManager;
    }
	
	public boolean isDirty() {
    	return _dirty;
    }

	public void setDirty(boolean flag) {
        if(_dirty == flag)
            return;
         _dirty = flag; 
         if(flag && getRootPane() != null)
            getRootPane().putClientProperty("Window.documentModified", true);
         else
            SikuliIDE.getInstance().checkDirtyPanes();
    }
	
    public boolean close() throws IOException{
        if( isDirty() ){
      	  Utils.UnsavedChangesDialogResult ans =  Utils.showCloseWithUnsavedChangesDialog(this, getCurrentShortFilename());
           if( ans == Utils.UnsavedChangesDialogResult.CANCEL_AND_DONT_SAVE )
              return false;
           else if( ans == Utils.UnsavedChangesDialogResult.CLOSE_AND_SAVE )
              saveFile();
           setDirty(false);
        }
        return true;
     }
	
	public String getCurrentShortFilename() {
        if(_srcBundlePath != null){
            File f = new File(_srcBundlePath);
            return f.getName();
         }
         return "Untitled";
    }
    
	public File getCurrentFile() {
    	return this.selectedFile;
    }
	
	private void convertSrcToHtml(String bundle)  {
    	//FIXME: NOT IMPLEMENTED
    }
	
	public String saveFile() throws IOException {
        if(getCurrentFile()==null)
            return saveAsFile();
         else{
            writeSrcFile(true);
            return getCurrentShortFilename();
         }
    }
    
	public String saveAsFile() throws IOException {
        File file = new FileChooser(SikuliIDE.getInstance()).saveBlocks();
        if(file == null)  return null;

        String bundlePath = file.getAbsolutePath();
        if( !file.getAbsolutePath().endsWith(".sikuliblocks") )
           bundlePath += ".sikuliblocks";
        if(Utils.exists(bundlePath)){
           int res = JOptionPane.showConfirmDialog(
                 null, I18N._I("msgFileExists", bundlePath), 
                 I18N._I("dlgFileExists"), JOptionPane.YES_NO_OPTION);
           if(res != JOptionPane.YES_OPTION)
              return null;
        }
        saveAsBundle(bundlePath);

        return getCurrentShortFilename();
    }
    
    private void saveAsBundle(String bundlePath) throws IOException{
        bundlePath = Utils.slashify(bundlePath, true);
        if(_srcBundlePath != null)
           Utils.xcopy( _srcBundlePath, bundlePath );
        else
           Utils.mkdir(bundlePath);
        setSrcBundle(bundlePath);
        setSelectedFile(createSourceFile(bundlePath, ".blocks"));
        Debug.log(1, "save to bundle: " + getSrcBundle());
        writeSrcFile(true);
        //TODO: update all bundle references in ImageButtons
        //BUG: if save and rename images, the images will be gone..
     }
    
    private File createSourceFile(String bundlePath, String ext){
        if( Utils.sikuliFileTypeForPath(bundlePath) == Utils.SikuliFileType.SIKULI_FILE_TYPE_BLOCKS) { //.sikuli file )
           File dir = new File(bundlePath);
           String name = dir.getName();
           name = name.substring(0, name.lastIndexOf("."));
           return new File(bundlePath, name+ext);
        }
        return new File(bundlePath);
     }
	
	public String exportAsZip() throws IOException, FileNotFoundException
	{
		//FIXME: NOT IMPLEMENTED
		return "";
	}
    
    private File findSourceFile(String sikuli_dir){
        if( Utils.sikuliFileTypeForPath(sikuli_dir) == Utils.SikuliFileType.SIKULI_FILE_TYPE_BLOCKS) { //.sikuliblocks file
           File dir = new File(sikuli_dir);
           File[] blockFiles = dir.listFiles(new GeneralFileFilter("blocks", "Blocks Source"));
           if(blockFiles.length > 1){
              String sikuli_name = dir.getName();
              sikuli_name = sikuli_name.substring(0, sikuli_name.lastIndexOf('.'));
              for(File f : blockFiles){
                 String block_name = f.getName();
                 block_name = block_name.substring(0, block_name.lastIndexOf('.'));
                 if( block_name.equals(sikuli_name) )
                    return f;
              }
           }
           if(blockFiles.length >= 1)
              return blockFiles[0];
        }
        return new File(sikuli_dir);
     }
     
    public void loadFile(String filename) throws IOException{
        if( filename.endsWith("/") )
           filename = filename.substring(0, filename.length()-1);
        setSrcBundle(filename+"/");        
        setSelectedFile(findSourceFile(filename));
        Debug.error("Loading file: " + selectedFile.getPath() + " workspace:" + this.toString());
        loadFreshWorkspace();
        loadProjectFromPath(selectedFile.getPath());
     }
    
    private void cleanBundle(String bundle){
    	/*
        PythonInterpreter py = 
           ScriptRunner.getInstance(null).getPythonInterpreter();
        Debug.log(2, "Clear source bundle " + bundle);
        py.set("bundle_path", bundle);
        py.exec(pyBundleCleaner);
        */
     
   }
    
	public String getSrcBundle() {
        if( _srcBundlePath == null ){
            File tmp = Utils.createTempDir("sikuliblocks");
            setSrcBundle(Utils.slashify(tmp.getAbsolutePath(),true));
         }
         return _srcBundlePath;
    }
    
    void setSrcBundle(String newBundlePath) {
        _srcBundlePath = newBundlePath;
        _imgLocator = new ImageLocator(_srcBundlePath);
    }
    
	public File copyFileToBundle(String filename) {
        File f = new File(filename);
        String bundlePath = getSrcBundle();
        if(f.exists()){
           try{
              File newFile = Utils.smartCopy(filename, bundlePath);
              return newFile;
           }
           catch(IOException e){
              e.printStackTrace();
              return f;
           }
        }
        return null;
    }
    
    public File getFileInBundle(String filename) {
        if(_imgLocator == null)
            return null;
         try{
            String fullpath = _imgLocator.locate(filename);
            return new File(fullpath);
         }
         catch(IOException e){
            return null;
         }
    }
	
	public int search(String str, int pos, boolean forward) {
    	//FIXME: NOT IMPLEMENTED
    	return -1;
    }
    
    public BlocksPane getComponent() {
 	   return this;
    }
    
	@Override
	public boolean supportsRegions() {
		return false;
	}
	
	@Override
	public boolean supportsPythonConversion() {
		return true;
	}
	
	@Override
	public boolean supportsTextCommands() {
		return false;
	}

}