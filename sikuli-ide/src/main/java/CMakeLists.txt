# Copyright 2010-2011, Sikuli.org
# Released under the MIT License.
IF(APPLE)
   SET(OS_EXTRA_SOURCE_FILES org/sikuli/ide/NativeLayerForMac.java)
ELSEIF(LINUX)
   SET(OS_EXTRA_SOURCE_FILES org/sikuli/ide/NativeLayerForLinux.java)
ELSEIF(WIN32)
   SET(OS_EXTRA_SOURCE_FILES org/sikuli/ide/NativeLayerForWindows.java)
ENDIF()

SET(CMAKE_OSX_ARCHITECTURES x86_64)

SET(JAVA_SOURCE_FILES 
         ${OS_EXTRA_SOURCE_FILES}
         org/sikuli/ide/AutoUpdater.java
         org/sikuli/ide/ButtonGenCommand.java
         org/sikuli/ide/BlockCompiler.java
         org/sikuli/ide/BlocksPane.java
         org/sikuli/ide/BlocksUndoManager.java
         org/sikuli/ide/AnyBlockSocketRule.java
         org/sikuli/ide/CaptureButton.java
         org/sikuli/ide/CaptureController.java
         org/sikuli/ide/CloseableTabbedPane.java
         org/sikuli/ide/CloseableTabbedPaneListener.java
         org/sikuli/ide/CommandArgs.java
         org/sikuli/ide/ConsolePane.java
         org/sikuli/ide/CurrentLineHighlighter.java
         org/sikuli/ide/EditPane.java
         org/sikuli/ide/FileChooser.java
         org/sikuli/ide/I18N.java
         org/sikuli/ide/IDESettings.java
         org/sikuli/ide/ImageButton.java
         org/sikuli/ide/LineNumberView.java
         org/sikuli/ide/MyDocument.java
         org/sikuli/ide/NativeLayer.java
         org/sikuli/ide/Observer.java
         org/sikuli/ide/PatternWindow.java
         org/sikuli/ide/PreferencesWin.java
         org/sikuli/ide/RegionButton.java
         org/sikuli/ide/ScreenshotPane.java
         org/sikuli/ide/sikuli_test/CounterPanel.java
         org/sikuli/ide/sikuli_test/DefaultFailureDetailView.java
         org/sikuli/ide/sikuli_test/FailureRunView.java
         org/sikuli/ide/sikuli_test/ProgressBar.java
         org/sikuli/ide/sikuli_test/TestHierarchyRunView.java
         org/sikuli/ide/sikuli_test/TestRunContext.java
         org/sikuli/ide/sikuli_test/TestRunView.java
         org/sikuli/ide/sikuli_test/TestSuitePanel.java
         org/sikuli/ide/sikuli_test/TestTreeModel.java
         org/sikuli/ide/sikuli_test/TextUnitTestRunner.java
         org/sikuli/ide/sikuli_test/UnitTestRunner.java
         org/sikuli/ide/SikuliDocument.java
         org/sikuli/ide/SikuliEditorKit.java
         org/sikuli/ide/SikuliIDE.java
         org/sikuli/ide/SikuliCodePane.java
         org/sikuli/ide/SikuliTextPane.java
         org/sikuli/ide/SikuliView.java
         org/sikuli/ide/SikuliViewFactory.java
         org/sikuli/ide/SimilaritySlider.java
         org/sikuli/ide/StatusBar.java
         org/sikuli/ide/Subject.java
         org/sikuli/ide/TargetOffsetPane.java
         org/sikuli/ide/ToolbarButton.java
         org/sikuli/ide/UserPreferences.java
         org/sikuli/ide/Utils.java
         org/sikuli/ide/NamingPane.java
         org/sikuli/ide/ModernTabbedPaneUI.java
         org/sikuli/ide/UndoManager.java
         org/sikuli/ide/BevelDividerPainter.java
         org/sikuli/ide/extmanager/ExtensionItem.java
         org/sikuli/ide/extmanager/ExtensionManagerFrame.java
         org/sikuli/ide/indentation/PythonIndentation.java
         org/sikuli/ide/indentation/PythonState.java
         org/sikuli/ide/util/GifDecoder.java
         org/sikuli/ide/util/LoadingSpinner.java
)

add_custom_target( ${PROJECT_NAME}.classes
    COMMAND ${JAVA_COMPILE} -d ${BINARY_CLASS_DIR} -classpath "${CLASSPATH}"
            ${JAVA_SOURCE_FILES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})
     

add_custom_target( ${JAR_FILE}.classes-in-jar
    COMMAND ${CMAKE_COMMAND} -E copy_directory ${BINARY_CLASS_DIR} ${JAR_DIR}
)

add_dependencies(${JAR_FILE}.classes-in-jar ${PROJECT_NAME}.classes)
