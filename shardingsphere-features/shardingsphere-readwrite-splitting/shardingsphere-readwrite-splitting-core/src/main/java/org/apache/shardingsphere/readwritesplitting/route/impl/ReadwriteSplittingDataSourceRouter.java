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

package org.apache.shardingsphere.readwritesplitting.route.impl;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.binder.statement.CommonSQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.apache.shardingsphere.transaction.TransactionHolder;
import org.apache.shardingsphere.readwritesplitting.rule.ReadwriteSplittingDataSourceRule;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.SelectStatement;
import org.apache.shardingsphere.sql.parser.sql.dialect.handler.dml.SelectStatementHandler;

/**
 * 读写分离数据源路由器，负责根据SQL类型（读/写）决定路由到主库或从库。
 * 核心逻辑：写操作和特定读操作（如加锁查询）走主库，其他读操作走从库（支持负载均衡）。
 */
@RequiredArgsConstructor
public final class ReadwriteSplittingDataSourceRouter {
    
    private final ReadwriteSplittingDataSourceRule rule;// 读写分离规则（包含主从库配置）

    /**
     * 路由到合适的数据源（主库或从库）。
     *
     * @param sqlStatementContext SQL语句上下文（包含SQL类型、表信息等）
     * @return 目标数据源名称
     */
    public String route(final SQLStatementContext<?> sqlStatementContext) {
        // 1. 判断是否需要强制走主库
        if (isPrimaryRoute(sqlStatementContext)) {
            return rule.getReadwriteSplittingType().getWriteDataSource();
        }
        // 2. 如果只有一个从库，直接返回（无需负载均衡）
        if (1 == rule.getReadDataSourceNames().size()) {
            return rule.getReadDataSourceNames().get(0);
        }
        // 3. 多个从库时，通过负载均衡算法选择
        return rule.getLoadBalancer().getDataSource(
                rule.getName(),                 // 规则名称（用于负载均衡标识）
                rule.getWriteDataSource(),      // 主库名称（通常作为负载均衡备用）
                rule.getReadDataSourceNames()   // 从库列表
        );
    }

    /**
     * 判断当前SQL是否需要强制路由到主库。
     * 以下情况会走主库：
     * 1. SQL包含锁（如SELECT FOR UPDATE）
     * 2. 非SELECT语句（INSERT/UPDATE/DELETE等）
     * 3. 通过Hint强制指定走主库（如HintManager.writeRouteOnly()）
     * 4. 当前处于事务中（保证读写一致性）
     */
    private boolean isPrimaryRoute(final SQLStatementContext<?> sqlStatementContext) {
        SQLStatement sqlStatement = sqlStatementContext.getSqlStatement();
        return containsLockSegment(sqlStatement) || !(sqlStatement instanceof SelectStatement) || isHintWriteRouteOnly(sqlStatementContext) || TransactionHolder.isTransaction();
    }

    /**
     * 检查是否通过Hint强制指定走主库。
     * Hint优先级最高，覆盖其他规则。
     */
    private boolean isHintWriteRouteOnly(final SQLStatementContext<?> sqlStatementContext) {
        return HintManager.isWriteRouteOnly() || (sqlStatementContext instanceof CommonSQLStatementContext && ((CommonSQLStatementContext<?>) sqlStatementContext).isHintWriteRouteOnly());
    }

    /**
     * 检查SQL是否包含锁段（如SELECT ... FOR UPDATE）。
     */
    private boolean containsLockSegment(final SQLStatement sqlStatement) {
        return sqlStatement instanceof SelectStatement && SelectStatementHandler.getLockSegment((SelectStatement) sqlStatement).isPresent();
    }
}
