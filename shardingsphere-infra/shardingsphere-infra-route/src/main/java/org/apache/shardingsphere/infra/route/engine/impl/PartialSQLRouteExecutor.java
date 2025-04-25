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

package org.apache.shardingsphere.infra.route.engine.impl;

import org.apache.shardingsphere.infra.binder.LogicSQL;
import org.apache.shardingsphere.infra.binder.statement.CommonSQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.route.SQLRouter;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.context.RouteMapper;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.infra.route.engine.SQLRouteExecutor;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.spi.ordered.OrderedSPIRegistry;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Partial SQL 路由执行器。
 *
 * 此执行器用于在部分 schema（或数据库实例）中选择路由目标。
 * 与 AllSQLRouteExecutor（广播路由）不同，此类会根据配置规则精确选择目标数据源。
 */
public final class PartialSQLRouteExecutor implements SQLRouteExecutor {

    // 静态块：注册 SQLRouter SPI 实现类（用于后续按需加载）
    static {
        ShardingSphereServiceLoader.register(SQLRouter.class);
    }
    
    private final ConfigurationProperties props;

    /**
     * 每个规则（ShardingSphereRule）对应一个 SQLRouter 实例。
     * SQLRouter 是实际执行路由逻辑的策略接口。
     */
    @SuppressWarnings("rawtypes")
    private final Map<ShardingSphereRule, SQLRouter> routers;

    /**
     * 构造函数，初始化属性和注册的路由器。
     *
     * @param rules 路由规则集合（分片、读写分离、影子库等）
     * @param props 配置属性
     */
    public PartialSQLRouteExecutor(final Collection<ShardingSphereRule> rules, final ConfigurationProperties props) {
        this.props = props;
        routers = OrderedSPIRegistry.getRegisteredServices(SQLRouter.class, rules);
    }

    /**
     * 执行路由逻辑，生成 RouteContext。
     *
     * @param logicSQL 解析后的逻辑 SQL
     * @param metaData 当前数据库的元数据
     * @return 路由上下文
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RouteContext route(final LogicSQL logicSQL, final ShardingSphereMetaData metaData) {
        // 初始化空的 RouteContext，用于收集路由单元
        RouteContext result = new RouteContext();

        // 1. 优先判断 Hint 强制指定的数据源（如 HintManager.setDataSourceName）
        Optional<String> dataSourceName = findDataSourceByHint(logicSQL.getSqlStatementContext(), metaData.getResource().getDataSources());
        if (dataSourceName.isPresent()) {
            // 如果通过 Hint 指定了数据源，直接构造 RouteUnit 返回
            result.getRouteUnits().add(new RouteUnit(new RouteMapper(dataSourceName.get(), dataSourceName.get()), Collections.emptyList()));
            return result;
        }

        // 2. 根据配置规则迭代执行每个 SQLRouter
        for (Entry<ShardingSphereRule, SQLRouter> entry : routers.entrySet()) {
            if (result.getRouteUnits().isEmpty()) {
                // 第一个路由器执行 createRouteContext
                result = entry.getValue().createRouteContext(logicSQL, metaData, entry.getKey(), props);
            } else {
                // 后续路由器执行 decorateRouteContext 进行补充或装饰
                entry.getValue().decorateRouteContext(result, logicSQL, metaData, entry.getKey(), props);
            }
        }
        // 3. 如果所有路由器都没匹配上，并且系统只配置了一个数据源，则默认选这个数据源
        if (result.getRouteUnits().isEmpty() && 1 == metaData.getResource().getDataSources().size()) {
            String singleDataSourceName = metaData.getResource().getDataSources().keySet().iterator().next();
            result.getRouteUnits().add(new RouteUnit(new RouteMapper(singleDataSourceName, singleDataSourceName), Collections.emptyList()));
        }
        return result;
    }

    /**
     * 尝试从 Hint 中获取用户强制指定的数据源。
     * 如果存在 Hint 并有效（即在当前资源中存在对应的 datasource），则返回。
     * 否则抛出异常或返回空。
     *
     * @param sqlStatementContext SQL 上下文
     * @param dataSources 当前所有的数据源
     * @return Optional 类型的数据源名
     */
    private Optional<String> findDataSourceByHint(final SQLStatementContext<?> sqlStatementContext, final Map<String, DataSource> dataSources) {
        Optional<String> result;

        // 1. 先判断是否通过 HintManager 显式指定数据源
        if (HintManager.isInstantiated() && HintManager.getDataSourceName().isPresent()) {
            result = HintManager.getDataSourceName();
        } else {
            // 2. 如果 HintManager 没有指定，则尝试从 SQL 语句上下文中找（如 SQL 注释中含有 Hint）
            result = ((CommonSQLStatementContext<?>) sqlStatementContext).findHintDataSourceName();
        }
        // 3. 校验该数据源是否真实存在于当前数据源中
        if (result.isPresent() && !dataSources.containsKey(result.get())) {
            throw new ShardingSphereException("Hint datasource: %s is not exist!", result.get());
        }
        return result;
    }
}
