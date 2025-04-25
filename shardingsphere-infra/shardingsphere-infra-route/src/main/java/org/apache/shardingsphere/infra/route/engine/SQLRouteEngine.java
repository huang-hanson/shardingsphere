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

package org.apache.shardingsphere.infra.route.engine;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.binder.LogicSQL;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.engine.impl.AllSQLRouteExecutor;
import org.apache.shardingsphere.infra.route.engine.impl.PartialSQLRouteExecutor;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.dialect.statement.mysql.dal.MySQLShowTableStatusStatement;
import org.apache.shardingsphere.sql.parser.sql.dialect.statement.mysql.dal.MySQLShowTablesStatement;

import java.util.Collection;

/**
 * SQL 路由引擎。
 * 该类是 ShardingSphere 中进行 SQL 路由（Routing）的核心入口。
 * 根据 SQL 类型和当前规则环境，选择合适的路由执行器（Executor）进行 SQL 路由。
 */
@RequiredArgsConstructor
public final class SQLRouteEngine {

    // 当前生效的所有 ShardingSphere 规则（例如分片规则、读写分离规则等）
    private final Collection<ShardingSphereRule> rules;

    // 配置属性（来自配置文件中的 props 配置）
    private final ConfigurationProperties props;

    /**
     * 对逻辑 SQL 进行路由，返回路由上下文信息（RouteContext）。
     *
     * @param logicSQL   表示经过解析、改写封装后的 LogicSQL 对象，包含 SQLStatement、参数、原始 SQL 等
     * @param metaData   当前数据库的元数据（包含各个逻辑库/表结构信息）
     * @return 路由上下文（包含每条实际 SQL 应该发往哪个数据源、目标表等信息）
     */
    public RouteContext route(final LogicSQL logicSQL, final ShardingSphereMetaData metaData) {
        // 判断是否需要对所有 schema 进行处理（如 SHOW TABLES 这类广播语句）
        SQLRouteExecutor executor = isNeedAllSchemas(logicSQL.getSqlStatementContext().getSqlStatement()) ? new AllSQLRouteExecutor() : new PartialSQLRouteExecutor(rules, props);
        return executor.route(logicSQL, metaData);
    }

    /**
     * 判断当前 SQL 是否需要对所有 schema 进行广播路由。
     * 通常用于 MySQL 的 SHOW TABLES 等语句，因为它们不指定具体 schema。
     *
     * TODO: 后续考虑通过动态配置来判断未配置 schema 的行为（UnconfiguredSchema）
     *
     * @param sqlStatement SQL 语句
     * @return true 则对所有 schema 进行路由；false 仅路由部分 schema
     */
    private boolean isNeedAllSchemas(final SQLStatement sqlStatement) {
        return sqlStatement instanceof MySQLShowTablesStatement || sqlStatement instanceof MySQLShowTableStatusStatement;
    }
}
