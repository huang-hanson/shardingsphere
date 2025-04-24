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

import com.google.common.cache.LoadingCache;
import org.apache.shardingsphere.infra.parser.cache.SQLStatementCacheBuilder;
import org.apache.shardingsphere.sql.parser.api.CacheOption;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;

/**
 * SQL 语句解析引擎。
 *
 * 该类封装了 SQL 语句解析的核心逻辑，支持使用缓存提高解析效率，
 * 并基于数据库类型、语法树缓存选项以及是否解析注释等配置初始化解析执行器。
 */
public final class SQLStatementParserEngine {
    /**
     * SQL 语句解析执行器。
     * 用于实际执行 SQL 字符串的语法解析，生成 SQLStatement 抽象语法树（AST）。
     */
    private final SQLStatementParserExecutor sqlStatementParserExecutor;
    /**
     * SQL 语句缓存（Guava 缓存）。
     * Key：原始 SQL 字符串；
     * Value：对应的解析结果 SQLStatement；
     * 该缓存用于避免重复解析相同 SQL，提高系统性能。
     */
    private final LoadingCache<String, SQLStatement> sqlStatementCache;
    /**
     * 构造方法。
     *
     * @param databaseType 数据库类型名称（如 "MySQL", "PostgreSQL" 等）
     * @param sqlStatementCacheOption SQL 语句级缓存配置（影响是否缓存解析结果）
     * @param parseTreeCacheOption 语法树缓存配置（影响解析器行为）
     * @param isParseComment 是否解析 SQL 中的注释（true 表示保留注释）
     */
    public SQLStatementParserEngine(final String databaseType, final CacheOption sqlStatementCacheOption, final CacheOption parseTreeCacheOption, final boolean isParseComment) {
        // 初始化 SQL 解析执行器，主要负责语法分析、语法树构建等底层操作
        sqlStatementParserExecutor = new SQLStatementParserExecutor(databaseType, parseTreeCacheOption, isParseComment);
        // 初始化 SQL 解析执行器，主要负责语法分析、语法树构建等底层操作
        sqlStatementCache = SQLStatementCacheBuilder.build(databaseType, sqlStatementCacheOption, parseTreeCacheOption, isParseComment);
    }

    /**
     * 解析 SQL 字符串为 SQLStatement 抽象语法树对象。
     *
     * @param sql 待解析的 SQL 语句
     * @param useCache 是否启用缓存：
     *                 true - 优先从缓存中获取解析结果；
     *                 false - 每次都重新解析 SQL，忽略缓存。
     * @return SQLStatement 抽象语法树对象，表示该 SQL 的结构化表达
     */
    public SQLStatement parse(final String sql, final boolean useCache) {// 使用缓存（如果没有就会自动解析并放入缓存）
        return useCache ? sqlStatementCache.getUnchecked(sql) : sqlStatementParserExecutor.parse(sql);// 不使用缓存，直接执行解析
    }
}
