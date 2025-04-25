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

package org.apache.shardingsphere.readwritesplitting.route;

import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.database.DefaultSchema;
import org.apache.shardingsphere.infra.route.SQLRouter;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.context.RouteMapper;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.binder.LogicSQL;
import org.apache.shardingsphere.readwritesplitting.route.impl.ReadwriteSplittingDataSourceRouter;
import org.apache.shardingsphere.readwritesplitting.constant.ReadwriteSplittingOrder;
import org.apache.shardingsphere.readwritesplitting.rule.ReadwriteSplittingDataSourceRule;
import org.apache.shardingsphere.readwritesplitting.rule.ReadwriteSplittingRule;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;

/**
 * 读写分离 SQL 路由器，负责根据 SQL 类型（读/写）路由到主库或从库。
 * 实现 SQLRouter 接口，专用于 ReadwriteSplittingRule 规则。
 */
public final class ReadwriteSplittingSQLRouter implements SQLRouter<ReadwriteSplittingRule> {

    /**
     * 创建初始路由上下文，确定逻辑数据源对应的物理数据源（主库或从库）。
     *
     * @param logicSQL  逻辑SQL对象，包含SQL语句、参数和解析后的SQLStatementContext
     * @param metaData  ShardingSphere元数据（如库表结构、规则配置）
     * @param rule      当前读写分离规则（如主从库配置）
     * @param props     全局配置属性
     * @return RouteContext 初始路由上下文，包含数据源映射关系
     */
    @Override
    public RouteContext createRouteContext(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ReadwriteSplittingRule rule, final ConfigurationProperties props) {
        RouteContext result = new RouteContext();
        // 使用读写分离数据源路由器选择主库或从库（根据SQL类型自动判断）
        String dataSourceName = new ReadwriteSplittingDataSourceRouter(rule.getSingleDataSourceRule()).route(logicSQL.getSqlStatementContext());
        // 构建路由单元：逻辑库名 -> 实际数据源名（如 logic_db -> master_db 或 slave_db）
        result.getRouteUnits().add(new RouteUnit(new RouteMapper(DefaultSchema.LOGIC_NAME, dataSourceName), Collections.emptyList()));
        return result;
    }

    /**
     * 装饰（修正）已有的路由上下文，处理动态主从切换场景。
     * 例如：当路由结果中的主库因故障切换时，需替换为新的主库。
     *
     * @param routeContext 已存在的路由上下文
     * @param logicSQL     逻辑SQL对象
     * @param metaData     元数据
     * @param rule         读写分离规则
     * @param props        配置属性
     */
    @Override
    public void decorateRouteContext(final RouteContext routeContext,
                                     final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ReadwriteSplittingRule rule, final ConfigurationProperties props) {
        Collection<RouteUnit> toBeRemoved = new LinkedList<>();
        Collection<RouteUnit> toBeAdded = new LinkedList<>();
        // 遍历现有路由单元，检查是否需要更新数据源映射
        for (RouteUnit each : routeContext.getRouteUnits()) {
            String dataSourceName = each.getDataSourceMapper().getLogicName();
            // 查找对应的读写分离规则配置
            Optional<ReadwriteSplittingDataSourceRule> dataSourceRule = rule.findDataSourceRule(dataSourceName);
            // 如果规则存在且当前实际数据源名与规则名一致
            if (dataSourceRule.isPresent() && dataSourceRule.get().getName().equalsIgnoreCase(each.getDataSourceMapper().getActualName())) {
                toBeRemoved.add(each);// 标记待移除
                // 重新路由获取最新数据源（如主库切换后返回新主库名）
                String actualDataSourceName = new ReadwriteSplittingDataSourceRouter(dataSourceRule.get()).route(logicSQL.getSqlStatementContext());
                // 创建新路由单元（保留原逻辑名和表映射）
                toBeAdded.add(new RouteUnit(new RouteMapper(each.getDataSourceMapper().getLogicName(), actualDataSourceName), each.getTableMappers()));
            }
        }
        // 批量更新路由上下文
        routeContext.getRouteUnits().removeAll(toBeRemoved);
        routeContext.getRouteUnits().addAll(toBeAdded);
    }
    
    @Override
    public int getOrder() {
        return ReadwriteSplittingOrder.ORDER;
    }
    
    @Override
    public Class<ReadwriteSplittingRule> getTypeClass() {
        return ReadwriteSplittingRule.class;
    }
}
