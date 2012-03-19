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
import java.util.Iterator;
import java.lang.Iterable;

import javax.swing.*;
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

import edu.mit.blocks.codeblocks.BlockConnectorShape;
import edu.mit.blocks.codeblocks.BlockGenus;
import edu.mit.blocks.codeblocks.Block;
import edu.mit.blocks.codeblocks.BlockLink;
import edu.mit.blocks.codeblocks.BlockConnector;
import edu.mit.blocks.codeblocks.BlockLinkChecker;
import edu.mit.blocks.codeblocks.CommandRule;
import edu.mit.blocks.codeblocks.Constants;
import edu.mit.blocks.codeblocks.SocketRule;
import edu.mit.blocks.workspace.BlockCanvas;
import edu.mit.blocks.workspace.SearchBar;
import edu.mit.blocks.workspace.SearchableContainer;
import edu.mit.blocks.workspace.Workspace;
import edu.mit.blocks.workspace.WorkspaceListener;
import edu.mit.blocks.workspace.WorkspaceEvent;
import edu.mit.blocks.workspace.Page;
import edu.mit.blocks.workspace.typeblocking.*;
import edu.mit.blocks.renderable.*;

import org.sikuli.script.Debug;
import org.sikuli.script.ScreenImage;
import org.sikuli.script.Observer;
import org.sikuli.script.Subject;
import org.sikuli.script.ScriptRunner;

/**
 * Example entry point to OpenBlock application creation.
 *
 * @author Ricarose Roque
 */
public class BlocksWorkspaceController implements Observer, WorkspaceListener {

    private Element langDefRoot;
    private boolean isWorkspacePanelInitialized = false;
    protected JPanel workspacePanel;
    protected final Workspace workspace;
    protected SearchBar searchBar;

    //flag to indicate if a new lang definition file has been set
    private boolean langDefDirty = true;

    //flag to indicate if a workspace has been loaded/initialized
    private boolean workspaceLoaded = false;
    // last directory that was selected with open or save action
    private File lastDirectory;
    // file currently loaded in workspace
    private File selectedFile;
    // Reference kept to be able to update frame title with current loaded file
    private JFrame frame;
    
    private Thread _runningThread;
    
    private boolean autoCaptureEnabled = false;

    /**
     * Constructs a WorkspaceController instance that manages the
     * interaction with the codeblocks.Workspace
     *
     */
    public BlocksWorkspaceController() {
        this.workspace = new Workspace();
        workspace.addWorkspaceListener(this);
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
        BlockGenus.loadBlockGenera(workspace, root);

        //load rules
        BlockLinkChecker.addRule(workspace, new CommandRule(workspace));
        BlockLinkChecker.addRule(workspace, new SocketRule());
        BlockLinkChecker.addRule(workspace, new AnyBlockSocketRule());

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
        BlockGenus.resetAllGenuses();
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
    public Node getSaveNode() {
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
    public Node getSaveNode(final boolean validate) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Element documentElement = document.createElementNS(Constants.XML_CODEBLOCKS_NS, "cb:CODEBLOCKS");
            // schema reference
            documentElement.setAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "xsi:schemaLocation", Constants.XML_CODEBLOCKS_NS+" "+Constants.XML_CODEBLOCKS_SCHEMA_URI);

            Node workspaceNode = workspace.getSaveNode(document);
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
        workspace.loadWorkspaceFrom(null, langDefRoot);
        workspaceLoaded = true;
        
    	Block runonceBlock = new Block(workspace, "runonce", "run once", true);
    	RenderableBlock runonceRenderableBlock = BlockUtilities.cloneBlock(runonceBlock);
        workspace.getBlockCanvas().getCanvas().add(runonceRenderableBlock, 0);
        runonceRenderableBlock.setLocation(50, 25);
        
    	Page p = workspace.getBlockCanvas().getPages().get(0); //FIXME: this won't work with multiple pages.
    	//add this block to that page.
    	p.blockDropped(runonceRenderableBlock);
    	
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
            workspace.reset();
            workspace.loadWorkspaceFrom(projectRoot, langDefRoot);
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
        workspace.loadWorkspaceFrom(elementToLoad, langDefRoot);
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
            workspace.loadWorkspaceFrom(projectRoot, langRoot);
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
        workspace.reset();
    }

