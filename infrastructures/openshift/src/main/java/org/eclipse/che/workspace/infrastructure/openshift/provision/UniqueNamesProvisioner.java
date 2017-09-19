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
package org.eclipse.che.workspace.infrastructure.openshift.provision;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.api.model.Route;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalEnvironment;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenShiftEnvironment;

/**
 * Changes names of OpenShift pods by adding the workspace identifier to the prefix also generates
 * OpenShift routes names with prefix 'route' see {@link NameGenerator#generate(String, int)}.
 *
 * @author Anton Korneta
 */
@Singleton
public class UniqueNamesProvisioner implements ConfigurationProvisioner {

  public static final String CHE_ORIGINAL_NAME_LABEL = "CHE_ORIGINAL_NAME_LABEL";
  public static final String ROUTE_PREFIX = "route";
  public static final int ROUTE_SUFFIX_SIZE = 8;
  public static final char SEPARATOR = '.';

  @Override
  public void provision(
      InternalEnvironment environment, OpenShiftEnvironment osEnv, RuntimeIdentity identity)
      throws InfrastructureException {
    final String workspaceId = identity.getWorkspaceId();
    final Map<String, Pod> pods = new HashMap<>(osEnv.getPods());
    osEnv.getPods().clear();
    for (Pod pod : pods.values()) {
      final ObjectMeta podMeta = pod.getMetadata();
      podMeta.getLabels().put(CHE_ORIGINAL_NAME_LABEL, podMeta.getName());
      final String podName = workspaceId + SEPARATOR + podMeta.getName();
      podMeta.setName(podName);
      osEnv.getPods().put(podName, pod);
    }
    final Map<String, Route> routes = new HashMap<>(osEnv.getRoutes());
    osEnv.getRoutes().clear();
    for (Route route : routes.values()) {
      final ObjectMeta routeMeta = route.getMetadata();
      routeMeta.getLabels().put(CHE_ORIGINAL_NAME_LABEL, routeMeta.getName());
      final String routeName = NameGenerator.generate(ROUTE_PREFIX, ROUTE_SUFFIX_SIZE);
      routeMeta.setName(routeName);
      osEnv.getRoutes().put(routeName, route);
    }
  }
}
