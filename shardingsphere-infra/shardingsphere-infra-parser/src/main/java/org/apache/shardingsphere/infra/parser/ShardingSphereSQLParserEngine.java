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

package org.apache.shardingsphere.infra.parser;

import com.google.common.util.concurrent.UncheckedExecutionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.shardingsphere.distsql.parser.engine.api.DistSQLStatementParserEngine;
import org.apache.shardingsphere.infra.parser.sql.SQLStatementParserEngine;
import org.apache.shardingsphere.infra.parser.sql.SQLStatementParserEngineFactory;
import org.apache.shardingsphere.sql.parser.exception.SQLParsingException;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.common.util.SQLUtil;

/**
 * ShardingSphere SQL 解析引擎。
 *
 * 该类统一封装了标准 SQL 和 DistSQL 的解析能力。
 * 对于标准 SQL，使用 SQLStatementParserEngine 进行解析；
 * 对于 DistSQL（ShardingSphere 自定义的分布式 SQL），使用 DistSQLStatementParserEngine。
 *
 * 支持使用缓存以提高解析性能，并在遇到解析异常时，尝试使用 DistSQL 解析作为兜底策略。
 */
public final class ShardingSphereSQLParserEngine {
    /**
     * 标准 SQL 解析引擎。
     * 该解析器支持对 MySQL、PostgreSQL、Oracle 等数据库类型的标准 SQL 语法进行解析。
     */
    private final SQLStatementParserEngine sqlStatementParserEngine;
    /**
     * DistSQL 解析引擎。
     * 用于解析 ShardingSphere 提供的自定义 SQL（如资源管理、规则定义等）。
     */
    private final DistSQLStatementParserEngine distSQLStatementParserEngine;
    /**
     * 构造方法。
     *
     * @param databaseTypeName 数据库类型名称（如 "MySQL"、"PostgreSQL"）
     * @param config 解析器配置，包括缓存选项、是否解析注释等
     */
    public ShardingSphereSQLParserEngine(final String databaseTypeName, final ParserConfiguration config) {
        // 获取标准 SQL 的解析引擎，使用工厂方法并带有缓存选项
        sqlStatementParserEngine = SQLStatementParserEngineFactory.getSQLStatementParserEngine(
                databaseTypeName, config.getSqlStatementCacheOption(), config.getParseTreeCacheOption(), config.isParseComment());

        // 初始化 DistSQL 解析引擎（不区分数据库类型）
        distSQLStatementParserEngine = new DistSQLStatementParserEngine();
    }

    /*
     * SkyWalking 监控系统相关提示：
     * 如果将来该类的 API 被修改，需要同步为 SkyWalking 提供新的插件适配。
     *
     * 详见插件开发文档：
     * https://github.com/apache/skywalking/blob/master/docs/en/guides/Java-Plugin-Development-Guide.md
     */
    /*
     * To make sure SkyWalking will be available at the next release of ShardingSphere, a new plugin should be provided to SkyWalking project if this API changed.
     *
     * @see <a href="https://github.com/apache/skywalking/blob/master/docs/en/guides/Java-Plugin-Development-Guide.md#user-content-plugin-development-guide">Plugin Development Guide</a>
     */
    /**
     * 解析 SQL 语句。
     *
     * 优先使用标准 SQL 解析器进行解析；如果解析失败（抛出异常），
     * 将尝试使用 DistSQL 解析器对去除注释后的 SQL 再次进行解析；
     * 若仍失败，则抛出最初的异常。
     *
     * @param sql 待解析的 SQL 字符串
     * @param useCache 是否使用解析缓存（true 表示启用缓存，false 表示每次都解析）
     * @return SQLStatement 对象，表示解析后的 SQL 抽象语法树（AST）
     */
    public SQLStatement parse(final String sql, final boolean useCache) {
        try {
            // 尝试使用标准 SQL 解析器进行解析
            return sqlStatementParserEngine.parse(sql, useCache);
        } catch (final SQLParsingException | ParseCancellationException | UncheckedExecutionException originalEx) {
            try {
                // 如果标准 SQL 解析失败，尝试使用 DistSQL 解析器解析去除注释后的 SQL
                String trimSQL = SQLUtil.trimComment(sql);
                return distSQLStatementParserEngine.parse(trimSQL);
            } catch (final SQLParsingException ignored) {
                // DistSQL 解析仍然失败，则抛出原始异常
                throw originalEx;
            }
        }
    }
}
