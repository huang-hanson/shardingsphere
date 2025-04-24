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

package org.apache.shardingsphere.infra.metadata.schema;

import lombok.Getter;
import org.apache.shardingsphere.infra.metadata.schema.model.TableMetaData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ShardingSphere 的逻辑 Schema 对象，封装了当前逻辑库中所有表的元数据信息。
 */
@Getter
public final class ShardingSphereSchema {
    // 存储表名与其对应的元数据信息（键为小写表名）
    private final Map<String, TableMetaData> tables;
    /**
     * 默认构造函数，初始化空的表结构集合。
     */
    @SuppressWarnings("CollectionWithoutInitialCapacity")
    public ShardingSphereSchema() {
        tables = new ConcurrentHashMap<>();
    }
    /**
     * 通过已有表结构 Map 构造 Schema。
     * 会将所有表名转换为小写，以便忽略大小写敏感。
     *
     * @param tables 表结构信息映射
     */
    public ShardingSphereSchema(final Map<String, TableMetaData> tables) {
        this.tables = new ConcurrentHashMap<>(tables.size(), 1);
        tables.forEach((key, value) -> this.tables.put(key.toLowerCase(), value));
    }

    /**
     * 获取所有的表名集合。
     *
     * @return 所有表名（小写）
     */
    public Collection<String> getAllTableNames() {
        return tables.keySet();
    }

    /**
     * 根据表名获取表的元数据信息。
     *
     * @param tableName 表名
     * @return 表的元数据（TableMetaData）
     */
    public TableMetaData get(final String tableName) {
        return tables.get(tableName.toLowerCase());
    }

    /**
     * 添加一张表的元数据。
     *
     * @param tableName 表名
     * @param tableMetaData 表的元数据
     */
    public void put(final String tableName, final TableMetaData tableMetaData) {
        tables.put(tableName.toLowerCase(), tableMetaData);
    }

    /**
     * 批量添加多个表的元数据。
     *
     * @param tableMetaDataMap 多个表名与其元数据的映射
     */
    public void putAll(final Map<String, TableMetaData> tableMetaDataMap) {
        for (Entry<String, TableMetaData> entry : tableMetaDataMap.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 移除某个表的元数据。
     *
     * @param tableName 表名
     */
    public void remove(final String tableName) {
        tables.remove(tableName.toLowerCase());
    }

    /**
     * 判断是否包含某张表的元数据。
     *
     * @param tableName 表名
     * @return 是否存在
     */
    public boolean containsTable(final String tableName) {
        return tables.containsKey(tableName.toLowerCase());
    }

    /**
     * 判断某张表是否包含指定列。
     *
     * @param tableName 表名
     * @param columnName 列名
     * @return 是否存在该列
     */
    public boolean containsColumn(final String tableName, final String columnName) {
        return containsTable(tableName) && get(tableName).getColumns().containsKey(columnName.toLowerCase());
    }

    /**
     * 获取指定表的所有列名。
     *
     * @param tableName 表名
     * @return 列名列表；若表不存在则返回空列表
     */
    public List<String> getAllColumnNames(final String tableName) {
        return containsTable(tableName) ? get(tableName).getColumnNames() : Collections.emptyList();
    }
}
