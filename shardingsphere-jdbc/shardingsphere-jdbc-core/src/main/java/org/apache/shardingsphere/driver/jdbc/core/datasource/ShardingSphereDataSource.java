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

package org.apache.shardingsphere.driver.jdbc.core.datasource;

import lombok.Getter;
import org.apache.shardingsphere.driver.jdbc.adapter.AbstractDataSourceAdapter;
import org.apache.shardingsphere.driver.state.DriverStateContext;
import org.apache.shardingsphere.infra.config.RuleConfiguration;
import org.apache.shardingsphere.infra.config.checker.RuleConfigurationCheckerFactory;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.schema.impl.DataSourceProvidedSchemaConfiguration;
import org.apache.shardingsphere.infra.config.scope.GlobalRuleConfiguration;
import org.apache.shardingsphere.infra.instance.definition.InstanceDefinition;
import org.apache.shardingsphere.infra.instance.definition.InstanceType;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.manager.ContextManagerBuilderFactory;
import org.apache.shardingsphere.mode.manager.ContextManagerBuilderParameter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
/**
 * ShardingSphere 数据源实现类，用于支持分库分表、读写分离、分布式事务等功能。
 * 是 JDBC 应用连接 ShardingSphere 的统一入口，底层使用 ContextManager 进行全局管理。
 */
@Getter
public final class ShardingSphereDataSource extends AbstractDataSourceAdapter implements AutoCloseable {

    /**
     * 当前数据源所属逻辑 schema 名称（逻辑数据库名）。
     */
    private final String schemaName;
    /**
     * 分库分表运行时上下文对象，内部包含元数据管理、规则引擎、事务控制等核心功能。
     */
    private final ContextManager contextManager;

    /**
     * 构造函数 - 仅传入 schema 名称和模式配置，使用空的数据源和规则集合进行初始化。
     *
     * @param schemaName 当前逻辑库名称
     * @param modeConfig 模式配置，包含本地模式、集群模式等配置内容
     * @throws SQLException 初始化失败时抛出
     */
    public ShardingSphereDataSource(final String schemaName, final ModeConfiguration modeConfig) throws SQLException {
        this.schemaName = schemaName;
        // 初始化上下文管理器（使用空数据源和空规则）
        contextManager = createContextManager(schemaName, modeConfig, new HashMap<>(), new LinkedList<>(), new Properties());
    }

    /**
     * 构造函数 - 传入完整配置：数据源、规则、属性
     *
     * @param schemaName 当前逻辑库名称
     * @param modeConfig 模式配置
     * @param dataSourceMap 数据源映射（key 是数据源逻辑名称，value 是对应的 DataSource 对象）
     * @param ruleConfigs 分库分表、读写分离等规则配置集合
     * @param props 其他属性配置，如 SQL 显示、执行引擎线程池等
     * @throws SQLException 初始化失败时抛出
     */
    public ShardingSphereDataSource(final String schemaName, final ModeConfiguration modeConfig, final Map<String, DataSource> dataSourceMap,
                                    final Collection<RuleConfiguration> ruleConfigs, final Properties props) throws SQLException {
        // 检查规则合法性（比如表路由规则、分片算法是否正确）
        checkRuleConfiguration(schemaName, ruleConfigs);
        this.schemaName = schemaName;
        // 初始化上下文管理器
        contextManager = createContextManager(schemaName, modeConfig, dataSourceMap, ruleConfigs, null == props ? new Properties() : props);
    }
    /**
     * 检查规则配置是否合法。
     * 每个规则（如 ShardingRuleConfiguration、ReadwriteSplittingRuleConfiguration）都应有对应的 Checker 实现。
     *
     * @param schemaName 当前逻辑库名称
     * @param ruleConfigs 配置集合
     */
    @SuppressWarnings("unchecked")
    private void checkRuleConfiguration(final String schemaName, final Collection<RuleConfiguration> ruleConfigs) {
        ruleConfigs.forEach(each -> RuleConfigurationCheckerFactory.newInstance(each).ifPresent(optional -> optional.check(schemaName, each)));
    }
    /**
     * 创建上下文管理器 ContextManager，是整个 ShardingSphere 的核心运行容器。
     *
     * @param schemaName 当前逻辑库名称
     * @param modeConfig 模式配置
     * @param dataSourceMap 数据源集合
     * @param ruleConfigs 分片规则
     * @param props 属性参数
     * @return ContextManager 上下文对象
     * @throws SQLException 初始化失败时抛出
     */
    private ContextManager createContextManager(final String schemaName, final ModeConfiguration modeConfig, final Map<String, DataSource> dataSourceMap,
                                                final Collection<RuleConfiguration> ruleConfigs, final Properties props) throws SQLException {
        // 过滤出全局规则（如权限、系统变量等）
        Collection<RuleConfiguration> globalRuleConfigs = ruleConfigs.stream().filter(each -> each instanceof GlobalRuleConfiguration).collect(Collectors.toList());
        // 构建上下文参数对象，用于传递给 ContextManagerBuilder
        ContextManagerBuilderParameter parameter = ContextManagerBuilderParameter.builder()
                .modeConfig(modeConfig) // 模式配置，如 Standalone、Cluster 等
                .schemaConfigs(Collections.singletonMap(schemaName, new DataSourceProvidedSchemaConfiguration(dataSourceMap, ruleConfigs))) // 指定 schema 的数据源和规则配置
                .globalRuleConfigs(globalRuleConfigs)// 全局规则配置
                .props(props)// 属性配置
                .instanceDefinition(new InstanceDefinition(InstanceType.JDBC)).build();
        // 构建上下文管理器
        return ContextManagerBuilderFactory.newInstance(modeConfig).build(parameter);
    }

    @Override
    public Connection getConnection() {
        return DriverStateContext.getConnection(schemaName, contextManager);
    }

    @Override
    public Connection getConnection(final String username, final String password) {
        return getConnection();
    }

    /**
     * Close data sources.
     *
     * @param dataSourceNames data source names to be closed
     * @throws Exception exception
     */
    public void close(final Collection<String> dataSourceNames) throws Exception {
        Map<String, DataSource> dataSourceMap = contextManager.getDataSourceMap(schemaName);
        for (String each : dataSourceNames) {
            close(dataSourceMap.get(each));
        }
        contextManager.close();
    }

    private void close(final DataSource dataSource) throws Exception {
        if (dataSource instanceof AutoCloseable) {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Override
    public void close() throws Exception {
        close(contextManager.getDataSourceMap(schemaName).keySet());
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        Map<String, DataSource> dataSourceMap = contextManager.getDataSourceMap(schemaName);
        return dataSourceMap.isEmpty() ? 0 : dataSourceMap.values().iterator().next().getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        for (DataSource each : contextManager.getDataSourceMap(schemaName).values()) {
            each.setLoginTimeout(seconds);
        }
    }
}
