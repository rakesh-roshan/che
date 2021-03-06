/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.selenium.debugger;

import com.google.inject.Inject;
import java.nio.file.Paths;
import org.eclipse.che.selenium.core.client.TestCommandServiceClient;
import org.eclipse.che.selenium.core.client.TestProjectServiceClient;
import org.eclipse.che.selenium.core.constant.TestBuildConstants;
import org.eclipse.che.selenium.core.constant.TestCommandsConstants;
import org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants;
import org.eclipse.che.selenium.core.constant.TestProjectExplorerContextMenuConstants;
import org.eclipse.che.selenium.core.project.ProjectTemplates;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.Loader;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.NotificationsPopupPanel;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.eclipse.che.selenium.pageobject.debug.DebugPanel;
import org.eclipse.che.selenium.pageobject.debug.JavaDebugConfig;
import org.eclipse.che.selenium.pageobject.intelligent.CommandsPalette;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** @author Dmytro Nochevnov */
public class DebugExternalClassTest {
  private static final String PROJECT = "java-with-external-libs";
  private static final String PATH_TO_CLASS =
      PROJECT + "/src/main/java/org/eclipse/che/examples/SimpleLogger.java";

  private static final String BUILD_AND_DEBUG_COMMAND_NAME = "build-and-debug";

  @Inject private TestWorkspace ws;
  @Inject private Ide ide;

  @Inject private ProjectExplorer projectExplorer;
  @Inject private Loader loader;
  @Inject private DebugPanel debugPanel;
  @Inject private JavaDebugConfig debugConfig;
  @Inject private NotificationsPopupPanel notifications;
  @Inject private Menu menu;
  @Inject private CodenvyEditor editor;
  @Inject private Consoles consoles;
  @Inject private TestCommandServiceClient testCommandServiceClient;
  @Inject private TestProjectServiceClient testProjectServiceClient;
  @Inject private CommandsPalette commandsPalette;

  @BeforeClass
  public void setup() throws Exception {
    testProjectServiceClient.importProject(
        ws.getId(),
        Paths.get(
            getClass()
                .getResource("/projects/plugins/DebuggerPlugin/java-with-external-libs")
                .toURI()),
        PROJECT,
        ProjectTemplates.CONSOLE_JAVA_SIMPLE);

    testCommandServiceClient.createCommand(
        "mvn -f ${current.project.path} clean install && java -jar "
            + "-Xdebug -Xnoagent -Djava.compiler=NONE "
            + "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 "
            + "${current.project.path}/target/java-with-external-libs-1.0-SNAPSHOT-jar-with-dependencies.jar",
        BUILD_AND_DEBUG_COMMAND_NAME,
        TestCommandsConstants.CUSTOM,
        ws.getId());

    // open IDE
    ide.open(ws);
    loader.waitOnClosed();
    projectExplorer.waitItem(PROJECT);
    notifications.waitProgressPopupPanelClose();

    // add java debug configuration
    menu.runCommand(
        TestMenuCommandsConstants.Run.RUN_MENU,
        TestMenuCommandsConstants.Run.EDIT_DEBUG_CONFIGURATION);
    debugConfig.createConfig(PROJECT);

    projectExplorer.quickRevealToItemWithJavaScript(PATH_TO_CLASS);

    // perform command "Maven > Reimport" to avoid "Type with fully qualified name: ch.qos.logback.classic.Logger was not found" error
    projectExplorer.openContextMenuByPathSelectedItem(PROJECT);
    projectExplorer.clickOnItemInContextMenu(TestProjectExplorerContextMenuConstants.MAVEN);
    projectExplorer.clickOnNewContextMenuItem(
        ProjectExplorer.PROJECT_EXPLORER_CONTEXT_MENU_MAVEN.REIMPORT);
  }

  @BeforeMethod
  public void startDebug() {
    projectExplorer.openItemByPath(PATH_TO_CLASS);

    // start application in debug mode
    commandsPalette.openCommandPalette();
    commandsPalette.startCommandByDoubleClick(BUILD_AND_DEBUG_COMMAND_NAME);
    consoles.waitExpectedTextIntoConsole(TestBuildConstants.LISTENING_AT_ADDRESS_8000);

    editor.waitActiveEditor();
  }

