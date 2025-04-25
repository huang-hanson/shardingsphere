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

package org.apache.shardingsphere.infra.rewrite;

import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.infra.rewrite.context.SQLRewriteContextDecorator;
import org.apache.shardingsphere.infra.rewrite.engine.GenericSQLRewriteEngine;
import org.apache.shardingsphere.infra.rewrite.engine.RouteSQLRewriteEngine;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteResult;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.spi.ordered.OrderedSPIRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * SQL 改写入口类，负责协调SQL改写过程。
 * 核心功能：
 * 1. 创建SQL改写上下文（SQLRewriteContext）
 * 2. 应用所有注册的改写装饰器（如分片、加密、读写分离等规则）
 * 3. 根据路由情况选择通用改写或路由改写引擎
 */
public final class SQLRewriteEntry {

    // 通过SPI机制加载所有SQLRewriteContextDecorator实现类
    static {
        ShardingSphereServiceLoader.register(SQLRewriteContextDecorator.class);
    }
    // 当前逻辑库名称
    private final String schemaName;
    // 库元数据（表结构、约束等）
    private final ShardingSphereSchema schema;
    // 全局配置属性
    private final ConfigurationProperties props;
    // 规则对应的改写装饰器映射
    @SuppressWarnings("rawtypes")
    private final Map<ShardingSphereRule, SQLRewriteContextDecorator> decorators;

    /**
     * 构造函数。
     *
     * @param schemaName 逻辑库名
     * @param schema 库元数据
     * @param props 配置属性
     * @param rules 当前生效的所有规则（分片、加密等）
     */
    public SQLRewriteEntry(final String schemaName, final ShardingSphereSchema schema, final ConfigurationProperties props, final Collection<ShardingSphereRule> rules) {
        this.schemaName = schemaName;
        this.schema = schema;
        this.props = props;
        // 获取所有已排序的SQLRewriteContextDecorator实例（按规则类型匹配）
        decorators = OrderedSPIRegistry.getRegisteredServices(SQLRewriteContextDecorator.class, rules);
    }

    /**
     * 执行SQL改写。
     *
     * @param sql 原始SQL
     * @param parameters SQL参数
     * @param sqlStatementContext SQL语句上下文（已解析的语法树）
     * @param routeContext 路由上下文（包含数据源和表路由信息）
     * @return SQL改写结果（可能包含多个路由单元的改写SQL）
     */
    public SQLRewriteResult rewrite(final String sql, final List<Object> parameters, final SQLStatementContext<?> sqlStatementContext, final RouteContext routeContext) {
        // 1. 创建改写上下文
        SQLRewriteContext sqlRewriteContext = createSQLRewriteContext(sql, parameters, sqlStatementContext, routeContext);

        // 2. 根据路由情况选择改写引擎
        return routeContext.getRouteUnits().isEmpty()// 无路由：通用改写
                ? new GenericSQLRewriteEngine().rewrite(sqlRewriteContext) : new RouteSQLRewriteEngine().rewrite(sqlRewriteContext, routeContext);// 有路由：基于路由单元改写
    }

    /**
     * 创建SQL改写上下文。
     * 包括：应用所有装饰器 + 生成SQL令牌（SQLToken）
     */
    private SQLRewriteContext createSQLRewriteContext(final String sql, final List<Object> parameters, final SQLStatementContext<?> sqlStatementContext, final RouteContext routeContext) {
        // 初始化上下文（包含原始SQL、参数、元数据等）
        SQLRewriteContext result = new SQLRewriteContext(schemaName, schema, sqlStatementContext, sql, parameters);
        // 应用所有注册的装饰器（如分片装饰器会添加分片条件）
        decorate(decorators, result, routeContext);
        // 生成最终的SQL令牌（用于后续实际改写）
        result.generateSQLTokens();
        return result;
    }

    /**
     * 应用所有装饰器到改写上下文。
     * 每个装饰器对应一种规则（如分片、加密等），按优先级顺序执行。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void decorate(final Map<ShardingSphereRule, SQLRewriteContextDecorator> decorators, final SQLRewriteContext sqlRewriteContext, final RouteContext routeContext) {
        for (Entry<ShardingSphereRule, SQLRewriteContextDecorator> entry : decorators.entrySet()) {
            // 每个装饰器根据对应规则修改上下文（如添加分片列条件）
            entry.getValue().decorate(entry.getKey(), props, sqlRewriteContext, routeContext);
        }
    }
}
