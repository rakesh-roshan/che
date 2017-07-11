/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.workspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.notification.SubscriptionManagerClient;
import org.eclipse.che.ide.api.workspace.model.WorkspaceImpl;
import org.eclipse.che.ide.bootstrap.BasicIDEInitializedEvent;
import org.eclipse.che.ide.context.AppContextImpl;
import org.eclipse.che.ide.resource.Path;

import java.util.Map;

import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.RUNNING;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STARTING;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STOPPED;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STOPPING;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_AUTO_START;

/** Performs the routines required to start/stop the current workspace. */
@Singleton
public class CurrentWorkspaceManager {

    private final WorkspaceServiceClient    workspaceServiceClient;
    private final AppContext                appContext;
    private final SubscriptionManagerClient subscriptionManagerClient;

    @Inject
    CurrentWorkspaceManager(WorkspaceServiceClient workspaceServiceClient,
                            AppContext appContext,
                            SubscriptionManagerClient subscriptionManagerClient,
                            EventBus eventBus) {
        this.workspaceServiceClient = workspaceServiceClient;
        this.appContext = appContext;
        this.subscriptionManagerClient = subscriptionManagerClient;

        eventBus.addHandler(BasicIDEInitializedEvent.TYPE, e -> handleWorkspaceState());

        // TODO (spi ide): get from CHE_PROJECTS_ROOT environment variable
        ((AppContextImpl)appContext).setProjectsRoot(Path.valueOf("/projects"));
    }

    /** Does an appropriate action depending on the current workspace status. */
    private void handleWorkspaceState() {
        final WorkspaceImpl workspace = appContext.getWorkspace();
        final WorkspaceStatus workspaceStatus = workspace.getStatus();

        if (workspaceStatus == STARTING) {
            subscribeToEvents();
        } else if (workspaceStatus == RUNNING) {
            subscribeToEvents();
        } else if (workspaceStatus == STOPPING || workspaceStatus == STOPPED) {
            workspaceServiceClient.getSettings().then(settings -> {
                if (parseBoolean(settings.getOrDefault(CHE_WORKSPACE_AUTO_START, "true"))) {
                    startWorkspace(false);
                }
            });
        }
    }

    /** Start the current workspace with a default environment. */
    Promise<Void> startWorkspace(boolean restoreFromSnapshot) {
        subscribeToEvents();

        final WorkspaceImpl workspace = appContext.getWorkspace();
        final String defEnvName = workspace.getConfig().getDefaultEnv();

        return workspaceServiceClient.startById(workspace.getId(), defEnvName, restoreFromSnapshot)
                                     .then((Function<WorkspaceImpl, Void>)arg -> null);
    }

    /** Stop the current workspace. */
    void stopWorkspace() {
        workspaceServiceClient.stop(appContext.getWorkspaceId());
    }

    private void subscribeToEvents() {
        Map<String, String> scope = singletonMap("workspaceId", appContext.getWorkspaceId());

        // TODO (spi ide): consider shared constants for the endpoints
        subscriptionManagerClient.subscribe("workspace/statuses", "workspace/statusChanged", scope);
        subscriptionManagerClient.subscribe("workspace/statuses", "machine/statusChanged", scope);
        subscriptionManagerClient.subscribe("workspace/statuses", "server/statusChanged", scope);
        subscriptionManagerClient.subscribe("workspace/output", "machine/log", scope);
        subscriptionManagerClient.subscribe("workspace/output", "installer/log", scope);
    }
}