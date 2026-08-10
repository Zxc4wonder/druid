/*
 * Copyright 1999-2017 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.bvt.sql.postgresql.issues;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for JumpServer issue #16341.
 *
 * <p>Druid 1.2.28 fails to round-trip several PostgreSQL {@code ALTER COLUMN} forms:
 * <ul>
 *   <li>{@code ALTER COLUMN ... TYPE <type> COLLATE <collation>} drops the COLLATE clause;</li>
 *   <li>{@code ALTER COLUMN ... SET DATA TYPE <type>} throws a ParserException
 *       (the SET branch only accepted NOT NULL / DEFAULT);</li>
 *   <li>{@code ALTER COLUMN ... TYPE <type> USING <expr>} throws a ParserException
 *       at the USING keyword.</li>
 * </ul>
 *
 * <p>Covered scenarios:
 * <ol>
 *   <li>{@code TYPE} + {@code COLLATE};</li>
 *   <li>{@code TYPE CHARACTER VARYING(n)};</li>
 *   <li>{@code SET DATA TYPE};</li>
 *   <li>{@code TYPE ... USING}.</li>
 * </ol>
 *
 * <p>The fix is a targeted backport of upstream commits
 * {@code d42545ff} (PG {@code SET DATA TYPE} / {@code COLLATE}, issues #6467 / #6573) and
 * {@code 26b856b1} (PG {@code TYPE ... USING}, issue #6064).
 */
public class JumpServer16341Test {
    private static String roundTrip(String sql) {
        List<SQLStatement> statements = SQLUtils.parseStatements(sql, DbType.postgresql);
        assertEquals(1, statements.size());
        return SQLUtils.toSQLString(statements.get(0), DbType.postgresql);
    }

    /** Collapse all runs of whitespace into single spaces, for format-independent checks. */
    private static String squash(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * Scenario 1: ALTER COLUMN ... TYPE <char type> COLLATE <collation>.
     *
     * <p>The TYPE keyword and the COLLATE clause (with its quoted schema/name) must both survive
     * the round-trip; the output must not degrade to a bare {@code ALTER COLUMN col <type>} form.
     */
    @Test
    public void scenario1_type_and_collate() {
        String sql = "ALTER TABLE \"public\".\"test_alter_table\" "
                + "ALTER COLUMN \"job_code\" "
                + "TYPE VARCHAR(255) COLLATE \"pg_catalog\".\"default\"";

        String out = roundTrip(sql);
        String upper = squash(out).toUpperCase();

        // Type-change keyword (TYPE or SET DATA TYPE) must remain before the data type.
        assertTrue("output must keep TYPE/SET DATA TYPE before the data type: " + out,
                upper.contains("TYPE VARCHAR(255)"));

        // COLLATE keyword and its quoted content must be preserved exactly.
        assertTrue("COLLATE keyword must be present: " + out, upper.contains("COLLATE"));
        assertTrue("COLLATE content must be preserved: " + out,
                out.contains("\"pg_catalog\".\"default\""));
    }

    /**
     * Scenario 2: ALTER COLUMN ... TYPE CHARACTER VARYING(n).
     *
     * <p>The TYPE keyword and the {@code CHARACTER VARYING(n)} type name must be preserved.
     */
    @Test
    public void scenario2_type_character_varying() {
        String sql = "ALTER TABLE \"public\".\"test_alter_table\" "
                + "ALTER COLUMN \"job_code\" "
                + "TYPE CHARACTER VARYING(255)";

        String out = roundTrip(sql);
        String upper = squash(out).toUpperCase();

        assertTrue("output must keep TYPE/SET DATA TYPE: " + out, upper.contains("TYPE"));
        assertTrue("CHARACTER VARYING(255) must be preserved: " + out,
                upper.contains("CHARACTER VARYING(255)"));
    }

    /**
     * Scenario 3: ALTER COLUMN ... SET DATA TYPE <type>.
     *
     * <p>Must parse without error and re-emit a legal type-change statement (SET DATA TYPE or
     * normalized to TYPE).
     */
    @Test
    public void scenario3_set_data_type() {
        String sql = "ALTER TABLE test_alter_table "
                + "ALTER COLUMN job_code "
                + "SET DATA TYPE VARCHAR(255)";

        String out = roundTrip(sql);
        String upper = squash(out).toUpperCase();

        assertTrue("output must keep TYPE/SET DATA TYPE and VARCHAR(255): " + out,
                upper.contains("TYPE") && upper.contains("VARCHAR(255)"));
    }

    /**
     * Scenario 4: ALTER COLUMN ... TYPE <type> USING <expr>.
     *
     * <p>The type-change keyword and the USING expression must both survive the round-trip.
     */
    @Test
    public void scenario4_type_using() {
        String sql = "ALTER TABLE test_alter_table "
                + "ALTER COLUMN value TYPE bigint USING value::bigint";

        String out = roundTrip(sql);
        String upper = squash(out).toUpperCase();

        assertTrue("output must keep TYPE/SET DATA TYPE bigint: " + out,
                upper.contains("TYPE") && upper.contains("BIGINT"));
        assertTrue("USING keyword must be present: " + out, upper.contains("USING"));
        // The USING expression must be preserved (case-insensitive on the bare identifiers,
        // but the :: cast form and structure must remain).
        assertTrue("USING expression must be preserved: " + out,
                out.toLowerCase().contains("value::bigint"));
    }
}
