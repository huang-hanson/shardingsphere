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

package org.apache.shardingsphere.infra.executor.sql.context;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.infra.binder.segment.table.TablesContext;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.rewrite.engine.result.GenericSQLRewriteResult;
import org.apache.shardingsphere.infra.rewrite.engine.result.RouteSQLRewriteResult;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteResult;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteUnit;
import org.apache.shardingsphere.infra.route.context.RouteMapper;
import org.apache.shardingsphere.infra.route.context.RouteUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Execution context builder.
 * 执行上下文构建器，用于根据 SQL 改写结果生成最终的 SQL 执行单元（ExecutionUnit）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExecutionContextBuilder {

    /**
     * 构建 SQL 执行单元集合。
     *
     * @param metaData ShardingSphere 元数据
     * @param sqlRewriteResult SQL 改写结果，可能是单路由或多路由
     * @param sqlStatementContext SQL 语句上下文（包含表、参数等绑定信息）
     * @return SQL 执行单元（ExecutionUnit）集合
     */
    public static Collection<ExecutionUnit> build(final ShardingSphereMetaData metaData, final SQLRewriteResult sqlRewriteResult, final SQLStatementContext<?> sqlStatementContext) {
        // 判断 SQL 是否为通用改写结果（即不依赖具体数据节点的情况，如广播表、无表 DML 等）
        return sqlRewriteResult instanceof GenericSQLRewriteResult
                ? build(metaData, (GenericSQLRewriteResult) sqlRewriteResult, sqlStatementContext) :
                build((RouteSQLRewriteResult) sqlRewriteResult);// 否则为路由相关改写结果
    }

    /**
     * 构建通用 SQL 改写结果的执行单元（仅涉及单数据源场景）。
     */
    private static Collection<ExecutionUnit> build(final ShardingSphereMetaData metaData, final GenericSQLRewriteResult sqlRewriteResult, final SQLStatementContext<?> sqlStatementContext) {
        Collection<String> instanceDataSourceNames = metaData.getResource().getDataSourcesMetaData().getAllInstanceDataSourceNames();
        if (instanceDataSourceNames.isEmpty()) {
            return Collections.emptyList();
        }
        // 通用 SQL 情况下，只需要取出任意一个数据源执行即可
        return Collections.singletonList(new ExecutionUnit(instanceDataSourceNames.iterator().next(),
                new SQLUnit(sqlRewriteResult.getSqlRewriteUnit().getSql(), sqlRewriteResult.getSqlRewriteUnit().getParameters(), getGenericTableRouteMappers(sqlStatementContext))));
    }

    /**
     * 构建包含具体路由信息的 SQL 执行单元。
     */
    private static Collection<ExecutionUnit> build(final RouteSQLRewriteResult sqlRewriteResult) {
        Collection<ExecutionUnit> result = new LinkedHashSet<>(sqlRewriteResult.getSqlRewriteUnits().size(), 1f);
        for (Entry<RouteUnit, SQLRewriteUnit> entry : sqlRewriteResult.getSqlRewriteUnits().entrySet()) {
            result.add(new ExecutionUnit(
                    // 路由到的实际数据源名称
                    entry.getKey().getDataSourceMapper().getActualName(),
                    new SQLUnit(
                            entry.getValue().getSql(),
                            entry.getValue().getParameters(),
                            getRouteTableRouteMappers(entry.getKey().getTableMappers())
                    )));
        }
        return result;
    }

    /**
     * 获取路由改写时使用的逻辑表到真实表的映射关系。
     */
    private static List<RouteMapper> getRouteTableRouteMappers(final Collection<RouteMapper> tableMappers) {
        if (null == tableMappers) {
            return Collections.emptyList();
        }
        List<RouteMapper> result = new ArrayList<>(tableMappers.size());
        for (RouteMapper each : tableMappers) {
            // 将路由后的逻辑表名和实际表名封装为 RouteMapper 对象
            result.add(new RouteMapper(each.getLogicName(), each.getActualName()));
        }
        return result;
    }

    /**
     * 获取通用改写的逻辑表名映射（实际表名与逻辑表名相同）。
     */
    private static List<RouteMapper> getGenericTableRouteMappers(final SQLStatementContext<?> sqlStatementContext) {
        TablesContext tablesContext = null;
        if (null != sqlStatementContext) {
            tablesContext = sqlStatementContext.getTablesContext();
        }
        // 对所有逻辑表名生成“逻辑 = 实际” 的 RouteMapper
        return null == tablesContext
                ? Collections.emptyList()
                : tablesContext.getTableNames().stream()
                .map(tableName -> new RouteMapper(tableName, tableName))
                .collect(Collectors.toList());
    }
}
