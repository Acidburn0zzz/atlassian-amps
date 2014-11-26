package com.atlassian.maven.plugins.amps;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Mapping database type by database uri prefix and database driver Please refer to the JIRA database documentation at
 * the following URL: http://www.atlassian.com/software/jira/docs/latest/databases/index.html
 */
public enum DatabaseType
{
    HSQL("hsql", true, "jdbc:hsqldb", "org.hsqldb.jdbcDriver"),
    MYSQL("mysql", false, "jdbc:mysql", "com.mysql.jdbc.Driver"),
    POSTGRESQL("postgres72", true, "jdbc:postgresql", "org.postgresql.Driver"),
    ORACLE("oracle10", false, "jdbc:oracle", "oracle.jdbc.OracleDriver"),
    MSSQL("mssql", true, "jdbc:sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    MSSQL_JTDS("mssql", true, "jdbc:jtds:sqlserver", "net.sourceforge.jtds.jdbc.Driver");

    private final String dbType;
    // if database does not have schema, schema-name node will be removed
    private final boolean haveSchema;
    private final String uriPrefix;
    private final String driverClassName;

    DatabaseType(String dbType, boolean haveSchema, String uriPrefix, String driverClassName)
    {
        this.dbType = dbType;
        this.haveSchema = haveSchema;
        this.uriPrefix = uriPrefix;
        this.driverClassName = driverClassName;
    }

    public static DatabaseType getDatabaseType(String uriPrefix, String driverClassName)
    {
        for (DatabaseType databaseType : values())
        {
            if (databaseType.accept(uriPrefix) && databaseType.driverClassName.equals(driverClassName))
            {
                return databaseType;
            }
        }
        return null;
    }

    /**
     * Checks whether the URI starts with the prefix associated with the database
     *
     * @param uri the give URI for connecting to the database
     * @return {@code true} if the URI is valid for this instance of data source factory
     */
    private boolean accept(String uri)
    {
        return checkNotNull(uri).trim().startsWith(uriPrefix);
    }

    public String getDbType()
    {
        return this.dbType;
    }

    public boolean hasSchema()
    {
        return this.haveSchema;
    }
}
