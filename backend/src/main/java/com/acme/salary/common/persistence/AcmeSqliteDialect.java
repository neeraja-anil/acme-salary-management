package com.acme.salary.common.persistence;

import java.sql.Types;
import java.util.Set;

import org.hibernate.community.dialect.SQLiteDialect;

/**
 * SQLite dialect that lets Hibernate's schema validation ({@code ddl-auto=validate}) accept our schema.
 * <p>
 * SQLite has a single 64-bit integer storage class, and primary keys must be declared exactly
 * {@code INTEGER PRIMARY KEY} to act as the rowid. The JDBC driver reports such columns as
 * {@link Types#INTEGER}, while Java {@code Long} fields expect {@link Types#BIGINT}. In SQLite these
 * are the same type, so we declare them equivalent instead of weakening validation.
 */
public class AcmeSqliteDialect extends SQLiteDialect {

    private static final Set<Integer> INTEGER_TYPES = Set.of(
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT);

    @Override
    public boolean equivalentTypes(int typeCode1, int typeCode2) {
        return (INTEGER_TYPES.contains(typeCode1) && INTEGER_TYPES.contains(typeCode2))
                || super.equivalentTypes(typeCode1, typeCode2);
    }
}
