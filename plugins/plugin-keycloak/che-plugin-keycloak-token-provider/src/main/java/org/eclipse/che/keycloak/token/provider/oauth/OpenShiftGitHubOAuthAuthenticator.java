/*******************************************************************************
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.keycloak.token.provider.oauth;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.util.store.MemoryDataStoreFactory;

import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.security.oauth.GitHubOAuthAuthenticator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;

@Singleton
public class OpenShiftGitHubOAuthAuthenticator extends GitHubOAuthAuthenticator {

    @Inject
    public OpenShiftGitHubOAuthAuthenticator(@Nullable @Named("che.oauth.github.redirecturis") String[] redirectUris,
                                             @Nullable @Named("che.oauth.github.authuri") String authUri,
                                             @Nullable @Named("che.oauth.github.tokenuri") String tokenUri) throws IOException {

        super("NULL", "NULL", redirectUris, authUri, tokenUri);

        if (!isNullOrEmpty(authUri) &&
            !isNullOrEmpty(tokenUri) &&
            redirectUris != null && redirectUris.length != 0) {

            configure("NULL", "NULL", redirectUris, authUri, tokenUri, new MemoryDataStoreFactory());
        }
    }

    public void setToken(String userId, OAuthToken token) throws IOException {
        flow.createAndStoreCredential(
                new TokenResponse().setAccessToken(token.getToken()).setScope(token.getScope()),
                userId);
    }

}