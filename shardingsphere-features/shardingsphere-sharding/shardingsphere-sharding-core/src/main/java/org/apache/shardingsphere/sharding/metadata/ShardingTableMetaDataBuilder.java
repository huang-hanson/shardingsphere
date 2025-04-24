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

package org.apache.shardingsphere.sharding.metadata;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.config.props.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.datanode.DataNode;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.infra.metadata.schema.builder.SchemaBuilderMaterials;
import org.apache.shardingsphere.infra.metadata.schema.loader.TableMetaDataLoaderEngine;
import org.apache.shardingsphere.infra.metadata.schema.loader.TableMetaDataLoaderMaterial;
import org.apache.shardingsphere.infra.metadata.schema.builder.spi.RuleBasedTableMetaDataBuilder;
import org.apache.shardingsphere.infra.metadata.schema.util.TableMetaDataUtil;
import org.apache.shardingsphere.infra.metadata.schema.model.ColumnMetaData;
import org.apache.shardingsphere.infra.metadata.schema.model.ConstraintMetaData;
import org.apache.shardingsphere.infra.metadata.schema.model.IndexMetaData;
import org.apache.shardingsphere.infra.metadata.schema.model.TableMetaData;
import org.apache.shardingsphere.sharding.constant.ShardingOrder;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.sharding.rule.TableRule;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ShardingTableMetaDataBuilder 类用于构建分片表的元数据，结合 ShardingRule 和表的元数据加载。
 * 该类实现了 RuleBasedTableMetaDataBuilder 接口，用于构建、修饰和验证表的元数据。
 */
public final class ShardingTableMetaDataBuilder implements RuleBasedTableMetaDataBuilder<ShardingRule> {
    
    @Override
    public Map<String, TableMetaData> load(final Collection<String> tableNames, final ShardingRule rule, final SchemaBuilderMaterials materials) throws SQLException {
        // 筛选出需要加载的表名，包含 ShardingRule 中的表规则或者广播表
        Collection<String> needLoadTables = tableNames.stream().filter(each -> rule.findTableRule(each).isPresent() || rule.isBroadcastTable(each)).collect(Collectors.toList());
        // 如果没有需要加载的表，直接返回空的 Map
        if (needLoadTables.isEmpty()) {
            return Collections.emptyMap();
        }
        // 判断是否启用表元数据检查
        boolean isCheckingMetaData = materials.getProps().getValue(ConfigurationPropertyKey.CHECK_TABLE_METADATA_ENABLED);
        // 获取加载表元数据的材料集合
        Collection<TableMetaDataLoaderMaterial> tableMetaDataLoaderMaterials = TableMetaDataUtil.getTableMetaDataLoadMaterial(needLoadTables, materials, isCheckingMetaData);
        // 如果没有需要加载的元数据材料，返回空 Map
        if (tableMetaDataLoaderMaterials.isEmpty()) {
            return Collections.emptyMap();
        }
        // 加载所有表的元数据
        Collection<TableMetaData> tableMetaDataList = TableMetaDataLoaderEngine.load(tableMetaDataLoaderMaterials, materials.getDatabaseType());
        // 如果启用了元数据检查，进行检查
        if (isCheckingMetaData) {
            checkTableMetaData(tableMetaDataList, rule);
        }
        // 返回表名与表元数据的映射
        return getTableMetaDataMap(tableMetaDataList, rule);
    }

    @Override
    public Map<String, TableMetaData> decorate(final Map<String, TableMetaData> tableMetaDataMap, final ShardingRule rule, final SchemaBuilderMaterials materials) {
        // 对每个表元数据进行修饰，返回修饰后的元数据映射
        Map<String, TableMetaData> result = new LinkedHashMap<>();
        for (Entry<String, TableMetaData> entry : tableMetaDataMap.entrySet()) {
            result.put(entry.getKey(), decorate(entry.getKey(), entry.getValue(), rule));
        }
        return result;
    }

    /**
     * 对表元数据进行修饰，结合分片规则修饰表的列、索引和约束信息
     *
     * @param tableName 表名
     * @param tableMetaData 表元数据
     * @param shardingRule 分片规则
     * @return 修饰后的表元数据
     */
    private TableMetaData decorate(final String tableName, final TableMetaData tableMetaData, final ShardingRule shardingRule) {
        // 如果找到了该表的分片规则，进行修饰；否则返回原始表元数据
        return shardingRule.findTableRule(tableName).map(tableRule -> new TableMetaData(tableName, getColumnMetaDataList(tableMetaData, tableRule),
                getIndexMetaDataList(tableMetaData, tableRule), getConstraintMetaDataList(tableMetaData, shardingRule, tableRule))).orElse(tableMetaData);
    }
    
