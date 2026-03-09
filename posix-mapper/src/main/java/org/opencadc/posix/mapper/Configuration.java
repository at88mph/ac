package org.opencadc.posix.mapper;

import java.net.URI;

public class Configuration {
    private static final String START_UID_ENV_KEY = "POSIX_MAPPER_UID_START";
    private static final String START_GID_ENV_KEY = "POSIX_MAPPER_GID_START";
    private static final String RESOURCE_ID_ENV_KEY = "POSIX_MAPPER_RESOURCE_ID";

    private final int startUID;
    private final int startGID;
    private final URI resourceID;
    private final DatabaseConfiguration databaseConfiguration;


    private Configuration(final int startUID, final int startGID, final URI resourceID,
                          final DatabaseConfiguration databaseConfiguration) {
        this.startUID = startUID;
        this.startGID = startGID;
        this.resourceID = resourceID;
        this.databaseConfiguration = databaseConfiguration;
    }

    /**
     * Reads configuration from environment variables and constructs a Configuration object.
     * @return  Configuration object populated with values from environment variables.  Never null.
     */
    public static Configuration fromEnv() {
        final DatabaseConfiguration databaseConfiguration = DatabaseConfiguration.fromEnv();
        final String startUID = System.getenv(Configuration.START_UID_ENV_KEY);
        final String startGID = System.getenv(Configuration.START_GID_ENV_KEY);
        final String resourceID = System.getenv(Configuration.RESOURCE_ID_ENV_KEY);

        if (startUID == null || startGID == null || resourceID == null) {
            throw new IllegalStateException("Missing required configuration environment variables: " +
                    Configuration.START_UID_ENV_KEY + ", " + Configuration.START_GID_ENV_KEY + ", and " +
                    Configuration.RESOURCE_ID_ENV_KEY);
        }

        return new Configuration(Configuration.toInt(startUID, Configuration.START_UID_ENV_KEY),
                Configuration.toInt(startGID, Configuration.START_GID_ENV_KEY),
                URI.create(resourceID), databaseConfiguration);
    }

    private static int toInt(String value, String envKey) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer value for environment variable " + envKey + ": " + value, e);
        }
    }

    public int getStartUID() {
        return startUID;
    }

    public int getStartGID() {
        return startGID;
    }

    public URI getResourceID() {
        return resourceID;
    }

    public DatabaseConfiguration getDatabaseConfiguration() {
        return databaseConfiguration;
    }

    public static final class DatabaseConfiguration {
        public static final String JNDI_DATASOURCE_ENV_KEY = "POSIX_MAPPER_JNDI_DATASOURCE";
        static final String SCHEMA_ENV_KEY = "POSIX_MAPPER_SCHEMA";

        private final String jndiDatasourceName;
        private final String schema;

        private DatabaseConfiguration(String jndiDatasourceName, String schema) {
            this.jndiDatasourceName = jndiDatasourceName;
            this.schema = schema;
        }

        static DatabaseConfiguration fromEnv() {
            final String jndiDatasourceName = System.getenv(DatabaseConfiguration.JNDI_DATASOURCE_ENV_KEY);
            final String schema = System.getenv(DatabaseConfiguration.SCHEMA_ENV_KEY);

            if (jndiDatasourceName == null || schema == null) {
                throw new IllegalStateException("Missing required database configuration environment variables: " +
                        DatabaseConfiguration.JNDI_DATASOURCE_ENV_KEY + " and " + DatabaseConfiguration.SCHEMA_ENV_KEY);
            }

            return new DatabaseConfiguration(jndiDatasourceName, schema);
        }

        public String getJNDIDatasourceName() {
            return jndiDatasourceName;
        }

        public String getSchema() {
            return schema;
        }
    }
}
