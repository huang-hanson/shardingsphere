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

package org.apache.shardingsphere.infra.context.kernel;

import org.apache.shardingsphere.infra.binder.LogicSQL;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContext;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContextBuilder;
import org.apache.shardingsphere.infra.executor.sql.log.SQLLogger;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.rewrite.SQLRewriteEntry;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteResult;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.engine.SQLRouteEngine;

/**
 * KernelProcessor 是核心处理器类，用于生成 SQL 的 ExecutionContext。
 * ExecutionContext 包含了执行 SQL 所需的所有上下文信息，如路由信息、改写后的 SQL、参数等。
 */
public final class KernelProcessor {

    /**
     * 生成执行上下文。
     *
     * @param logicSQL 逻辑 SQL 对象（包含原始 SQL、参数、SQL 上下文）
     * @param metaData 数据库的元数据（包括表结构、分片规则等）
     * @param props 配置属性（如是否显示 SQL 日志等）
     * @return SQL 执行上下文 ExecutionContext
     */
    public ExecutionContext generateExecutionContext(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ConfigurationProperties props) {
        // 第一步：执行路由，根据逻辑 SQL 和规则决定该 SQL 应该在哪些数据源、表上执行
        RouteContext routeContext = route(logicSQL, metaData, props);
        // 第二步：SQL 改写，例如添加真实表名、绑定参数等
        SQLRewriteResult rewriteResult = rewrite(logicSQL, metaData, props, routeContext);
        // 第三步：构造最终的执行上下文，包括执行单元（SQLUnit）和路由信息
        ExecutionContext result = createExecutionContext(logicSQL, metaData, routeContext, rewriteResult);
        // 第四步（可选）：如果开启了 SQL 显示功能，则打印 SQL 日志
        logSQL(logicSQL, props, result);
        return result;
    }

    /**
     * 路由逻辑 SQL，生成 RouteContext。
     *
     * @param logicSQL 逻辑 SQL
     * @param metaData 元数据
     * @param props 配置属性
     * @return 路由上下文（包含路由结果）
     */
    private RouteContext route(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ConfigurationProperties props) {
        // 使用 SQLRouteEngine 路由引擎进行路由计算
        return new SQLRouteEngine(metaData.getRuleMetaData().getRules(), props).route(logicSQL, metaData);
    }

    /**
     * 对 SQL 进行改写。
     *
     * @param logicSQL 逻辑 SQL
     * @param metaData 元数据
     * @param props 配置属性
     * @param routeContext 路由上下文
     * @return SQLRewriteResult 改写后的 SQL 结果
     */
    private SQLRewriteResult rewrite(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ConfigurationProperties props, final RouteContext routeContext) {
        // 创建 SQL 改写引擎，并执行 SQL 改写逻辑
        SQLRewriteEntry sqlRewriteEntry = new SQLRewriteEntry(metaData.getName(), metaData.getDefaultSchema(), props, metaData.getRuleMetaData().getRules());
        return sqlRewriteEntry.rewrite(logicSQL.getSql(), logicSQL.getParameters(), logicSQL.getSqlStatementContext(), routeContext);
    }

    /**
     * 创建最终的执行上下文。
     *
     * @param logicSQL 逻辑 SQL
     * @param metaData 元数据
     * @param routeContext 路由上下文
     * @param rewriteResult 改写结果
     * @return ExecutionContext 执行上下文
     */
    private ExecutionContext createExecutionContext(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final RouteContext routeContext, final SQLRewriteResult rewriteResult) {
        // 调用 ExecutionContextBuilder 构建执行单元
        return new ExecutionContext(logicSQL, ExecutionContextBuilder.build(metaData, rewriteResult, logicSQL.getSqlStatementContext()), routeContext);
    }

    /**
     * 打印 SQL 日志（可选，根据配置决定是否打印）。
     *
     * @param logicSQL 逻辑 SQL
     * @param props 配置属性
     * @param executionContext 执行上下文
     */
    private void logSQL(final LogicSQL logicSQL, final ConfigurationProperties props, final ExecutionContext executionContext) {
        // 如果 SQL_SHOW 配置项为 true，则打印 SQL 日志
        if (props.<Boolean>getValue(ConfigurationPropertyKey.SQL_SHOW)) {
            SQLLogger.logSQL(logicSQL, props.<Boolean>getValue(ConfigurationPropertyKey.SQL_SIMPLE), executionContext);
        }
    }
}
