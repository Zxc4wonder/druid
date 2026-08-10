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
package com.alibaba.druid.bvt.sql.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.util.Utils;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SQLASTOutputVisitorLineCommentTest {
    private static final String SELECT_ITEM_LINE_COMMENT =
            "SELECT (id - 1) -- comment\n, name FROM users;";

    @Test
    public void test_selectItemLineComment_mysql() {
        assertSelectItemRoundTrip(SELECT_ITEM_LINE_COMMENT, DbType.mysql, 2);
    }

    @Test
    public void test_selectItemLineComment_postgresql() {
        assertSelectItemRoundTrip(SELECT_ITEM_LINE_COMMENT, DbType.postgresql, 2);
    }

    @Test
    public void test_selectItemLineComment_oracle() {
        assertSelectItemRoundTrip(SELECT_ITEM_LINE_COMMENT, DbType.oracle, 2);
    }

    @Test
    public void test_selectItemLineComment_clickhouse() {
        assertSelectItemRoundTrip(SELECT_ITEM_LINE_COMMENT, DbType.clickhouse, 2);
    }

    @Test
    public void test_lineCommentBeforeFrom() {
        assertSelectItemRoundTrip("SELECT id -- comment\nFROM users;", DbType.mysql, 1);
    }

    @Test
    public void test_lineCommentAfterComma() {
        String sql = "SELECT\n"
                + "    id, -- comment\n"
                + "    name\n"
                + "FROM users;";
        assertSelectItemRoundTrip(sql, DbType.mysql, 2);
    }

    @Test
    public void test_blockCommentBeforeComma() {
        String sql = "SELECT\n"
                + "    id /* comment */,\n"
                + "    name\n"
                + "FROM users;";
        String output = assertSelectItemRoundTrip(sql, DbType.mysql, 2);
        assertTrue(output, output.contains("SELECT id, name"));
    }

    @Test
    public void test_lineCommentInFunctionArguments() {
        String sql = "SELECT\n"
                + "    func(a -- comment\n"
                + "    , b)\n"
                + "FROM t;";
        SQLSelectStatement statement = parseSelect(sql, DbType.mysql);
        SQLSelectQueryBlock queryBlock = queryBlock(statement);
        assertEquals(2, methodArguments(queryBlock));

        String output = SQLUtils.toSQLString(statement, DbType.mysql);
        assertTrue(output, output.contains("-- comment"));

        SQLSelectQueryBlock reparsed = queryBlock(parseSelect(output, DbType.mysql));
        assertEquals(output, 2, methodArguments(reparsed));
    }

    @Test
    public void test_oracleNumbersResource() {
        String resource = "bvt/parser/antlr_grammers_v4_plsql/examples/numbers01.sql";
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        assertNotNull(resource, input);

        assertSelectItemRoundTrip(Utils.read(input), DbType.oracle, 12);
    }

    private String assertSelectItemRoundTrip(String sql, DbType dbType, int expectedItemCount) {
        SQLSelectStatement statement = parseSelect(sql, dbType);
        SQLSelectQueryBlock queryBlock = queryBlock(statement);
        assertEquals(expectedItemCount, queryBlock.getSelectList().size());

        String output = SQLUtils.toSQLString(statement, dbType);
        if (sql.contains("--")) {
            assertTrue(output, output.contains("--"));
        }

        SQLSelectQueryBlock reparsed = queryBlock(parseSelect(output, dbType));
        assertEquals(output, expectedItemCount, reparsed.getSelectList().size());
        return output;
    }

    private SQLSelectStatement parseSelect(String sql, DbType dbType) {
        return (SQLSelectStatement) SQLUtils.parseSingleStatement(sql, dbType);
    }

    private SQLSelectQueryBlock queryBlock(SQLSelectStatement statement) {
        return (SQLSelectQueryBlock) statement.getSelect().getQuery();
    }

    private int methodArguments(SQLSelectQueryBlock queryBlock) {
        SQLExpr expr = queryBlock.getSelectList().get(0).getExpr();
        return ((SQLMethodInvokeExpr) expr).getArguments().size();
    }
}
