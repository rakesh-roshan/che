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
package org.eclipse.che.selenium.core.user;

import static java.lang.String.format;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PreDestroy;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.selenium.core.client.TestAuthServiceClient;
import org.eclipse.che.selenium.core.client.TestUserServiceClient;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.provider.TestApiEndpointUrlProvider;
import org.eclipse.che.selenium.core.requestfactory.TestUserHttpJsonRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Anatolii Bazko */
public class TestUserImpl implements TestUser {
  private static final Logger LOG = LoggerFactory.getLogger(TestUserImpl.class);

  private final String email;
  private final String password;
  private final String name;
  private final String id;
  private final String authToken;

  private final TestUserServiceClient userServiceClient;
  private final TestWorkspaceServiceClient workspaceServiceClient;

  @Inject
  public TestUserImpl(
      TestUserServiceClient userServiceClient,
      TestAuthServiceClient authServiceClient,
      TestApiEndpointUrlProvider apiEndpointUrlProvider,
      TestUserNamespaceResolver testUserNamespaceResolver)
      throws Exception {
    this(
        NameGenerator.generate("user", 6) + "@some.mail",
        userServiceClient,
        authServiceClient,
        apiEndpointUrlProvider,
        testUserNamespaceResolver);
  }

  /** To instantiate user with specific e-mail. */
  public TestUserImpl(
      String email,
      TestUserServiceClient userServiceClient,
      TestAuthServiceClient authServiceClient,
      TestApiEndpointUrlProvider apiEndpointUrlProvider,
      TestUserNamespaceResolver userNamespaceResolver)
      throws Exception {
    this.userServiceClient = userServiceClient;

    this.email = email;
    this.password = NameGenerator.generate("Pwd1", 6);
    this.name = email.split("@")[0];

    this.id = userServiceClient.create(email, password).getId();

    LOG.info("User name='{}', password '{}', id='{}' has been created", name, password, id);

    this.authToken = authServiceClient.login(getName(), getPassword());
    this.workspaceServiceClient =
        new TestWorkspaceServiceClient(
            apiEndpointUrlProvider,
            new TestUserHttpJsonRequestFactory(authToken),
            userNamespaceResolver);
  }

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getAuthToken() {
    return authToken;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  @PreDestroy
  public void delete() {
    List<String> workspaces = new ArrayList<>();
    try {
      workspaces = workspaceServiceClient.getAll();
    } catch (Exception e) {
      LOG.error("Failed to get all workspaces.", e);
    }

    for (String workspace : workspaces) {
      try {
        workspaceServiceClient.delete(workspace, name);
      } catch (Exception e) {
        LOG.error(
            format("User name='%s' failed to remove workspace name='%s'", workspace, name), e);
      }
    }

    try {
      userServiceClient.deleteByEmail(email);
      LOG.info("User name='{}', id='{}' removed", name, id);
    } catch (Exception e) {
      LOG.error(format("Failed to remove user name='%s', id='%s'", email, id), e);
    }
  }

  @Override
  public String toString() {
    return format(
        "%s{name=%s, email=%s, password=%s}",
        this.getClass().getSimpleName(), this.getName(), this.getEmail(), getPassword());
  }
}
