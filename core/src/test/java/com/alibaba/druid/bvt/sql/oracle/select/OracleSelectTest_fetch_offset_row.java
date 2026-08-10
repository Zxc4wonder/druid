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
package com.alibaba.druid.bvt.sql.oracle.select;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import junit.framework.TestCase;

public class OracleSelectTest_fetch_offset_row extends TestCase {
    public void test_fetch_offset_row() {
        assertFormatted(
                "SELECT * FROM t FETCH FIRST 1 ROW ONLY;",
                "SELECT *\nFROM t\nFETCH FIRST 1 ROWS ONLY;");
        assertFormatted(
                "SELECT * FROM t FETCH FIRST 2 ROWS ONLY;",
                "SELECT *\nFROM t\nFETCH FIRST 2 ROWS ONLY;");
        assertFormatted(
                "SELECT * FROM t OFFSET 1 ROW;",
                "SELECT *\nFROM t\nOFFSET 1 ROWS;");
        assertFormatted(
                "SELECT * FROM t OFFSET 2 ROWS;",
                "SELECT *\nFROM t\nOFFSET 2 ROWS;");
        assertFormatted(
                "SELECT * FROM t OFFSET 1 ROW FETCH NEXT 1 ROW ONLY;",
                "SELECT *\nFROM t\nOFFSET 1 ROWS FETCH FIRST 1 ROWS ONLY;");
    }

    private void assertFormatted(String sql, String expected) {
        SQLStatement statement = SQLUtils.parseSingleStatement(sql, DbType.oracle);
        String formatted = SQLUtils.toSQLString(statement, DbType.oracle);

        assertEquals(expected, formatted);
        assertNotNull(SQLUtils.parseSingleStatement(formatted, DbType.oracle));
    }
}
