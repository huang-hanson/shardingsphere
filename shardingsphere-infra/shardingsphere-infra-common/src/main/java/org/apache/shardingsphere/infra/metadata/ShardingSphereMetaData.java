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

package org.apache.shardingsphere.infra.metadata;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.config.schema.SchemaConfiguration;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.database.type.DatabaseTypeRecognizer;
import org.apache.shardingsphere.infra.metadata.resource.CachedDatabaseMetaData;
import org.apache.shardingsphere.infra.metadata.resource.DataSourcesMetaData;
import org.apache.shardingsphere.infra.metadata.resource.ShardingSphereResource;
import org.apache.shardingsphere.infra.metadata.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * ShardingSphere 元数据类。
 * 该类封装了某个逻辑数据库（逻辑库）的资源、规则、模式（schema）等元信息，
 * 用于支持分库分表、读写分离、数据加密等功能。
 */
@RequiredArgsConstructor
@Getter
public final class ShardingSphereMetaData {
    /**
     * 逻辑数据库名称，对应 ShardingSphere 配置的逻辑库名。
     */
    private final String name;
    /**
     * 数据源属性
     * 数据源及元数据资源封装类，包含了数据源、数据源元数据、数据库类型等。
     */
    private final ShardingSphereResource resource;
    /**
     * ShardingSphere 中的规则元数据（例如：分片规则、加密规则、读写分离规则等）
     */
    private final ShardingSphereRuleMetaData ruleMetaData;
    /**
     * 数据库表属性
     * 所有 schema 的映射，key 是 schema 名称，value 是对应的 ShardingSphereSchema 对象。
     */
    private final Map<String, ShardingSphereSchema> schemas;

    /**
     * 创建 ShardingSphere 元数据。
     *
     * @param databaseName         逻辑数据库名
     * @param schemas              schema 映射
     * @param schemaConfig         schema 配置（包含规则配置、数据源配置等）
     * @param rules                规则实现类集合
     * @param defaultDatabaseType  默认数据库类型（当数据源为空时使用）
     * @return 构造好的 ShardingSphereMetaData 实例
     * @throws SQLException 获取数据库连接元信息时可能抛出异常
     */
    public static ShardingSphereMetaData create(final String databaseName, final Map<String, ShardingSphereSchema> schemas, final SchemaConfiguration schemaConfig, 
                                                final Collection<ShardingSphereRule> rules, final DatabaseType defaultDatabaseType) throws SQLException {
        // 创建资源信息
        ShardingSphereResource resource = createResource(schemaConfig.getDataSources(), defaultDatabaseType);
        // 创建规则元数据对象
        ShardingSphereRuleMetaData ruleMetaData = new ShardingSphereRuleMetaData(schemaConfig.getRuleConfigurations(), rules);
        return new ShardingSphereMetaData(databaseName, resource, ruleMetaData, schemas);
    }
    /**
     * 构造 ShardingSphereResource 对象。
     *
     * @param dataSourceMap        数据源映射
     * @param defaultDatabaseType  默认数据库类型
     * @return 资源对象
     * @throws SQLException 数据库连接异常
     */
    private static ShardingSphereResource createResource(final Map<String, DataSource> dataSourceMap, final DatabaseType defaultDatabaseType) throws SQLException {
        // 如果数据源为空则使用默认数据库类型，否则识别数据源类型
        DatabaseType databaseType = dataSourceMap.isEmpty() ? defaultDatabaseType : DatabaseTypeRecognizer.getDatabaseType(dataSourceMap.values());
        // 构建数据源元数据
        DataSourcesMetaData dataSourcesMetaData = new DataSourcesMetaData(databaseType, dataSourceMap);
        // 缓存的数据库元信息（DatabaseMetaData）
        CachedDatabaseMetaData cachedDatabaseMetaData = createCachedDatabaseMetaData(dataSourceMap).orElse(null);
        return new ShardingSphereResource(dataSourceMap, dataSourcesMetaData, cachedDatabaseMetaData, databaseType);
    }
    /**
     * 构造缓存的数据库元数据信息。
     *
     * @param dataSources 数据源映射
     * @return Optional 包装的 CachedDatabaseMetaData
     * @throws SQLException 获取连接或元数据失败
     */
    private static Optional<CachedDatabaseMetaData> createCachedDatabaseMetaData(final Map<String, DataSource> dataSources) throws SQLException {
        if (dataSources.isEmpty()) {
            return Optional.empty();
        }
        // 取任意一个数据源的连接进行元数据获取
        try (Connection connection = dataSources.values().iterator().next().getConnection()) {
            return Optional.of(new CachedDatabaseMetaData(connection.getMetaData()));
        }
    }

    /**
     * 判断元数据是否完整。
     * 需同时包含规则和数据源信息。
     *
     * @return 是否完整
     */
    public boolean isComplete() {
        return !ruleMetaData.getRules().isEmpty() && !resource.getDataSources().isEmpty();
    }

    /**
     * 判断是否存在数据源。
     *
     * @return 是否有数据源
     */
    public boolean hasDataSource() {
        return !resource.getDataSources().isEmpty();
    }

    /**
     * 获取默认 schema。
     * 默认以逻辑库名称作为默认 schema 的名称。
     *
     * @return 默认的 ShardingSphereSchema
     */
    public ShardingSphereSchema getDefaultSchema() {
        return schemas.get(name);
    }

    /**
     * 根据 schema 名称获取对应的 schema。
     *
     * @param schemaName schema 名
     * @return ShardingSphereSchema 对象
     */
    public ShardingSphereSchema getSchemaByName(final String schemaName) {
        return schemas.get(schemaName);
    }
}