    /**
     * This method creates and lays out the entire workspace panel with its
     * different components.  Workspace and language data not loaded in
     * this function.
     * Should be call only once at application startup.
     */
    private void initWorkspacePanel() {
        workspacePanel = new JPanel();
        workspacePanel.setLayout(new BorderLayout());
        workspacePanel.add(workspace, BorderLayout.CENTER);
        isWorkspacePanelInitialized = true;
    }

    /**
     * Returns the JComponent of the entire workspace.
     * @return the JComponent of the entire workspace.
     */
    public JComponent getWorkspacePanel() {
        if (!isWorkspacePanelInitialized) {
            initWorkspacePanel();
        }
        return workspacePanel;
    }
    
    public void workspaceEventOccurred(WorkspaceEvent event) {
    	if(event.getEventType() == WorkspaceEvent.BLOCK_ADDED && event.isUserEvent()) {
    		Long blockID = event.getSourceBlockID();
			assert !invalidBlockID(blockID);
			Block addedBlock = Block.getBlock(blockID);
			if(addedBlock.getNumSockets() > 0) {
				BlockConnector socket = addedBlock.getSocketAt(0);
			
				if(socket.getKind().equals("screenshot") && autoCaptureEnabled) {
					//if the user added a block with a screenshot connector, and auto capture is enabled, start taking a screenshot
					capture(0);
				}
			}
    	}
    	if(event.getEventType() == WorkspaceEvent.BLOCK_STACK_COMPILED) {
    		Long blockID = event.getSourceBlockID();
			assert !invalidBlockID(blockID);
			Block clickedBlock = Block.getBlock(blockID);
	    	Debug.error("EVENT: " + event.toString() + " BLOCK: " + clickedBlock.toString() + " GENUS:" + clickedBlock.getGenusName());
			if(clickedBlock.getGenusName().equals("screenshot-block")) {
				workspace.getFocusManager().setFocus(blockID);
				capture(0);
			}
    	}
    }

    /**
     * Action bound to "Open" action.
     */
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

    /**
     * Action bound to "Save" button.
     */
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

    /**
     * Action bound to "Save As..." button.
     */
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
    
    /**
     * Action bound to "Add Function..." button.
     */
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
    
    /**
     * Action bound to "Screenshot..." button.
     */
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
    
    /**
     * Action bound to "Run" button.
     */
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
    
    /**
     * Action bound to "Auto Capture" checkbox.
     */
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

    /**
     * Saves the content of the workspace to the given file
     * @param file Destination file
     * @throws IOException If save failed
     */
    private void saveToFile(File file) throws IOException {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            fileWriter.write(getSaveString());
        }
        finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    public void setSelectedFile(File selectedFile) {
        this.selectedFile = selectedFile;
        frame.setTitle("WorkspaceDemo - "+selectedFile.getPath());
    }
    
    private static boolean invalidBlockID(Long blockID) {
        return BlockUtilities.isNullBlockInstance(blockID);
    }
    
    public void capture(int delay) {
        SikuliIDE.getInstance().setVisible(false);
        frame.setVisible(false);
    	CaptureController captureController =  new CaptureController();
    	captureController.addObserver(this);
    	captureController.capture(delay);
    }
    public void update(Subject s) {
        if(s instanceof CaptureController){
            SikuliIDE.getInstance().setVisible(true);
            frame.setVisible(true);
        	CaptureController captureController = (CaptureController)s;
        	String screenshotPath = captureController.getScreenshotPath();
        	if(screenshotPath != null)
        		createOrEditScreenshotBlock(screenshotPath);
        }
    }
    
