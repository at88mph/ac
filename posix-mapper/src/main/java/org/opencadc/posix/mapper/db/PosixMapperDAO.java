package org.opencadc.posix.mapper.db;

import org.opencadc.gms.GroupURI;
import org.opencadc.posix.mapper.Group;
import org.opencadc.posix.mapper.User;
import org.opencadc.posix.mapper.web.PosixInitAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple DAO for accessing the Users and Groups tables.  This class is designed to be used by the PosixMapperCache to
 * load user and group information into memory.
 */
public class PosixMapperDAO {
    // Dummy scheme and authority for default groups.
    private static final String JNDI_NAME = "java:comp/env/" + PosixInitAction.JNDI_DATASOURCE;
    private static final String INSERT_SQL_TEMPLATE = "INSERT INTO %s (%s) VALUES (?) ON CONFLICT (%s) DO NOTHING RETURNING %s";
    private static final String INSERT_USER_SQL = String.format(PosixMapperDAO.INSERT_SQL_TEMPLATE, "Users", "username", "username", "uid");
    private static final String INSERT_GROUP_SQL = String.format(PosixMapperDAO.INSERT_SQL_TEMPLATE, "Groups", "groupURI", "groupURI", "gid");

    // The JdbcTemplate is thread-safe and can be shared across multiple threads, so we can safely use a single instance for the DAO.
    private final JdbcTemplate jdbcTemplate;

    /**
     * Used for testing, allows injection of a mock or in-memory JdbcTemplate.
     * @param jdbcTemplate  The Spring JDBCTemplate object.
     */
    PosixMapperDAO(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Base Constructor.  Initializes the JdbcTemplate using a DataSource obtained via JNDI lookup.  If the DataSource
     * cannot be found, a RuntimeException is thrown.
     */
    public PosixMapperDAO() {
        final JndiDataSourceLookup jndiDataSourceLookup = new JndiDataSourceLookup();
        try {
            final DataSource dataSource = jndiDataSourceLookup.getDataSource(JNDI_NAME);
            this.jdbcTemplate = new JdbcTemplate(dataSource);
        } catch (DataSourceLookupFailureException dataSourceLookupFailureException) {
            throw new RuntimeException("Failed to initialize PosixMapperDAO with JNDI name: " + JNDI_NAME,
                    dataSourceLookupFailureException);
        }
    }

    /**
     * When the user is not found in the cache, this method is called to retrieve the user information from the database.  If the user is not found in the database, an EmptyResultDataAccessException is thrown.
     * @param issuer    The issuer of the user, typically the authentication service that authenticated the user.
     * @param subject   The subject of the user, typically a unique identifier for the user provided by the authentication service.
     * @return  User object containing the user information retrieved from the database.  Possibly null if the user is not found in the database.
     */
    public User getUser(final String issuer, final String subject) {
        Objects.requireNonNull(issuer, "issuer cannot be null");
        Objects.requireNonNull(subject, "subject cannot be null");
        return this.jdbcTemplate.queryForObject("select issuer, subject, username, uid from Users where issuer = ? and subject = ?",
                (rs, rowNum) -> {
                    final User user = new User(rs.getString("issuer"), rs.getString("subject"), rs.getString("username"));
                    user.setUID(rs.getInt("uid"));

                    return user;
                }, issuer, subject);
    }

    public User createUserMapping(final User user) {
        Objects.requireNonNull(user, "user cannot be null");
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(con -> {
            final PreparedStatement preparedStatement =
                    con.prepareStatement(PosixMapperDAO.INSERT_USER_SQL, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, user.getUsername());
            return preparedStatement;
        }, keyHolder);

        final Number key = keyHolder.getKey();
        if (key == null) {
            throw new RuntimeException("Failed to retrieve generated key for user: " + user.getUsername());
        }
        user.setUID(key.intValue());
        return user;
    }

    public Group createGroupMapping(final Group group) {
        Objects.requireNonNull(group, "group cannot be null");
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(con -> {
            final PreparedStatement preparedStatement =
                    con.prepareStatement(PosixMapperDAO.INSERT_GROUP_SQL, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, group.getGroupURI().getURI().toString());
            return preparedStatement;
        }, keyHolder);

        final Number key = keyHolder.getKey();
        if (key == null) {
            throw new RuntimeException("Failed to retrieve generated key for group: " + group.getGroupURI().getURI());
        }
        group.setGid(key.intValue());
        return group;
    }

    public void cacheUsers(final ConcurrentHashMap<String, User> userCache) {
        Objects.requireNonNull(userCache, "userCache cannot be null");
        userCache.clear();
        this.jdbcTemplate.queryForStream("select issuer, subject, username, uid from Users", (rs, rowNum) -> {
            final User user = new User(rs.getString("issuer"), rs.getString("subject"), rs.getString("username"));
            user.setUID(rs.getInt("uid"));

            return user;
        }).forEach(user -> userCache.put(user.getUsername(), user));
    }

    public void cacheGroups(final ConcurrentHashMap<URI, Group> groupCache) {
        Objects.requireNonNull(groupCache, "groupCache cannot be null");
        groupCache.clear();
        this.jdbcTemplate.queryForStream("select gid, groupURI from Groups", (rs, rowNum) -> {
            final Group group = new Group(new GroupURI(URI.create(rs.getString("groupURI"))));
            group.setGid(rs.getInt("gid"));

            return group;
        }).forEach(group -> groupCache.put(group.getGroupURI().getURI(), group));
    }
}
