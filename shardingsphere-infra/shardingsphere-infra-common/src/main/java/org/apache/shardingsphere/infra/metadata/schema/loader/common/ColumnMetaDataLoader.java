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

package org.apache.shardingsphere.infra.metadata.schema.loader.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.metadata.schema.model.ColumnMetaData;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * 列元数据加载器。
 * 用于加载指定表的所有字段信息，包括字段名、数据类型、是否主键、是否自增、是否区分大小写等。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ColumnMetaDataLoader {
    // JDBC 元数据中常用的字段名
    private static final String COLUMN_NAME = "COLUMN_NAME";
    
    private static final String DATA_TYPE = "DATA_TYPE";
    
    private static final String TABLE_NAME = "TABLE_NAME";

    /**
     * 加载表的所有字段元数据。
     *
     * @param connection JDBC 连接
     * @param tableNamePattern 表名（完全匹配）
     * @param databaseType 数据库类型（用于适配不同 SQL 方言）
     * @return 字段元数据集合
     * @throws SQLException SQL 异常
     */
    public static Collection<ColumnMetaData> load(final Connection connection, final String tableNamePattern, final DatabaseType databaseType) throws SQLException {
        Collection<ColumnMetaData> result = new LinkedList<>();

        // 加载主键字段名
        Collection<String> primaryKeys = loadPrimaryKeys(connection, tableNamePattern);
        //字段名称集合
        List<String> columnNames = new ArrayList<>();
        //字段类型集合
        List<Integer> columnTypes = new ArrayList<>();
        //是否是主键集合
        List<Boolean> isPrimaryKeys = new ArrayList<>();
        //字段是否是大小写敏感
        List<Boolean> isCaseSensitives = new ArrayList<>();
        //使用原生的jdbc的connection获取字段元数据，并且对字段元数据进行遍历
        try (ResultSet resultSet = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(), tableNamePattern, "%")) {
            while (resultSet.next()) {
                //字段名称
                String tableName = resultSet.getString(TABLE_NAME);
                if (Objects.equals(tableNamePattern, tableName)) {
                    String columnName = resultSet.getString(COLUMN_NAME);
                    //字段类型
                    columnTypes.add(resultSet.getInt(DATA_TYPE));
                    //是否是主键
                    isPrimaryKeys.add(primaryKeys.contains(columnName));
                    columnNames.add(columnName);
                }
            }
        }
        //判断表字段是否大小写敏感
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(generateEmptyResultSQL(tableNamePattern, databaseType))) {
            for (int i = 0; i < columnNames.size(); i++) {
                boolean generated = resultSet.getMetaData().isAutoIncrement(i + 1);
                isCaseSensitives.add(resultSet.getMetaData().isCaseSensitive(resultSet.findColumn(columnNames.get(i))));
                result.add(new ColumnMetaData(columnNames.get(i), columnTypes.get(i), isPrimaryKeys.get(i), generated, isCaseSensitives.get(i)));
            }
        }
        return result;
    }
    
    private static String generateEmptyResultSQL(final String table, final DatabaseType databaseType) {
        return String.format("SELECT * FROM %s WHERE 1 != 1", databaseType.getQuoteCharacter().wrap(table));
    }
    
    private static Collection<String> loadPrimaryKeys(final Connection connection, final String table) throws SQLException {
        Collection<String> result = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), connection.getSchema(), table)) {
            while (resultSet.next()) {
                result.add(resultSet.getString(COLUMN_NAME));
            }
        }
        return result;
    }
}
