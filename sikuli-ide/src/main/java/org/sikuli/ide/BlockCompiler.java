/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide;

import java.lang.*;
import java.util.*;

import edu.mit.blocks.codeblocks.BlockConnector;
import edu.mit.blocks.codeblocks.BlockConnectorShape;
import edu.mit.blocks.codeblocks.BlockGenus;
import edu.mit.blocks.codeblocks.Block;
import edu.mit.blocks.codeblocks.BlockLinkChecker;
import edu.mit.blocks.codeblocks.CommandRule;
import edu.mit.blocks.codeblocks.Constants;
import edu.mit.blocks.codeblocks.SocketRule;
import edu.mit.blocks.workspace.BlockCanvas;
import edu.mit.blocks.workspace.SearchBar;
import edu.mit.blocks.workspace.SearchableContainer;
import edu.mit.blocks.workspace.Workspace;
import edu.mit.blocks.workspace.typeblocking.*;
import edu.mit.blocks.renderable.*;

import org.sikuli.script.Debug;

public class BlockCompiler {
	
	private interface CompileAdapter {
		public String compileBlock(Block block);
	}
	
	private static final Map<String, CompileAdapter> _compilerAdapters;
	static {
		Map<String, CompileAdapter> map = new HashMap<String, CompileAdapter>();
		
		map.put("runOnce",			new CompileAdapter() { public String compileBlock(Block block) { return compileRunOnce(block); } });
		map.put("wait", 			new CompileAdapter() { public String compileBlock(Block block) { return compileWait(block); } });
		map.put("click", 			new CompileAdapter() { public String compileBlock(Block block) { return compileClick(block); } });
		map.put("doubleClick", 		new CompileAdapter() { public String compileBlock(Block block) { return compileDoubleClick(block); } });
		map.put("rightClick", 		new CompileAdapter() { public String compileBlock(Block block) { return compileRightClick(block); } });
		map.put("hover",	 		new CompileAdapter() { public String compileBlock(Block block) { return compileHover(block); } });
		map.put("dragDrop",	 		new CompileAdapter() { public String compileBlock(Block block) { return compileDragDrop(block); } });
		map.put("type",		 		new CompileAdapter() { public String compileBlock(Block block) { return compileType(block); } });
		map.put("typeIn",	 		new CompileAdapter() { public String compileBlock(Block block) { return compileTypeIn(block); } });
		map.put("typeModifiers",	new CompileAdapter() { public String compileBlock(Block block) { return compileTypeModifiers(block); } });
		map.put("paste",	 		new CompileAdapter() { public String compileBlock(Block block) { return compileTypeIn(block); } });
		map.put("pasteIn",	 		new CompileAdapter() { public String compileBlock(Block block) { return compileTypeIn(block); } });
		map.put("screenshot", 		new CompileAdapter() { public String compileBlock(Block block) { return compileScreenshot(block); } });
		map.put("string",	 		new CompileAdapter() { public String compileBlock(Block block) { return compileStringBlock(block); } });
		map.put("number",	 		new CompileAdapter() { public String compileBlock(Block block) { return compileNumber(block); } });
		map.put("if",				new CompileAdapter() { public String compileBlock(Block block) { return compileIf(block); } });
		map.put("ifelse",			new CompileAdapter() { public String compileBlock(Block block) { return compileIfElse(block); } });
		map.put("exists",			new CompileAdapter() { public String compileBlock(Block block) { return compileExists(block); } });
		map.put("repeat",			new CompileAdapter() { public String compileBlock(Block block) { return compileRepeat(block); } });
		map.put("while",			new CompileAdapter() { public String compileBlock(Block block) { return compileWhile(block); } });
		map.put("and",				new CompileAdapter() { public String compileBlock(Block block) { return compileAnd(block); } });
		map.put("or",				new CompileAdapter() { public String compileBlock(Block block) { return compileOr(block); } });
		map.put("not",				new CompileAdapter() { public String compileBlock(Block block) { return compileNot(block); } });
		map.put("true",				new CompileAdapter() { public String compileBlock(Block block) { return compileTrue(block); } });
		map.put("false",			new CompileAdapter() { public String compileBlock(Block block) { return compileFalse(block); } });
		map.put("string-equals",	new CompileAdapter() { public String compileBlock(Block block) { return compileEquals(block); } });
		map.put("number-equals",	new CompileAdapter() { public String compileBlock(Block block) { return compileEquals(block); } });
		map.put("string-not-equals",new CompileAdapter() { public String compileBlock(Block block) { return compileNotEquals(block); } });
		map.put("number-not-equals",new CompileAdapter() { public String compileBlock(Block block) { return compileNotEquals(block); } });
		map.put("number-to-string",	new CompileAdapter() { public String compileBlock(Block block) { return compileToString(block); } });
		map.put("appendString",		new CompileAdapter() { public String compileBlock(Block block) { return compileAppendString(block); } });
		map.put("pi",				new CompileAdapter() { public String compileBlock(Block block) { return compilePi(block); } });
		map.put("e",				new CompileAdapter() { public String compileBlock(Block block) { return compileE(block); } });
		map.put("sum",				new CompileAdapter() { public String compileBlock(Block block) { return compileAdd(block); } });
		map.put("difference",		new CompileAdapter() { public String compileBlock(Block block) { return compileSubtract(block); } });
		map.put("product",			new CompileAdapter() { public String compileBlock(Block block) { return compileMultiply(block); } });
		map.put("quotient",			new CompileAdapter() { public String compileBlock(Block block) { return compileDivide(block); } });
		map.put("less-than",		new CompileAdapter() { public String compileBlock(Block block) { return compileLessThan(block); } });
		map.put("greater-than",		new CompileAdapter() { public String compileBlock(Block block) { return compileGreaterThan(block); } });
		map.put("less-than-or-equal-to",new CompileAdapter() { public String compileBlock(Block block) { return compileLessThanOrEqualTo(block); } });
		map.put("greater-than-or-equal-to", new CompileAdapter() { public String compileBlock(Block block) { return compileGreaterThanOrEqualTo(block); } });
		map.put("atan",				new CompileAdapter() { public String compileBlock(Block block) { return compileArcTan(block); } });
		map.put("random",			new CompileAdapter() { public String compileBlock(Block block) { return compileRandom(block); } });
		map.put("round",			new CompileAdapter() { public String compileBlock(Block block) { return compileRound(block); } });
		map.put("int",				new CompileAdapter() { public String compileBlock(Block block) { return compileInt(block); } });
		map.put("min",				new CompileAdapter() { public String compileBlock(Block block) { return compileMin(block); } });
		map.put("max",				new CompileAdapter() { public String compileBlock(Block block) { return compileMax(block); } });
		map.put("remainder",		new CompileAdapter() { public String compileBlock(Block block) { return compileRemainder(block); } });
		map.put("power",			new CompileAdapter() { public String compileBlock(Block block) { return compilePower(block); } });
		map.put("abs",				new CompileAdapter() { public String compileBlock(Block block) { return compileAbs(block); } });
		map.put("sqrt",				new CompileAdapter() { public String compileBlock(Block block) { return compileSqrt(block); } });
		map.put("sin",				new CompileAdapter() { public String compileBlock(Block block) { return compileSin(block); } });
		map.put("cos",				new CompileAdapter() { public String compileBlock(Block block) { return compileCos(block); } });
		map.put("tan",				new CompileAdapter() { public String compileBlock(Block block) { return compileTan(block); } });
		map.put("asin",				new CompileAdapter() { public String compileBlock(Block block) { return compileArcSin(block); } });
		map.put("acos",				new CompileAdapter() { public String compileBlock(Block block) { return compileArcCos(block); } });
		map.put("log",				new CompileAdapter() { public String compileBlock(Block block) { return compileLog(block); } });
		map.put("ln",				new CompileAdapter() { public String compileBlock(Block block) { return compileLn(block); } });
		map.put("on-appear-procedure",new CompileAdapter() { public String compileBlock(Block block) { return compileOnAppear(block); } });
		map.put("on-vanish-procedure",new CompileAdapter() { public String compileBlock(Block block) { return compileOnVanish(block); } });
		map.put("on-change-procedure",new CompileAdapter() { public String compileBlock(Block block) { return compileOnChange(block); } });
		map.put("observe",			new CompileAdapter() { public String compileBlock(Block block) { return compileObserve(block); } });
		map.put("print",			new CompileAdapter() { public String compileBlock(Block block) { return compilePrint(block); } });
		map.put("assert",			new CompileAdapter() { public String compileBlock(Block block) { return compileAssert(block); } });
		map.put("openApp",			new CompileAdapter() { public String compileBlock(Block block) { return compileOpenApp(block); } });
		map.put("switchApp",		new CompileAdapter() { public String compileBlock(Block block) { return compileSwitchApp(block); } });
		map.put("closeApp",			new CompileAdapter() { public String compileBlock(Block block) { return compileCloseApp(block); } });
		map.put("runCommand",		new CompileAdapter() { public String compileBlock(Block block) { return compileRunCommand(block); } });
		map.put("popup",			new CompileAdapter() { public String compileBlock(Block block) { return compilePopup(block); } });
		map.put("input",			new CompileAdapter() { public String compileBlock(Block block) { return compileInput(block); } });
		map.put("input",			new CompileAdapter() { public String compileBlock(Block block) { return compileInput(block); } });
		map.put("controlKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("KeyModifier.CTRL"); } });
		map.put("shiftKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("KeyModifier.SHIFT"); } });
		map.put("altKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("KeyModifier.ALT"); } });
		map.put("metaKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("KeyModifier.META"); } });
		map.put("commandKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("KeyModifier.CMD"); } });
		map.put("windowsKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("KeyModifier.WIN"); } });
		map.put("enterKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.ENTER"); } });
		map.put("tabKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.TAB"); } });
		map.put("escapeKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.ESCAPE"); } });
		map.put("backspaceKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.BACKSPACE"); } });
		map.put("deleteKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.DELETE"); } });
		map.put("insertKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.INSERT"); } });
		map.put("spaceKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.SPACE"); } });
		map.put("functionKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileFunctionKey(block); } });
		map.put("homeKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.HOME"); } });
		map.put("endKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.END"); } });
		map.put("leftKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.LEFT"); } });
		map.put("rightKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.RIGHT"); } });
		map.put("downKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.DOWN"); } });
		map.put("upKey",			new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.UP"); } });
		map.put("pageUpKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.PAGE_UP"); } });
		map.put("pageDownKey",		new CompileAdapter() { public String compileBlock(Block block) { return compileConstant("Key.PAGE_DOWN"); } });
		map.put("break",			new CompileAdapter() { public String compileBlock(Block block) { return compileBreak(block); } });
		map.put("continue",			new CompileAdapter() { public String compileBlock(Block block) { return compileContinue(block); } });
		map.put("variable",			new CompileAdapter() { public String compileBlock(Block block) { return compileVariable(block); } });
		map.put("getScreenshot",	new CompileAdapter() { public String compileBlock(Block block) { return compileGetter(block); } });
		map.put("setScreenshot",	new CompileAdapter() { public String compileBlock(Block block) { return compileSetter(block); } });
		map.put("getString",		new CompileAdapter() { public String compileBlock(Block block) { return compileGetter(block); } });
		map.put("setString",		new CompileAdapter() { public String compileBlock(Block block) { return compileSetter(block); } });
		map.put("getNumber",		new CompileAdapter() { public String compileBlock(Block block) { return compileGetter(block); } });
		map.put("setNumber",		new CompileAdapter() { public String compileBlock(Block block) { return compileSetter(block); } });
		map.put("getBoolean",		new CompileAdapter() { public String compileBlock(Block block) { return compileGetter(block); } });
		map.put("setBoolean",		new CompileAdapter() { public String compileBlock(Block block) { return compileSetter(block); } });
		map.put("getVariable",		new CompileAdapter() { public String compileBlock(Block block) { return compileGetter(block); } });
		map.put("setVariable",		new CompileAdapter() { public String compileBlock(Block block) { return compileSetter(block); } });
		_compilerAdapters = Collections.unmodifiableMap(map);
	}
	
	public static String compileBlock(Block block) {
		if(block == null)
			return "";
		
		String type = block.getProperty("command-type");
		CompileAdapter adapter = _compilerAdapters.get(type);
		if(adapter == null) {
			Debug.error("Unsupported block type: " + type);
			return "";
		}
		return adapter.compileBlock(block);
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
	
    private static String compileFunction(String name, BlockConnector... sockets)
    {
    	return compileFunction(name, "\"\"", sockets);
    }
    
    private static String compileFunction(String name, String missing, BlockConnector... sockets)
    {
    	String result = "";
    	result += name;
    	result += "(";
    	for(BlockConnector socket : sockets) {
    		if(!socket.hasBlock()) {
    			result += missing;
    		}
    		else {
    			Long blockID = socket.getBlockID();
    			assert !invalidBlockID(blockID);
    			result += compileBlock(Block.getBlock(blockID));
    		}
    		result += ",";
    	}
    	result = result.substring(0, result.length() - 1); //remove last comma
    	result += ")";
    	return result;
    }
    
    //accepts one or two operands.
    //If two operands, returns operand[0] operator operand[1]
    //If one operand, returns operator operand[0]
    private static String compileBooleanOperator(String operator, BlockConnector... operandSockets)
    {
    	return compileOperator(operator, "True", operandSockets);
    }
    private static String compileOperator(String operator, String missing, BlockConnector... operandSockets)
    {
    	String result = "";
    	
    	//compile all the operands
    	List<String> compiledOperands = new ArrayList<String>();
    	for(BlockConnector socket : operandSockets) {
    		if(!socket.hasBlock()) {
    			compiledOperands.add(missing);
    		}
    		else {
    			Long blockID = socket.getBlockID();
    			assert !invalidBlockID(blockID);
    			compiledOperands.add(compileBlock(Block.getBlock(blockID)));
    		}
    	}
    	
    	//position the compiled operands
    	if(compiledOperands.size() == 1)
    		result = operator + " " + compiledOperands.get(0);
    	else if(compiledOperands.size() == 2)
    		result = compiledOperands.get(0) + " " + operator + " " + compiledOperands.get(1);
    	else
    		result = missing;
    				
    	return result;
    }
    
    private static String compileChildren(String header, BlockConnector socket, boolean indent)
    {
    	String result = header + "\n";
		
		if(!socket.hasBlock()) {
			if(indent) result += "\tpass";
			return result;
		}
		Block aBlock = null;
		
		Long nextBlockID = socket.getBlockID();
		assert !invalidBlockID(nextBlockID);
		aBlock = Block.getBlock(nextBlockID);
		
		while(aBlock != null) {
			String childResult = compileBlock(aBlock);
			if(indent) {
				String[] childResultLines = childResult.split(System.getProperty("line.separator"));
				childResult = "";
				for(String line : childResultLines)
					childResult += "\t" + line + "\n";
			}
			result += childResult;
			result += "\n";
			
			nextBlockID = aBlock.getAfterBlockID();
			if(invalidBlockID(nextBlockID))
				break;
			aBlock = Block.getBlock(nextBlockID);
		}
		
		return result;
    	
    }
    
    private static String compileSikuliEventHandler(String handlerType, Block block)
    {
		String result = "";
		//OpenBlocks will ensure that the label is unique, so use that (without whitespace) as the procedure name
		String procedureName = block.getBlockLabel().replaceAll("\\s","");
		BlockConnector commandSocket = block.getSocketAt(1);

		//compile the procedure
		result += compileChildren("def " + procedureName + "(event):", commandSocket, true);
		
		BlockConnector testSocket = block.getSocketAt(0);
		String test = "";
		if(testSocket.hasBlock()) {
			Long blockID = testSocket.getBlockID();
			assert !invalidBlockID(blockID);
			test = compileBlock(Block.getBlock(blockID));
		}
		
		//setup the handler
		result += handlerType + "(" + test + ", " + procedureName + ")";
		
		return result;
    }
    
    private static String compileConstant(String constant)
    {
    	return constant;
    }
    
	private static String compileRunOnce(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileChildren("", socket, false);
	}

	private static String compileWait(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("wait", socket);
	}
	
	private static String compileClick(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("click", socket);
	}
	
	private static String compileDoubleClick(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("doubleClick", socket);
	}
	
	private static String compileRightClick(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("rightClick", socket);
	}
	
	private static String compileHover(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("hover", socket);
	}
	
	private static String compileDragDrop(Block block) {
		BlockConnector socketFrom = block.getSocketAt(0);
		BlockConnector socketTo = block.getSocketAt(0);
		return compileFunction("dragDrop", socketFrom, socketTo);
	}
	
	private static String compileType(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("type", socket);
	}
	
	private static String compileTypeIn(Block block) {
		BlockConnector screenshotSocket = block.getSocketAt(0);
		BlockConnector stringSocket = block.getSocketAt(1);
		return compileFunction("type", screenshotSocket, stringSocket);
	}
	
	private static String compileTypeModifiers(Block block) {
		BlockConnector textSocket = block.getSocketAt(0);
		BlockConnector modifiersSocket = block.getSocketAt(1);
		return compileFunction("type", textSocket, modifiersSocket);
	}
	
	private static String compilePaste(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("paste", socket);
	}
	
	private static String compilePasteIn(Block block) {
		BlockConnector screenshotSocket = block.getSocketAt(0);
		BlockConnector stringSocket = block.getSocketAt(1);
		return compileFunction("paste", screenshotSocket, stringSocket);
	}
	
	
	private static String compileScreenshot(Block block) {
		String screenshotPath = block.getProperty("screenshot-path");
		if(screenshotPath == null)
			return "\"\"";
		return "\"" + screenshotPath + "\"";
	}
	
	private static String compileStringBlock(Block block) {
		String string = block.getBlockLabel();
		if(string == null)
			return "\"\"";
		return "\"" + string + "\"";
	}
	
	private static String compileNumber(Block block) {
		String string = block.getBlockLabel();
		if(string == null)
			return "None";
		return string;
	}
	
	private static String compileIf(Block block) {
		BlockConnector testSocket = block.getSocketAt(0);
		String test = "True";
		if(testSocket.hasBlock()) {
			Long blockID = testSocket.getBlockID();
			assert !invalidBlockID(blockID);
			test = compileBlock(Block.getBlock(blockID));
		}
		String testString = "if " + test + ":";
		BlockConnector thenSocket = block.getSocketAt(1);
		return compileChildren(testString, thenSocket, true);
	}
	
	private static String compileIfElse(Block block) {
		String result = "";
		BlockConnector testSocket = block.getSocketAt(0);
		String test = "True";
		if(testSocket.hasBlock()) {
			Long blockID = testSocket.getBlockID();
			assert !invalidBlockID(blockID);
			test = compileBlock(Block.getBlock(blockID));
		}
		
		String testString = "if " + test + ":";
		BlockConnector thenSocket = block.getSocketAt(1);
		result += compileChildren(testString, thenSocket, true);
		
		BlockConnector elseSocket = block.getSocketAt(2);
		result += compileChildren("else:", elseSocket, true);
		
		return result;
	}
	
	private static String compileExists(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("exists", socket);
	}
	
	private static String compileRepeat(Block block) {
		BlockConnector timesSocket = block.getSocketAt(0);
		String times = "1";
		if(timesSocket.hasBlock()) {
			Long blockID = timesSocket.getBlockID();
			assert !invalidBlockID(blockID);
			times = compileBlock(Block.getBlock(blockID));
		}
		String timesString = "for i in range(" + times + "):";
		BlockConnector doSocket = block.getSocketAt(1);
		return compileChildren(timesString, doSocket, true);
	}
	
	private static String compileWhile(Block block) {
		BlockConnector testSocket = block.getSocketAt(0);
		String test = "True";
		if(testSocket.hasBlock()) {
			Long blockID = testSocket.getBlockID();
			assert !invalidBlockID(blockID);
			test = compileBlock(Block.getBlock(blockID));
		}
		String testString = "while " + test + ":";
		BlockConnector thenSocket = block.getSocketAt(1);
		return compileChildren(testString, thenSocket, true);
	}
	
	private static String compileAnd(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator("and", firstOperand, secondOperand);
	}
	
	private static String compileOr(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator("or", firstOperand, secondOperand);
	}
	
	private static String compileNot(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileBooleanOperator("not", operand);
	}
	
	private static String compileEquals(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator("==", firstOperand, secondOperand);
	}
	
	private static String compileNotEquals(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator("!=", firstOperand, secondOperand);
	}
	
	private static String compileTrue(Block block) {
		return "True";
	}
	
	private static String compileFalse(Block block) {
		return "False";
	}
	
	private static String compileToString(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("str", socket);
	}
	
	private static String compilePi(Block block) {
		return "math.pi";
	}
	
	private static String compileE(Block block) {
		return "math.e";
	}

	private static String compileAppendString(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileOperator("+", "", firstOperand, secondOperand);
	}
	
	private static String compileAdd(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileOperator("+", "0", firstOperand, secondOperand);
	}
	
	private static String compileSubtract(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileOperator("-", "0", firstOperand, secondOperand);
	}
	
	private static String compileMultiply(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileOperator("*", "1", firstOperand, secondOperand);
	}
	
	private static String compileDivide(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileOperator("/", "1", firstOperand, secondOperand);
	}
	
	private static String compileLessThan(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator("<", firstOperand, secondOperand);
	}
	
	private static String compileGreaterThan(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator(">", firstOperand, secondOperand);
	}
	
	private static String compileLessThanOrEqualTo(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator(">=", firstOperand, secondOperand);
	}
	
	private static String compileGreaterThanOrEqualTo(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileBooleanOperator(">=", firstOperand, secondOperand);
	}
	
	private static String compileArcTan(Block block) {
		BlockConnector x = block.getSocketAt(0);
		BlockConnector y = block.getSocketAt(1);
		return compileFunction("atan2", "0", y, x);
	}
	
	private static String compileRandom(Block block) {
		BlockConnector lowerBound = block.getSocketAt(0);
		BlockConnector upperBound = block.getSocketAt(1);
		return compileFunction("random.randint", "0", lowerBound, upperBound);
	}
	
	private static String compileRound(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("round", "0", socket);
	}
	
	private static String compileInt(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("int", "0", socket);
	}
	
	private static String compileMin(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileFunction("min", "0", firstOperand, secondOperand);
	}
	
	private static String compileMax(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileFunction("max", "0", firstOperand, secondOperand);
	}
	
	private static String compileRemainder(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileOperator("%", "1", firstOperand, secondOperand);
	}
	
	private static String compilePower(Block block) {
		BlockConnector firstOperand = block.getSocketAt(0);
		BlockConnector secondOperand = block.getSocketAt(1);
		return compileFunction("math.pow", "1", firstOperand, secondOperand);
	}
	
	private static String compileAbs(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("abs", "0", operand);
	}
	
	private static String compileSqrt(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.sqrt", "0", operand);
	}
	
	private static String compileSin(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.sin", "0", operand);
	}
	
	private static String compileCos(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.cos", "0", operand);
	}
	
	private static String compileTan(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.tan", "0", operand);
	}
	
	private static String compileArcSin(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.asin", "0", operand);
	}
	
	private static String compileArcCos(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.acos", "0", operand);
	}
	
	private static String compileLog(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.log10", "1", operand);
	}
	
	private static String compileLn(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("math.log", "1", operand);
	}
	
	private static String compileOnAppear(Block block) {
		return compileSikuliEventHandler("onAppear", block);
	}
	
	private static String compileOnVanish(Block block) {
		return compileSikuliEventHandler("onVanish", block);
	}
	
	private static String compileOnChange(Block block) {
		return compileSikuliEventHandler("onChange", block);
	}
	
	private static String compileObserve(Block block) {
		BlockConnector stringSocket = block.getSocketAt(0);
		BlockConnector backgroundSocket = block.getSocketAt(1);
		return compileFunction("observe", stringSocket, backgroundSocket);
	}
	
	private static String compilePrint(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("print", operand);
	}
	
	private static String compileAssert(Block block) {
		BlockConnector operand = block.getSocketAt(0);
		return compileFunction("assert", operand);
	}
	
	private static String compileOpenApp(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("openApp", socket);
	}
	
	private static String compileSwitchApp(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("switchApp", socket);
	}
	
	private static String compileCloseApp(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("closeApp", socket);
	}
	
	private static String compileRunCommand(Block block) {
		BlockConnector socket = block.getSocketAt(0);
		return compileFunction("run", socket);
	}
	
	private static String compilePopup(Block block) {
		BlockConnector textSocket = block.getSocketAt(0);
		BlockConnector titleSocket = block.getSocketAt(1);
		return compileFunction("popup", textSocket, titleSocket);
	}
	
	private static String compileInput(Block block) {
		BlockConnector textSocket = block.getSocketAt(0);
		BlockConnector defaultSocket = block.getSocketAt(1);
		return compileFunction("input", textSocket, defaultSocket);
	}
	
	private static String compileFunctionKey(Block block) {
		BlockConnector numberSocket = block.getSocketAt(0);
		Long blockID = numberSocket.getBlockID();
		assert !invalidBlockID(blockID);
		String number = compileBlock(Block.getBlock(blockID));
		if(number.equals("None"))
			number = "1";
		return "Key.F" + number;
	}
	
	private static String compileBreak(Block block) {
		return "break";
	}

	private static String compileContinue(Block block) {
		return "continue";
	}
	
	private static String compileVariable(Block block) {
		String variable = block.getBlockLabel();
		if(variable == null)
			return "None";
		return variable;
	}
	
	private static String compileGetter(Block block) {
		BlockConnector variableSocket = block.getSocketAt(0);
		return compileOperator("", "None", variableSocket);
	}
	
	private static String compileSetter(Block block) {
		BlockConnector variableSocket = block.getSocketAt(0);
		BlockConnector valueSocket = block.getSocketAt(1);
		return compileOperator("=", "None", variableSocket, valueSocket);
	}
}