    private void changeScreenshotImages(Block screenshotBlock, String screenshotPath, Map<BlockImageIcon.ImageLocation, BlockImageIcon> blockImageMap)
    {
    	//modify the existing screenshot block
		RenderableBlock screenshotRenderableBlock = RenderableBlock.getRenderableBlock(screenshotBlock.getBlockID());
		screenshotBlock.setBlockImageMap(blockImageMap);
		screenshotBlock.setProperty("screenshot-path", screenshotPath);
		
		Long screenshotParentBlockID = screenshotBlock.getPlugBlockID();
		if(!invalidBlockID(screenshotParentBlockID)) {
			//if this screenshot block has a parent, redraw the parent, and in turn, the screenshot
			RenderableBlock screenshotParentRenderableBlock = RenderableBlock.getRenderableBlock(screenshotParentBlockID);
			
			/*
			BlockLink link = BlockLink.getBlockLink(workspace, screenshotParentRenderableBlock.getBlock(),
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
			screenshotRenderableBlock.repaintBlock();
			screenshotRenderableBlock.repaint();
			screenshotParentRenderableBlock.blockConnected(connectedSocket, screenshotBlock.getBlockID());
			workspace.getCurrentPage(screenshotRenderableBlock).getJComponent().repaint();
			workspace.getCurrentPage(screenshotRenderableBlock).getJComponent().revalidate();
			screenshotParentRenderableBlock.clearBufferedImage();
            screenshotParentRenderableBlock.moveConnectedBlocks();
            screenshotParentRenderableBlock.repaintBlock();
			screenshotParentRenderableBlock.repaint();
			workspace.getBlockCanvas().getJComponent().repaint();
			workspace.getBlockCanvas().getJComponent().revalidate();


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
    	
    	
    	FocusTraversalManager focusManager = workspace.getFocusManager();
    	BlockCanvas blockCanvas = workspace.getBlockCanvas();
    	Long parentBlockID = focusManager.getFocusBlockID();
    	if(!invalidBlockID(parentBlockID)) {
    		Block parentBlock = Block.getBlock(parentBlockID);
    		if(parentBlock.getGenusName().equals("screenshot-block")) {
    			changeScreenshotImages(parentBlock, screenshotPath, blockImageMap);
    			return;

    		}
    	}
    	
    	//create a new screenshot block
    	
    	Block screenshotBlock = new Block(workspace, "screenshot-block", "", true);
    	
    	screenshotBlock.setBlockImageMap(blockImageMap);
    	
    	RenderableBlock block = BlockUtilities.cloneBlock(screenshotBlock);
        Debug.error("Creating screenshot block: " + block.toString());
    	
    	block.getBlock().setProperty("screenshot-path", screenshotPath);

    	boolean didConnectBlock = false;

        if(!invalidBlockID(parentBlockID)) {
            RenderableBlock parentRenderableBlock = RenderableBlock.getRenderableBlock(parentBlockID);
            if(parentRenderableBlock != null && parentRenderableBlock.isVisible()) {
	        	Debug.error("Adding as child of block: " + parentRenderableBlock.toString());
	        	//find a socket for the new screenshot
	        	Iterable<BlockConnector> sockets = parentRenderableBlock.getBlock().getSockets();
	        	int i = 0;
	        	//first, try an empty socket
	        	for(BlockConnector aConnector : sockets) {
					if(!aConnector.hasBlock() && aConnector.getKind().equals("screenshot") && RenderableBlock.getRenderableBlock(aConnector.getBlockID()).isVisible()) {
						//empty screenshot socket! use that
						BlockLink link = BlockLink.getBlockLink(workspace, parentRenderableBlock.getBlock(),
								screenshotBlock,
								parentRenderableBlock.getBlock().getSocketAt(i), 
								screenshotBlock.getPlug());
						link.connect();
				        
				        workspace.notifyListeners(new WorkspaceEvent(
	                            workspace, 
	                            RenderableBlock.getRenderableBlock(link.getPlugBlockID()).getParentWidget(),
	                            link, WorkspaceEvent.BLOCKS_CONNECTED));
	                    RenderableBlock.getRenderableBlock(link.getSocketBlockID()).moveConnectedBlocks();
	                    RenderableBlock.getRenderableBlock(link.getSocketBlockID()).repaintBlock();
	                    RenderableBlock.getRenderableBlock(link.getSocketBlockID()).repaint();
	                    
	                    focusManager.setFocus(block.getBlockID());
	                    didConnectBlock = true;
				        
						break;
					}
					i ++;
				}
	        	
	        	//second, try to replace a block in an existing screenshot socket
	        	for(BlockConnector aConnector : sockets) {
					if(aConnector.hasBlock() && aConnector.getKind().equals("screenshot")) {
		    			changeScreenshotImages(Block.getBlock(aConnector.getBlockID()), screenshotPath, blockImageMap);
		    	        focusManager.setFocus(block.getBlockID());
		    	        didConnectBlock = true;
		    			break;
					}
	        	}
            }
        }
        if(!didConnectBlock) {
            blockCanvas.getCanvas().add(block, 0);
            block.setLocation(25, 25);
            
        	Page p = workspace.getBlockCanvas().getPages().get(0); //FIXME: this won't work with multiple pages.
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
                     frame.setVisible(false);
                     
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
                     frame.setVisible(true);
                     _runningThread = null;
                  }
               }
               catch(IOException e){ e.printStackTrace(); }
            }
         };
         _runningThread.start();
    }
    
    private String compileToPython() {
    	String source = "import math\nimport random\nsetThrowException(False)\n";
    	for (Block aBlock : workspace.getBlocks()) {
    		String isRootBlockAsString = aBlock.getProperty("is-root-block");
    		if(isRootBlockAsString != null && isRootBlockAsString.equals("yes")) {
    			source += BlockCompiler.compileBlock(aBlock);
    			source += "\n\n";
    		}
    	}
    	Debug.error("SOURCE: \n" + source);
    	return source;
    }

    /**
     * Return the lower button panel.
     */
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

    /**
     * Returns a SearchBar instance capable of searching for blocks
     * within the BlockCanvas and block drawers
     */
    public JComponent getSearchBar() {
        final SearchBar sb = new SearchBar(
                "Search blocks", "Search for blocks in the drawers and workspace", workspace);
        for (SearchableContainer con : getAllSearchableContainers()) {
            sb.addSearchableContainer(con);
        }
        return sb.getComponent();
    }

    /**
     * Returns an unmodifiable Iterable of SearchableContainers
     * @return an unmodifiable Iterable of SearchableContainers
     */
    public Iterable<SearchableContainer> getAllSearchableContainers() {
        return workspace.getAllSearchableContainers();
    }
    
    private void createAndShowAddFunctionDialog() {
    	JFrame dialog = new JFrame("Add Function");
    	dialog.setSize(500, 250);
    	
    	Container content = dialog.getContentPane();
    	content.setLayout(new GridBagLayout());
    	GridBagConstraints c = new GridBagConstraints();
    	
    	c.anchor = GridBagConstraints.FIRST_LINE_START;
    	c.ipadx = 12;

    	final JLabel nameLabel = new JLabel("Function Name:", SwingConstants.RIGHT);
    	nameLabel.setVerticalTextPosition(SwingConstants.CENTER);
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.gridx = 0;
    	c.gridy = 0;
    	c.weighty = 1;
    	dialog.add(nameLabel, c);
    	    	
    	final JTextField nameField = new JTextField();
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.gridx = 1;
    	c.gridy = 0;
    	c.gridwidth = 2;
    	//c.ipadx = 350;
    	dialog.add(nameField, c);
    	
    	//c.ipadx = 0;
    	c.gridwidth = 1;
    	
    	final JLabel parametersLabel = new JLabel("Parameters:", SwingConstants.LEFT);
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.gridx = 0;
    	c.gridy = 1;
    	dialog.add(parametersLabel, c);

    	String[] data = {"one", "two", "three", "four"};
    	final JList myList = new JList(data);
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.ipady = 50;
    	c.gridwidth = 4;
    	c.gridx = 0;
    	c.gridy = 2;
    	dialog.add(myList, c);
    	
    	c.ipady = 0;
    	c.gridwidth = 1;
    	
    	final JButton addButton = new JButton("Add");
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.gridx = 0;
    	c.gridy = 3;
    	dialog.add(addButton, c);
    	
    	final JButton removeButton = new JButton("Remove");
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.gridx = 1;
    	c.gridy = 3;
    	dialog.add(removeButton, c);

    	final Component spacer = Box.createHorizontalStrut(48);
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.ipadx = 48;
    	c.gridx = 2;
    	c.gridy = 3;
    	dialog.add(spacer, c);
    	
    	c.ipadx = 12;
    	
    	final JButton cancelButton = new JButton("Cancel");
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.gridx = 3;
    	c.gridy = 4;
    	dialog.add(cancelButton, c);
    	
    	final JButton okButton = new JButton("Add Function");
    	dialog.getRootPane().setDefaultButton(okButton);
    	c.fill = GridBagConstraints.HORIZONTAL;
    	c.gridx = 4;
    	c.gridy = 4;
    	dialog.add(okButton, c);
    	
    	
    	dialog.setVisible(true);
    }

    /**
     * Create the GUI and show it.  For thread safety, this method should be
     * invoked from the event-dispatching thread.
     */
    public void createAndShowGUI() {
        frame = new JFrame("Sikuli Blocks");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(100, 100, 1000, 1000);
        final SearchBar sb = new SearchBar("Search blocks",
                "Search for blocks in the drawers and workspace", workspace);
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

}