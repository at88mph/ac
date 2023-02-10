/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2023.                            (c) 2023.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
************************************************************************
*/

package org.opencadc.auth;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.auth.AuthorizationToken;
import ca.nrc.cadc.auth.AuthorizationTokenPrincipal;
import ca.nrc.cadc.auth.HttpPrincipal;
import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.auth.NotAuthenticatedException;
import ca.nrc.cadc.auth.NumericPrincipal;
import ca.nrc.cadc.net.HttpGet;
import ca.nrc.cadc.reg.Standards;
import ca.nrc.cadc.reg.client.LocalAuthority;
import ca.nrc.cadc.util.InvalidConfigException;
import ca.nrc.cadc.util.StringUtil;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.security.auth.Subject;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * Prototype IdentityManager for a standards-based system.
 * 
 * @author pdowler
 */
public class StandardIdentityManager implements IdentityManager {
    private static final Logger log = Logger.getLogger(StandardIdentityManager.class);

    private final URI oidcIssuer;
    
    // TODO: need these to contruct an AuthorizationToken
    private List<String> oidcDomains = new ArrayList<>();
    private URI oidcScope;
    
    public StandardIdentityManager() {
        LocalAuthority loc = new LocalAuthority();
        String key = Standards.SECURITY_METHOD_OPENID.toASCIIString();
        this.oidcIssuer = loc.getServiceURI(key);
        try {
            URL u = oidcIssuer.toURL();
            oidcDomains.add(u.getHost());
        } catch (MalformedURLException ex) {
            throw new InvalidConfigException("found " + key + " = " + oidcIssuer + " - expected valid URL", ex);
        }
    }

    @Override
    public Subject validate(Subject subject) throws NotAuthenticatedException {
        validateOidcAccessToken(subject);
        return subject;
    }

    @Override
    public Subject augment(Subject subject) {
        // TODO: if X500Principal && servops && UMS we could augment CADC-style
        // oidc tokens: validate gets HttpPrincipal and NumericPrincial
        // cadc signed cookies/tokens: validate gets all identities
        return subject;
    }

    @Override
    public Subject toSubject(Object owner) {
        Subject ret = new Subject();
        if (owner != null) {
            UUID uuid = null;
            if (owner instanceof UUID) {
                uuid = (UUID) owner;
            } else if (owner instanceof String) {
                String sub = (String) owner;
                uuid = UUID.fromString(sub);
            } else {
                throw new RuntimeException("unexpected owner type: " + owner.getClass().getName() + " value: " + owner);
            }
            NumericPrincipal p = new NumericPrincipal(uuid);
            
            // effectively augment by using the current subject as a "cache" of known identities
            Subject s = AuthenticationUtil.getCurrentSubject();
            if (s != null) {
                for (Principal cp : s.getPrincipals()) {
                    if (AuthenticationUtil.equals(p, cp)) {
                        log.debug("[cache hit] caller Subject matches " + p + ": " + s);
                        ret.getPrincipals().addAll(s.getPrincipals());
                        return ret;
                    }
                }
            }
            
            ret.getPrincipals().add(p);
            // TODO: this is sufficient for some purposes, but not for output using toDisplayString
            // a real augment would require system credentials to implement
        }
        return ret;
    }

    @Override
    public Object toOwner(Subject subject) {
        // use NumericPrincipal aka OIDC sub for persistence
        Set<NumericPrincipal> ps = subject.getPrincipals(NumericPrincipal.class);
        if (ps.isEmpty()) {
            return null;
        }
        return ps.iterator().next().getUUID().toString();
    }

    @Override
    public String toDisplayString(Subject subject) {
        // use HttpPrincipal aka OIDC preferred_username for string output, eg logging
        Set<HttpPrincipal> ps = subject.getPrincipals(HttpPrincipal.class);
        if (ps.isEmpty()) {
            return null;
        }
        return ps.iterator().next().getName(); // kind of ugh
    }
    
    private void validateOidcAccessToken(Subject s) {
        log.debug("validateOidcAccessToken - START");
        Set<AuthorizationTokenPrincipal> rawTokens = s.getPrincipals(AuthorizationTokenPrincipal.class);
        
        log.debug("token issuer: "  + oidcIssuer + " rawTokens: " + rawTokens.size());
        if (oidcIssuer != null && !rawTokens.isEmpty()) {
            URL u = getUserEndpoint();
            for (AuthorizationTokenPrincipal raw : rawTokens) {
                String credentials = null;
                String challengeType = null;

                // parse header
                log.debug("header key: " + raw.getHeaderKey());
                log.debug("header val: " + raw.getHeaderValue());
                if (AuthenticationUtil.AUTHORIZATION_HEADER.equalsIgnoreCase(raw.getHeaderKey())) {
                    String[] tval = raw.getHeaderValue().split(" ");
                    if (tval.length == 2) {
                        challengeType = tval[0];
                        credentials = tval[1];
                    } else {
                        throw new NotAuthenticatedException(challengeType, NotAuthenticatedException.AuthError.INVALID_REQUEST,
                            "invalid authorization");
                    }
                } // else: some other challenge 
                log.debug("challenge type: " + challengeType);
                log.debug("credentials: " + credentials);
                    
                // validate
                if (challengeType != null && credentials != null) {
                    try {
                        HttpGet get = new HttpGet(u, true);
                        get.setRequestProperty("authorization", raw.getHeaderValue());
                        get.prepare();

                        InputStream istream = get.getInputStream();
                        String str = StringUtil.readFromInputStream(istream, "UTF-8");
                        JSONObject json = new JSONObject(str);
                        String sub = json.getString("sub");
                        String username = json.getString("preferred_username");
                        // TODO: register an X509 DN with IAM and see if I can get it back here

                        NumericPrincipal np = new NumericPrincipal(UUID.fromString(sub));
                        HttpPrincipal hp = new HttpPrincipal(username);

                        s.getPrincipals().remove(raw);
                        s.getPrincipals().add(np);
                        s.getPrincipals().add(hp);

                        AuthorizationToken authToken = new AuthorizationToken(challengeType, credentials, oidcDomains, oidcScope);
                        s.getPublicCredentials().add(authToken);
                    } catch (Exception ex) {
                        throw new NotAuthenticatedException(challengeType, NotAuthenticatedException.AuthError.INVALID_TOKEN, ex.getMessage(), ex);
                    }
                }
            }
        }
        log.debug("validateOidcAccessToken - DONE");
    }
    
    private URL getUserEndpoint() {
        try {
            // TODO: call OpenID well known config to find userinfo endpoint
            StringBuilder sb = new StringBuilder(oidcIssuer.toASCIIString());
            if (sb.charAt(sb.length() - 1) != '/') {
                sb.append("/");
            }
            sb.append("userinfo");
            URL userinfo = new URL(sb.toString());
            log.debug("oidc.userinfo: " + userinfo);
            return userinfo;
        } catch (MalformedURLException ex) {
            throw new RuntimeException("BUG: failed to create valid oidc userinfo url", ex);
        }
    }
        
}