  @AfterMethod
  public void stopDebug() {
    debugPanel.removeAllBreakpoints();
    menu.runCommand(
        TestMenuCommandsConstants.Run.RUN_MENU, TestMenuCommandsConstants.Run.END_DEBUG_SESSION);
    editor.closeAllTabs();
  }

  @Test
  public void shouldDebugJreClass() {
    // when
    editor.setInactiveBreakpoint(19);
    menu.runCommandByXpath(
        TestMenuCommandsConstants.Run.RUN_MENU,
        TestMenuCommandsConstants.Run.DEBUG,
        debugConfig.getXpathToІRunDebugCommand(PROJECT));

    notifications.waitExpectedMessageOnProgressPanelAndClosed("Remote debugger connected");
    editor.waitAcitveBreakpoint(19);
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_INTO);

    // then
    editor.waitActiveTabFileName("Logger"); // there should be class "Logger" opened
    debugPanel.waitDebugHighlightedText(
        "    "); // we can't rely on concrete code of external library which can be changed in future
    debugPanel.waitTextInVariablesPanel(
        "=\"Info from java logger\""); // there should be at least parameter with value "Info from java logger"

    // when
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.RESUME_BTN_ID);

    // then
    editor.waitActiveTabFileName("SimpleLogger");
  }

  @Test(priority = 1)
  public void shouldDebugMavenArtifactClassWithSources() {
    // when
    editor.setInactiveBreakpoint(23);
    menu.runCommandByXpath(
        TestMenuCommandsConstants.Run.RUN_MENU,
        TestMenuCommandsConstants.Run.DEBUG,
        debugConfig.getXpathToІRunDebugCommand(PROJECT));

    notifications.waitExpectedMessageOnProgressPanelAndClosed("Remote debugger connected");
    editor.waitAcitveBreakpoint(23);
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_INTO);

    // then
    editor.waitActiveTabFileName(
        "Logger"); // there should be class "Logger" opened in decompiled view with "Download sources" link at the top
    editor.clickOnDownloadSourcesLink();
    editor.waitActiveTabFileName("Logger"); // there should be class "Logger" opened
    debugPanel.waitDebugHighlightedText(
        "filterAndLog_1(FQCN, null, Level.INFO, format, arg, null);");
    debugPanel.waitTextInVariablesPanel(
        "=\"Info from {}\""); // there should be at least parameter with value "Info from {}"

    // when
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_OVER);

    // then
    editor.waitActiveTabFileName("Logger"); // there should be class "Logger" opened
    debugPanel.waitDebugHighlightedText("  }");

    // when
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_OVER);

    // then
    editor.waitActiveTabFileName("SimpleLogger");
    debugPanel.waitDebugHighlightedText(
        "        org.apache.log4j.Logger log4jLogger = org.apache.log4j.Logger.getLogger(SimpleLogger.class);");
  }

  @Test(priority = 2)
  public void shouldHandleDebugOfMavenArtifactWithoutSources() {
    // when
    editor.setInactiveBreakpoint(27);
    menu.runCommandByXpath(
        TestMenuCommandsConstants.Run.RUN_MENU,
        TestMenuCommandsConstants.Run.DEBUG,
        debugConfig.getXpathToІRunDebugCommand(PROJECT));

    notifications.waitExpectedMessageOnProgressPanelAndClosed("Remote debugger connected");
    editor.waitAcitveBreakpoint(27);
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_INTO);

    // then
    editor.waitActiveTabFileName(
        "Category"); // there should be class "Category" opened in decompiled view with "Download sources" link at the top
    editor
        .clickOnDownloadSourcesLink(); // there should be "Download sources" link displayed in at the top of editor. Download they.
    notifications.waitExpectedMessageOnProgressPanelAndClosed(
        "Download sources for 'org.apache.log4j.Category' failed"); // there should an error of downloading the sources
    editor.waitActiveTabFileName("Category"); // there should be class "Category" opened

    // when
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_OUT);

    // then
    editor.waitActiveTabFileName("SimpleLogger");
    debugPanel.waitDebugHighlightedText("    }");
  }
}
