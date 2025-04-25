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

package org.apache.shardingsphere.infra.rewrite.engine;

import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.datanode.DataNode;
import org.apache.shardingsphere.infra.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.infra.rewrite.engine.result.RouteSQLRewriteResult;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteUnit;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.ParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.impl.GroupedParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.impl.StandardParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.sql.impl.RouteSQLBuilder;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.sql.parser.sql.common.util.SQLUtil;
import org.apache.shardingsphere.sql.parser.sql.dialect.handler.dml.SelectStatementHandler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 基于路由单元的 SQL 改写引擎，处理需要分片执行的 SQL 改写。
 * 核心功能：
 * 1. 根据路由单元（RouteUnit）生成分片SQL
 * 2. 支持两种改写模式：单路由单元独立改写 vs 多路由单元聚合改写（UNION ALL）
 * 3. 处理参数与路由单元的匹配关系
 */
public final class RouteSQLRewriteEngine {

    /**
     * 执行 SQL 和参数改写。
     *
     * @param sqlRewriteContext SQL 改写上下文（包含原始SQL、参数、SQLToken等）
     * @param routeContext 路由上下文（包含所有路由单元信息）
     * @return 路由SQL改写结果（包含每个路由单元对应的改写SQL和参数）
     */
    public RouteSQLRewriteResult rewrite(final SQLRewriteContext sqlRewriteContext, final RouteContext routeContext) {
        // 结果映射：RouteUnit -> 改写后的SQL和参数
        Map<RouteUnit, SQLRewriteUnit> result = new LinkedHashMap<>(routeContext.getRouteUnits().size(), 1);

        // 按数据源分组路由单元（相同数据源的路由单元可能合并执行）
        for (Entry<String, Collection<RouteUnit>> entry : aggregateRouteUnitGroups(routeContext.getRouteUnits()).entrySet()) {
            Collection<RouteUnit> routeUnits = entry.getValue();

            // 判断是否需要聚合改写（将多个路由单元合并为UNION ALL查询）
            if (isNeedAggregateRewrite(sqlRewriteContext.getSqlStatementContext(), routeUnits)) {
                // 聚合模式：多个路由单元合并为一个SQLRewriteUnit
                result.put(routeUnits.iterator().next(), createSQLRewriteUnit(sqlRewriteContext, routeContext, routeUnits));
            } else {
                // 独立模式：每个路由单元生成独立的SQLRewriteUnit
                addSQLRewriteUnits(result, sqlRewriteContext, routeContext, routeUnits);
            }
        }
        return new RouteSQLRewriteResult(result);
    }

    /**
     * 创建聚合改写单元（UNION ALL 模式）。
     * 适用场景：简单SELECT查询跨多个分片表，无子查询/JOIN/ORDER BY/LOCK等复杂语法。
     */
    private SQLRewriteUnit createSQLRewriteUnit(final SQLRewriteContext sqlRewriteContext,
                                                final RouteContext routeContext,
                                                final Collection<RouteUnit> routeUnits) {
        Collection<String> sql = new LinkedList<>();
        List<Object> parameters = new LinkedList<>();

        // 特殊处理包含$参数的SELECT语句（如PostgreSQL的$1占位符）
        boolean containsDollarMarker = sqlRewriteContext.getSqlStatementContext() instanceof SelectStatementContext
                && ((SelectStatementContext) sqlRewriteContext.getSqlStatementContext()).isContainsDollarParameterMarker();

        for (RouteUnit each : routeUnits) {
            // 生成单路由单元SQL并去除末尾分号
            sql.add(SQLUtil.trimSemicolon(new RouteSQLBuilder(sqlRewriteContext, each).toSQL()));

            // 如果包含$参数且参数已填充，则跳过重复添加
            if (containsDollarMarker && !parameters.isEmpty()) {
                continue;
            }
            parameters.addAll(getParameters(sqlRewriteContext.getParameterBuilder(), routeContext, each));
        }

        // 合并为UNION ALL查询
        return new SQLRewriteUnit(String.join(" UNION ALL ", sql), parameters);
    }

