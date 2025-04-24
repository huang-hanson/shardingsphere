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

package org.apache.shardingsphere.infra.metadata.schema.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TableMetaData 表示一张逻辑表的元数据信息。
 * 包含字段（columns）、索引（indexes）、约束（constraints）等结构信息。
 */
@Getter
@EqualsAndHashCode
@ToString
public final class TableMetaData {
    // 表名
    private final String name;
    // 列信息，key 为列名（小写），value 为 ColumnMetaData 对象
    private final Map<String, ColumnMetaData> columns;
    // 索引信息，key 为索引名（小写），value 为 IndexMetaData 对象
    private final Map<String, IndexMetaData> indexes;
    // 表约束信息（如主键、唯一约束等），key 为约束名（小写），value 为 ConstraintMetaData 对象
    private final Map<String, ConstraintMetaData> constrains;
    // 按顺序记录所有列的名称（原始大小写）
    private final List<String> columnNames = new ArrayList<>();
    // 主键列名称列表（小写）
    private final List<String> primaryKeyColumns = new ArrayList<>();
    /**
     * 默认构造函数，构造一个空表结构。
     */
    public TableMetaData() {
        this("", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }
    /**
     * 构造函数，创建表结构元数据。
     *
     * @param name 表名
     * @param columnMetaDataList 列元数据集合
     * @param indexMetaDataList 索引元数据集合
     * @param constraintMetaDataList 约束元数据集合
     */
    public TableMetaData(final String name, final Collection<ColumnMetaData> columnMetaDataList, 
                         final Collection<IndexMetaData> indexMetaDataList, final Collection<ConstraintMetaData> constraintMetaDataList) {
        this.name = name;
        columns = getColumns(columnMetaDataList);
        indexes = getIndexes(indexMetaDataList);
        constrains = getConstrains(constraintMetaDataList);
    }
    /**
     * 将列集合转换为 Map，记录列元数据，并填充 columnNames 和 primaryKeyColumns。
     *
     * @param columnMetaDataList 列元数据集合
     * @return 列名到列元数据的映射
     */
    private Map<String, ColumnMetaData> getColumns(final Collection<ColumnMetaData> columnMetaDataList) {
        Map<String, ColumnMetaData> result = new LinkedHashMap<>(columnMetaDataList.size(), 1);
        for (ColumnMetaData each : columnMetaDataList) {
            String lowerColumnName = each.getName().toLowerCase();
            result.put(lowerColumnName, each);
            columnNames.add(each.getName());
            if (each.isPrimaryKey()) {
                primaryKeyColumns.add(lowerColumnName);
            }
        }
        return result;
    }
    /**
     * 将索引集合转换为 Map。
     *
     * @param indexMetaDataList 索引元数据集合
     * @return 索引名到索引元数据的映射
     */
    private Map<String, IndexMetaData> getIndexes(final Collection<IndexMetaData> indexMetaDataList) {
        Map<String, IndexMetaData> result = new LinkedHashMap<>(indexMetaDataList.size(), 1);
        for (IndexMetaData each : indexMetaDataList) {
            result.put(each.getName().toLowerCase(), each);
        }
        return result;
    }
    /**
     * 将约束集合转换为 Map。
     *
     * @param constraintMetaDataList 约束元数据集合
     * @return 约束名到约束元数据的映射
     */
    private Map<String, ConstraintMetaData> getConstrains(final Collection<ConstraintMetaData> constraintMetaDataList) {
        Map<String, ConstraintMetaData> result = new LinkedHashMap<>(constraintMetaDataList.size(), 1);
        for (ConstraintMetaData each : constraintMetaDataList) {
            result.put(each.getName().toLowerCase(), each);
        }
        return result;
    }
}
