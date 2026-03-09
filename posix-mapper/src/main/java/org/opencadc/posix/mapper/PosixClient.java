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
 *
 ************************************************************************
 */

package org.opencadc.posix.mapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.log4j.Logger;
import org.opencadc.gms.GroupURI;
import org.opencadc.posix.mapper.db.PosixMapperDAO;
import org.opencadc.posix.mapper.web.group.GroupWriter;
import org.opencadc.posix.mapper.web.user.UserWriter;
import org.springframework.transaction.annotation.Transactional;


public class PosixClient {
    private static final Logger LOGGER = Logger.getLogger(PosixClient.class.getName());
    private static final String USER_CACHE_KEY_TEMPLATE = "%s|%s";
    private final LoadingCache<String, User> userLoadingCache;
    private final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<URI, Group> groupCache = new ConcurrentHashMap<>();
    private final PosixMapperDAO posixMapperDAO = new PosixMapperDAO();

    public PosixClient() {
        userLoadingCache = Caffeine.newBuilder().maximumSize(10_000)
                .recordStats()
                .build(key -> {
                    final String[] parts = key.split("\\|", 2);
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Invalid cache key format: " + key);
                    }
                    final String issuer = parts[0];
                    final String subject = parts[1];
                    return posixMapperDAO.getUser(issuer, subject);
                });
    }

    void cache() {
        final long startTime = System.currentTimeMillis();
        final CompletableFuture<Void> userCacheFuture =
                CompletableFuture.runAsync(() -> posixMapperDAO.allUsers().forEach(u -> userCache.put(PosixClient.toKey(u), u)));
        final CompletableFuture<Void> groupCacheFuture =
                CompletableFuture.runAsync(() -> posixMapperDAO.allGroups().forEach(g -> groupCache.put(g.getGroupURI().getURI(), g)));
        CompletableFuture.allOf(userCacheFuture, groupCacheFuture).join();
        final long endTime = System.currentTimeMillis();
        LOGGER.info(String.format("Cache initialized in %d ms with %d users and %d groups", endTime - startTime, userCache.size(), groupCache.size()));
    }

    static String toKey(final User user) {
        return String.format(PosixClient.USER_CACHE_KEY_TEMPLATE, user.getIssuer(), user.getSubject());
    }

    public User getUser(final User user) {
        return this.userCache.get(PosixClient.toKey(user));
    }

    @Transactional
    public void ensureUser(User user) {
        final User cachedUser = getUser(user);
        if (cachedUser == null) {
            final User existingUser = this.posixMapperDAO.getUser(user.getIssuer(), user.getSubject());
            if (existingUser != null) {
                this.userCache.putIfAbsent(PosixClient.toKey(existingUser),
                        existingUser);
            } else {
                final User newUser = saveUser(user);
                final Group newGroup = new Group(new GroupURI(URI.create("ivo://cadc.nrc.ca/groups?default")));
                newGroup.setGid(newUser.getUID());
                saveGroup(newGroup);
            }
        }
    }

    public User saveUser(User user) {
        final User newUser = this.posixMapperDAO.createUserMapping(user);
        this.userCache.putIfAbsent(PosixClient.toKey(newUser), newUser);

        return newUser;
    }

    public Group getOrCreateGroup(GroupURI groupURI) {
        return this.groupCache.getOrDefault(groupURI.getURI(), saveGroup(new Group(groupURI)));
    }

    private Group saveGroup(Group group) {
        final Group newGroup = this.posixMapperDAO.createGroupMapping(group);
        this.groupCache.putIfAbsent(group.getGroupURI().getURI(), newGroup);
        return newGroup;
    }

    public void writeUsers(UserWriter writer, Integer[] uidConstraints) {
        final boolean writeAllUsers = uidConstraints.length == 0;

        try {
            if (writeAllUsers) {
                writer.write(this.userCache.values().iterator());
            } else {
                final List<Integer> uidList = Arrays.asList(uidConstraints);
                final List<User> filteredUsers = this.userCache.values().stream()
                        .filter(user -> uidList.contains(user.getUID()))
                        .collect(Collectors.toList());
                writer.write(filteredUsers.iterator());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void writeGroups(GroupWriter writer, GroupURI[] groupURIConstraints, Integer[] gidConstraints) {
        final boolean writeAllGroups = groupURIConstraints.length == 0 && gidConstraints.length == 0;

        try {
            if (writeAllGroups) {
                writer.write(this.groupCache.values().iterator());
            } else if (groupURIConstraints.length > 0) {
                for (final GroupURI groupURIConstraint : groupURIConstraints) {
                    final Group group = getOrCreateGroup(groupURIConstraint);
                    if (group != null) {
                        writer.write(group);
                    }
                }
            } else {
                // GID constraints only
                for (final Group group : this.groupCache.values()) {
                    if (Arrays.asList(gidConstraints).contains(group.getGID())) {
                        writer.write(group);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
