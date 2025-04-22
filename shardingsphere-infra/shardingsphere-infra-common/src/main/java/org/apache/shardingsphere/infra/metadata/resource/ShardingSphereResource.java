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

package org.apache.shardingsphere.infra.metadata.resource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.datasource.pool.destroyer.DataSourcePoolDestroyer;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * ShardingSphere 物理数据源资源管理器，统一管理所有底层数据库连接池及其元数据。
 * 1. 维护数据源的生命周期（创建、销毁）
 * 2. 提供数据库类型和元数据的快速访问
 * 3. 支持多实例模式（如读写分离的主从库）
 */
@RequiredArgsConstructor
@Getter
public final class ShardingSphereResource {
    /**
     * 数据源集合（Key为数据源名称，Value为物理数据源对象）
     * 示例：
     * {
     *   "ds_0": HikariDataSource@1234,
     *   "ds_1": DruidDataSource@5678
     * }
     */
    private final Map<String, DataSource> dataSources;
    /**
     * 数据源元数据，包含：
     * - 数据源URL、用户名等连接信息
     * - 实例分组（如主从库的读写分离组）
     */
    private final DataSourcesMetaData dataSourcesMetaData;
    /**
     * 缓存的数据库元数据，避免频繁访问数据库系统表：
     * - 表结构信息
     * - 索引信息
     * - 约束信息
     */
    private final CachedDatabaseMetaData cachedDatabaseMetaData;
    /**
     * 数据库类型（MySQL/Oracle/PostgreSQL等），用于：
     * - SQL方言适配
     * - 分页语法生成
     * - DDL语句转换
     */
    private final DatabaseType databaseType;

    /**
     * 获取所有实例级数据源（通常用于读写分离场景）
     *
     * @return 去重后的实例数据源集合（相同IP:PORT的多个库只返回一个连接池）
     */
    public Collection<DataSource> getAllInstanceDataSources() {
        return dataSources.entrySet().stream().filter(entry -> dataSourcesMetaData.getAllInstanceDataSourceNames().contains(entry.getKey())).map(Entry::getValue).collect(Collectors.toSet());
    }

    /**
     * 校验资源名称是否存在
     *
     * @param resourceNames 待校验的资源名称集合
     * @return 不存在的资源名称集合（用于配置校验）
     */
    public Collection<String> getNotExistedResources(final Collection<String> resourceNames) {
        return resourceNames.stream().filter(each -> !dataSources.containsKey(each)).collect(Collectors.toSet());
    }

    /**
     * 异步关闭数据源连接池（防止应用关闭时连接泄漏）
     *
     * @param dataSource 待关闭的数据源对象
     */
    public void close(final DataSource dataSource) {
        new DataSourcePoolDestroyer(dataSource).asyncDestroy();
    }
}
