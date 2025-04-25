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

package org.apache.shardingsphere.infra.executor.sql.prepare;

import com.google.common.collect.Lists;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroup;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupContext;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionUnit;
import org.apache.shardingsphere.infra.executor.sql.context.SQLUnit;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.ConnectionMode;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.spi.ordered.OrderedSPIRegistry;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 执行计划准备引擎的抽象类。
 *
 * @param <T> 表示执行组中实际的任务类型（如 Statement、PreparedStatement 等）
 */
public abstract class AbstractExecutionPrepareEngine<T> implements ExecutionPrepareEngine<T> {

    // 静态代码块：注册装饰器 SPI 接口
    static {
        ShardingSphereServiceLoader.register(ExecutionPrepareDecorator.class);
    }

    // 每个查询最大连接数限制（用于控制连接模式选择）
    private final int maxConnectionsSizePerQuery;

    // 根据规则映射的装饰器集合
    @SuppressWarnings("rawtypes")
    private final Map<ShardingSphereRule, ExecutionPrepareDecorator> decorators;

    // 构造函数：初始化最大连接数和规则装饰器映射
    protected AbstractExecutionPrepareEngine(final int maxConnectionsSizePerQuery, final Collection<ShardingSphereRule> rules) {
        this.maxConnectionsSizePerQuery = maxConnectionsSizePerQuery;
        decorators = OrderedSPIRegistry.getRegisteredServices(ExecutionPrepareDecorator.class, rules);
    }

    /**
     * 主执行准备方法，将路由上下文和 ExecutionUnit 转换为执行组上下文。
     */
    @Override
    public final ExecutionGroupContext<T> prepare(final RouteContext routeContext, final Collection<ExecutionUnit> executionUnits) throws SQLException {
        Collection<ExecutionGroup<T>> result = new LinkedList<>();

        // 1. 按数据源分组聚合 SQL 单元
        for (Entry<String, List<SQLUnit>> entry : aggregateSQLUnitGroups(executionUnits).entrySet()) {
            String dataSourceName = entry.getKey();
            List<SQLUnit> sqlUnits = entry.getValue();
            // 2. 将 SQL 分组（用于控制连接复用等）
            List<List<SQLUnit>> sqlUnitGroups = group(sqlUnits);

            // 3. 判断连接模式（基于最大连接数与 SQL 数量）
            ConnectionMode connectionMode = maxConnectionsSizePerQuery < sqlUnits.size() ? ConnectionMode.CONNECTION_STRICTLY : ConnectionMode.MEMORY_STRICTLY;

            // 4. 将 SQL 分组构建成执行组
            result.addAll(group(dataSourceName, sqlUnitGroups, connectionMode));
        }
        // 5. 对执行组应用装饰器（例如事务控制、SQL 追踪等）
        return decorate(routeContext, result);
    }

    /**
     * 按照最大连接数对 SQLUnit 分组。
     * 例如如果最大连接数是2，SQL数量是6，则每个连接处理3条SQL。
     */
    private List<List<SQLUnit>> group(final List<SQLUnit> sqlUnits) {
        int desiredPartitionSize = Math.max(0 == sqlUnits.size() % maxConnectionsSizePerQuery ? sqlUnits.size() / maxConnectionsSizePerQuery : sqlUnits.size() / maxConnectionsSizePerQuery + 1, 1);
        return Lists.partition(sqlUnits, desiredPartitionSize);
    }

    /**
     * 抽象方法，由子类实现构建 ExecutionGroup（实际创建语句执行对象）
     */
    protected abstract List<ExecutionGroup<T>> group(String dataSourceName, List<List<SQLUnit>> sqlUnitGroups, ConnectionMode connectionMode) throws SQLException;

    /**
     * 按数据源名将 ExecutionUnit 中的 SQLUnit 分组。
     */
    private Map<String, List<SQLUnit>> aggregateSQLUnitGroups(final Collection<ExecutionUnit> executionUnits) {
        Map<String, List<SQLUnit>> result = new LinkedHashMap<>(executionUnits.size(), 1);
        for (ExecutionUnit each : executionUnits) {
            if (!result.containsKey(each.getDataSourceName())) {
                result.put(each.getDataSourceName(), new LinkedList<>());
            }
            result.get(each.getDataSourceName()).add(each.getSqlUnit());
        }
        return result;
    }

    /**
     * 应用所有装饰器对执行组进行加工（例如 HintDecorator、事务控制等）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ExecutionGroupContext<T> decorate(final RouteContext routeContext, final Collection<ExecutionGroup<T>> executionGroups) {
        Collection<ExecutionGroup<T>> result = executionGroups;
        for (Entry<ShardingSphereRule, ExecutionPrepareDecorator> each : decorators.entrySet()) {
            result = each.getValue().decorate(routeContext, each.getKey(), result);
        }
        return new ExecutionGroupContext(result);
    }
}