    private void checkTableMetaData(final Collection<TableMetaData> tableMetaDataList, final ShardingRule rule) {
        Map<String, Collection<TableMetaData>> logicTableMetaDataMap = new LinkedHashMap<>();
        for (TableMetaData each : tableMetaDataList) {
            Optional<String> logicName = rule.findLogicTableByActualTable(each.getName());
            if (logicName.isPresent()) {
                Collection<TableMetaData> logicTableMetaDataList = logicTableMetaDataMap.getOrDefault(logicName.get(), new LinkedList<>());
                logicTableMetaDataList.add(each);
                logicTableMetaDataMap.putIfAbsent(logicName.get(), logicTableMetaDataList);
            }
        }
        for (Entry<String, Collection<TableMetaData>> entry : logicTableMetaDataMap.entrySet()) {
            checkUniformed(entry.getKey(), entry.getValue(), rule);
        }
    }
    
    private Map<String, TableMetaData> getTableMetaDataMap(final Collection<TableMetaData> tableMetaDataList, final ShardingRule rule) {
        Map<String, TableMetaData> result = new LinkedHashMap<>();
        for (TableMetaData each : tableMetaDataList) {
            Collection<String> logicTables = rule.getLogicTablesByActualTable(each.getName());
            if (!logicTables.isEmpty()) {
                logicTables.forEach(logicTable -> result.putIfAbsent(logicTable, each));
            } else {
                result.putIfAbsent(each.getName(), each);
            }
        }
        return result;
    }
    
    private void checkUniformed(final String logicTableName, final Collection<TableMetaData> tableMetaDataList, final ShardingRule shardingRule) {
        TableMetaData sample = decorate(logicTableName, tableMetaDataList.iterator().next(), shardingRule);
        Collection<TableMetaDataViolation> violations = tableMetaDataList.stream()
                .filter(each -> !sample.equals(decorate(logicTableName, each, shardingRule)))
                .map(each -> new TableMetaDataViolation(each.getName(), each)).collect(Collectors.toList());
        throwExceptionIfNecessary(violations, logicTableName);
    }
    
    private void throwExceptionIfNecessary(final Collection<TableMetaDataViolation> violations, final String logicTableName) {
        if (!violations.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder(
                    "Cannot get uniformed table structure for logic table `%s`, it has different meta data of actual tables are as follows:").append(System.lineSeparator());
            for (TableMetaDataViolation each : violations) {
                errorMessage.append("actual table: ").append(each.getActualTableName()).append(", meta data: ").append(each.getTableMetaData()).append(System.lineSeparator());
            }
            throw new ShardingSphereException(errorMessage.toString(), logicTableName);
        }
    }
    
    private Collection<ColumnMetaData> getColumnMetaDataList(final TableMetaData tableMetaData, final TableRule tableRule) {
        Collection<ColumnMetaData> result = new LinkedList<>();
        for (Entry<String, ColumnMetaData> entry : tableMetaData.getColumns().entrySet()) {
            boolean generated = entry.getKey().equalsIgnoreCase(tableRule.getGenerateKeyColumn().orElse(null));
            ColumnMetaData columnMetaData = entry.getValue();
            result.add(new ColumnMetaData(columnMetaData.getName(), columnMetaData.getDataType(), columnMetaData.isPrimaryKey(), generated, columnMetaData.isCaseSensitive()));
        }
        return result;
    }
    
    private Collection<IndexMetaData> getIndexMetaDataList(final TableMetaData tableMetaData, final TableRule tableRule) {
        Collection<IndexMetaData> result = new HashSet<>();
        for (Entry<String, IndexMetaData> entry : tableMetaData.getIndexes().entrySet()) {
            for (DataNode each : tableRule.getActualDataNodes()) {
                getLogicIndex(entry.getKey(), each.getTableName()).ifPresent(logicIndex -> result.add(new IndexMetaData(logicIndex)));
            }
        }
        return result;
    }
    
    private Collection<ConstraintMetaData> getConstraintMetaDataList(final TableMetaData tableMetaData, final ShardingRule shardingRule, final TableRule tableRule) {
        Collection<ConstraintMetaData> result = new HashSet<>();
        for (Entry<String, ConstraintMetaData> entry : tableMetaData.getConstrains().entrySet()) {
            for (DataNode each : tableRule.getActualDataNodes()) {
                String referencedTableName = entry.getValue().getReferencedTableName();
                getLogicIndex(entry.getKey(), each.getTableName()).ifPresent(logicConstraint -> result.add(
                        new ConstraintMetaData(logicConstraint, shardingRule.findLogicTableByActualTable(referencedTableName).orElse(referencedTableName))));
            }
        }
        return result;
    }
    
    private Optional<String> getLogicIndex(final String actualIndexName, final String actualTableName) {
        String indexNameSuffix = "_" + actualTableName;
        return actualIndexName.endsWith(indexNameSuffix) ? Optional.of(actualIndexName.replace(indexNameSuffix, "")) : Optional.empty();
    }
    
    @Override
    public int getOrder() {
        return ShardingOrder.ORDER;
    }
    
    @Override
    public Class<ShardingRule> getTypeClass() {
        return ShardingRule.class;
    }
    
    @RequiredArgsConstructor
    @Getter
    private static final class TableMetaDataViolation {
        
        private final String actualTableName;
        
        private final TableMetaData tableMetaData;
    }
}
