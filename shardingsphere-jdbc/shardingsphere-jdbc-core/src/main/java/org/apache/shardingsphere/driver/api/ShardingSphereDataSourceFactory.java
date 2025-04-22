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

package org.apache.shardingsphere.driver.api;

import com.google.common.base.Strings;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.infra.config.RuleConfiguration;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.database.DefaultSchema;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * ShardingSphere 数据源工厂类。
 * 提供多种方式创建支持分片、读写分离等功能的 ShardingSphere 数据源。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShardingSphereDataSourceFactory {

    /**
     * 根据 schema 名称和模式配置创建数据源。
     *
     * @param schemaName 数据库逻辑名称（可为空）
     * @param modeConfig 模式配置（Standalone, Cluster, Memory 等）
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final String schemaName, final ModeConfiguration modeConfig) throws SQLException {
        return new ShardingSphereDataSource(Strings.isNullOrEmpty(schemaName) ? DefaultSchema.LOGIC_NAME : schemaName, modeConfig);
    }

    /**
     * 根据模式配置创建默认逻辑 schema 的数据源。
     *
     * @param modeConfig 模式配置
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final ModeConfiguration modeConfig) throws SQLException {
        return createDataSource(DefaultSchema.LOGIC_NAME, modeConfig);
    }

    /**
     * 创建包含分片规则等配置的数据源。
     *
     * @param schemaName 数据库逻辑名称
     * @param modeConfig 模式配置
     * @param dataSourceMap 数据源映射（key 为数据源名称）
     * @param configs 规则配置集合（如分片、读写分离、影子库等）
     * @param props 配置属性（用于自定义行为）
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final String schemaName, final ModeConfiguration modeConfig,
                                              final Map<String, DataSource> dataSourceMap, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return new ShardingSphereDataSource(Strings.isNullOrEmpty(schemaName) ? DefaultSchema.LOGIC_NAME : schemaName, modeConfig, dataSourceMap, configs, props);
    }

    /**
     * 创建包含配置的默认 schema 数据源。
     *
     * @param modeConfig 模式配置
     * @param dataSourceMap 数据源映射
     * @param configs 规则配置集合
     * @param props 配置属性
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final ModeConfiguration modeConfig,
                                              final Map<String, DataSource> dataSourceMap, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return createDataSource(DefaultSchema.LOGIC_NAME, modeConfig, dataSourceMap, configs, props);
    }

    /**
     * 根据单个数据源创建带有规则配置的数据源。
     *
     * @param schemaName 数据库逻辑名称
     * @param modeConfig 模式配置
     * @param dataSource 单个数据源
     * @param configs 规则配置集合
     * @param props 配置属性
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final String schemaName, final ModeConfiguration modeConfig,
                                              final DataSource dataSource, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return createDataSource(schemaName, modeConfig, Collections.singletonMap(Strings.isNullOrEmpty(schemaName) ? DefaultSchema.LOGIC_NAME : schemaName, dataSource), configs, props);
    }

    /**
     * 根据单个数据源创建默认 schema 的数据源。
     *
     * @param modeConfig 模式配置
     * @param dataSource 单个数据源
     * @param configs 规则配置集合
     * @param props 配置属性
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final ModeConfiguration modeConfig,
                                              final DataSource dataSource, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return createDataSource(modeConfig, Collections.singletonMap(DefaultSchema.LOGIC_NAME, dataSource), configs, props);
    }

    /**
     * 不使用模式配置，仅使用 schema 和配置创建数据源。
     *
     * @param schemaName 数据库逻辑名称
     * @param dataSourceMap 数据源映射
     * @param configs 规则配置集合
     * @param props 配置属性
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final String schemaName,
                                              final Map<String, DataSource> dataSourceMap, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return createDataSource(schemaName, null, dataSourceMap, configs, props);
    }

    /**
     * 不使用 schema 和模式配置，仅使用数据源映射和规则创建数据源。
     *
     * @param dataSourceMap 数据源映射
     * @param configs 规则配置集合
     * @param props 配置属性
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final Map<String, DataSource> dataSourceMap, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return createDataSource((ModeConfiguration) null, dataSourceMap, configs, props);
    }

    /**
     * 使用 schema 名称和单个数据源创建数据源。
     *
     * @param schemaName 数据库逻辑名称
     * @param dataSource 单个数据源
     * @param configs 规则配置集合
     * @param props 配置属性
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final String schemaName, final DataSource dataSource, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return createDataSource(schemaName, null, dataSource, configs, props);
    }

    /**
     * 使用单个数据源创建默认 schema 的数据源。
     *
     * @param dataSource 单个数据源
     * @param configs 规则配置集合
     * @param props 配置属性
     * @return ShardingSphere 数据源
     * @throws SQLException SQL 异常
     */
    public static DataSource createDataSource(final DataSource dataSource, final Collection<RuleConfiguration> configs, final Properties props) throws SQLException {
        return createDataSource((ModeConfiguration) null, dataSource, configs, props);
    }
}