    /**
     * 添加独立路由单元改写结果。
     */
    private void addSQLRewriteUnits(final Map<RouteUnit, SQLRewriteUnit> sqlRewriteUnits,
                                    final SQLRewriteContext sqlRewriteContext,
                                    final RouteContext routeContext,
                                    final Collection<RouteUnit> routeUnits) {
        for (RouteUnit each : routeUnits) {
            sqlRewriteUnits.put(each,
                    new SQLRewriteUnit(
                            new RouteSQLBuilder(sqlRewriteContext, each).toSQL(),
                            getParameters(sqlRewriteContext.getParameterBuilder(), routeContext, each)
                    ));
        }
    }

    /**
     * 判断是否需要聚合改写（UNION ALL模式）。
     * 满足以下所有条件时启用：
     * 1. 必须是SELECT语句
     * 2. 多个路由单元
     * 3. 不包含子查询/JOIN查询
     * 4. 不包含ORDER BY/LIMIT子句
     * 5. 不包含锁语句（如FOR UPDATE）
     */
    private boolean isNeedAggregateRewrite(final SQLStatementContext<?> sqlStatementContext,
                                           final Collection<RouteUnit> routeUnits) {
        if (!(sqlStatementContext instanceof SelectStatementContext) || routeUnits.size() == 1) {
            return false;
        }
        SelectStatementContext statementContext = (SelectStatementContext) sqlStatementContext;

        boolean containsComplexQuery = statementContext.isContainsSubquery() || statementContext.isContainsJoinQuery();
        boolean containsOrderByLimit = !statementContext.getOrderByContext().getItems().isEmpty()
                || statementContext.getPaginationContext().isHasPagination();
        boolean containsLock = SelectStatementHandler.getLockSegment(statementContext.getSqlStatement()).isPresent();

        boolean needAggregate = !containsComplexQuery && !containsOrderByLimit && !containsLock;
        statementContext.setNeedAggregateRewrite(needAggregate); // 标记改写状态供后续使用
        return needAggregate;
    }

    /**
     * 按数据源名称分组路由单元。
     */
    private Map<String, Collection<RouteUnit>> aggregateRouteUnitGroups(final Collection<RouteUnit> routeUnits) {
        Map<String, Collection<RouteUnit>> result = new LinkedHashMap<>(routeUnits.size(), 1);
        for (RouteUnit each : routeUnits) {
            String dataSourceName = each.getDataSourceMapper().getActualName();
            result.computeIfAbsent(dataSourceName, unused -> new LinkedList<>()).add(each);
        }
        return result;
    }

    /**
     * 获取路由单元对应的参数列表。
     */
    private List<Object> getParameters(final ParameterBuilder parameterBuilder,
                                       final RouteContext routeContext,
                                       final RouteUnit routeUnit) {
        // 标准参数构建器直接返回全局参数
        if (parameterBuilder instanceof StandardParameterBuilder) {
            return parameterBuilder.getParameters();
        }

        // 分组参数构建器需匹配参数与数据节点关系
        return routeContext.getOriginalDataNodes().isEmpty()
                ? ((GroupedParameterBuilder) parameterBuilder).getParameters()
                : buildRouteParameters((GroupedParameterBuilder) parameterBuilder, routeContext, routeUnit);
    }

    /**
     * 构建路由单元专属参数列表（用于批量插入等场景）。
     */
    private List<Object> buildRouteParameters(final GroupedParameterBuilder parameterBuilder,
                                              final RouteContext routeContext,
                                              final RouteUnit routeUnit) {
        List<Object> result = new LinkedList<>();
        int count = 0;

        // 遍历原始数据节点，匹配当前路由单元
        for (Collection<DataNode> each : routeContext.getOriginalDataNodes()) {
            if (isInSameDataNode(each, routeUnit)) {
                result.addAll(parameterBuilder.getParameters(count));
            }
            count++;
        }

        // 添加通用参数（不绑定到特定分片）
        result.addAll(parameterBuilder.getGenericParameterBuilder().getParameters());
        return result;
    }

    /**
     * 判断数据节点是否属于当前路由单元。
     */
    private boolean isInSameDataNode(final Collection<DataNode> dataNodes, final RouteUnit routeUnit) {
        if (dataNodes.isEmpty()) {
            return true;
        }
        for (DataNode each : dataNodes) {
            if (routeUnit.findTableMapper(each.getDataSourceName(), each.getTableName()).isPresent()) {
                return true;
            }
        }
        return false;
    }
}