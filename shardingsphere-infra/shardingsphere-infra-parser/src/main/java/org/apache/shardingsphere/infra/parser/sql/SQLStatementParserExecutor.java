/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.parser.sql;

import org.apache.shardingsphere.sql.parser.api.CacheOption;
import org.apache.shardingsphere.sql.parser.api.SQLParserEngine;
import org.apache.shardingsphere.sql.parser.api.SQLVisitorEngine;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;

import java.util.Properties;

/**
 * SQL statement parser executor.
 */
public final class SQLStatementParserExecutor {

    // SQL 解析器引擎，负责将 SQL 文本解析为抽象语法树（AST）
    private final SQLParserEngine parserEngine;

    // SQL 访问器引擎，负责将解析树（ParseTree）转换为 SQLStatement 对象
    private final SQLVisitorEngine visitorEngine;

    /**
     * 构造函数，初始化解析器引擎和访问器引擎。
     *
     * @param databaseType 数据库类型，例如：MySQL、PostgreSQL、Oracle等
     * @param parseTreeCacheOption ParseTree 缓存配置，用于提升性能
     * @param isParseComment 是否解析 SQL 注释
     */
    public SQLStatementParserExecutor(final String databaseType, final CacheOption parseTreeCacheOption, final boolean isParseComment) {
        // 初始化 SQL 解析器引擎
        parserEngine = new SQLParserEngine(databaseType, parseTreeCacheOption);
        // 初始化 SQL 访问器引擎，STATEMENT 模式代表将 ParseTree 转换为 SQLStatement
        visitorEngine = new SQLVisitorEngine(databaseType, "STATEMENT", isParseComment, new Properties());
    }

    /**
     * 解析 SQL 字符串为 SQLStatement 对象。
     *
     * @param sql 要解析的 SQL 字符串
     * @return SQLStatement 抽象语法树结果
     */
    public SQLStatement parse(final String sql) {
        // 先通过 parserEngine 解析为 ParseTree，然后通过 visitorEngine 转换为 SQLStatement
        return visitorEngine.visit(parserEngine.parse(sql, false));
    }
}
