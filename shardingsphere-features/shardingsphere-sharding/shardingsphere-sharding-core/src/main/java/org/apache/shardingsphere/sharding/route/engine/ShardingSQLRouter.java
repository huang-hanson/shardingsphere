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

package org.apache.shardingsphere.sharding.route.engine;

import org.apache.shardingsphere.infra.binder.LogicSQL;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.route.SQLRouter;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.sharding.constant.ShardingOrder;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingCondition;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingConditions;
import org.apache.shardingsphere.sharding.route.engine.condition.engine.ShardingConditionEngine;
import org.apache.shardingsphere.sharding.route.engine.condition.engine.ShardingConditionEngineFactory;
import org.apache.shardingsphere.sharding.route.engine.type.ShardingRouteEngineFactory;
import org.apache.shardingsphere.sharding.route.engine.validator.ShardingStatementValidator;
import org.apache.shardingsphere.sharding.route.engine.validator.ShardingStatementValidatorFactory;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.DMLStatement;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 分片 SQL 路由器，负责根据分片规则将逻辑 SQL 路由到具体的数据库/表。
 */
public final class ShardingSQLRouter implements SQLRouter<ShardingRule> {

    /**
     * 构建路由上下文（核心逻辑）。
     *
     * @param logicSQL 解析后的逻辑 SQL
     * @param metaData 当前元数据（库表结构、数据源信息等）
     * @param rule 分片规则
     * @param props 配置属性
     * @return 路由上下文，包含路由结果信息
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public RouteContext createRouteContext(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ShardingRule rule, final ConfigurationProperties props) {
        // 原始 SQL 语句
        SQLStatement sqlStatement = logicSQL.getSqlStatementContext().getSqlStatement();
        // 生成分片条件，例如 where user_id = 10001 -> 用于计算路由
        ShardingConditions shardingConditions = createShardingConditions(logicSQL, metaData, rule);
        // 校验器：用于 DML/DDL 的分片验证
        Optional<ShardingStatementValidator> validator = ShardingStatementValidatorFactory.newInstance(sqlStatement, shardingConditions);
        // 执行路由前的语义校验（如不支持多表更新等）
        validator.ifPresent(v -> v.preValidate(rule, logicSQL.getSqlStatementContext(), logicSQL.getParameters(), metaData.getDefaultSchema()));
        // 如果是 DML 且需要合并（例如批量 insert 多条记录中包含不同分片键），进行合并处理
        if (sqlStatement instanceof DMLStatement && shardingConditions.isNeedMerge()) {
            shardingConditions.merge();
        }
        // 创建具体的路由引擎并执行路由，返回路由结果（路由到哪个库哪个表）
        RouteContext result = ShardingRouteEngineFactory.newInstance(rule, metaData, logicSQL.getSqlStatementContext(), shardingConditions, props).route(rule);
        // 执行路由后的语义校验（如路由后结果是否合法等）
        validator.ifPresent(v -> v.postValidate(rule, logicSQL.getSqlStatementContext(), logicSQL.getParameters(), metaData.getDefaultSchema(), props, result));
        return result;
    }

    /**
     * 创建分片条件（ShardingConditions），用于路由时进行分片判断。
     *
     * @param logicSQL 当前逻辑 SQL
     * @param metaData 当前元数据
     * @param rule 分片规则
     * @return 分片条件集合
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ShardingConditions createShardingConditions(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ShardingRule rule) {
        List<ShardingCondition> shardingConditions;
        // 仅对 DML（如 INSERT/UPDATE/DELETE/SELECT）生成分片条件
        if (logicSQL.getSqlStatementContext().getSqlStatement() instanceof DMLStatement) {
            // 创建条件引擎
            ShardingConditionEngine shardingConditionEngine = ShardingConditionEngineFactory.createShardingConditionEngine(logicSQL, metaData, rule);
            // 从参数中提取出用于分片的条件（如 where user_id = ?）
            shardingConditions = shardingConditionEngine.createShardingConditions(logicSQL.getSqlStatementContext(), logicSQL.getParameters());
        } else {
            // 非 DML 不需要分片条件
            shardingConditions = Collections.emptyList();
        }
        return new ShardingConditions(shardingConditions, logicSQL.getSqlStatementContext(), rule);
    }
    
    @Override
    public void decorateRouteContext(final RouteContext routeContext, final LogicSQL logicSQL, final ShardingSphereMetaData metaData, 
                                     final ShardingRule rule, final ConfigurationProperties props) {
        // TODO
    }
    
    @Override
    public int getOrder() {
        return ShardingOrder.ORDER;
    }
    
    @Override
    public Class<ShardingRule> getTypeClass() {
        return ShardingRule.class;
    }
}
