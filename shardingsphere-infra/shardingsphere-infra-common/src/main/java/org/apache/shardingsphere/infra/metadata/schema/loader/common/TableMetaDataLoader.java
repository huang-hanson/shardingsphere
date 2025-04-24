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
import org.apache.shardingsphere.infra.metadata.schema.loader.adapter.MetaDataLoaderConnectionAdapter;
import org.apache.shardingsphere.infra.metadata.schema.model.TableMetaData;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Optional;

/**
 * 表元数据加载器。
 * 提供静态方法用于从数据源中加载某个表的结构信息（字段、索引等）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TableMetaDataLoader {

    /**
     * 加载指定表的元数据信息。
     *
     * @param dataSource 数据源（一般是实际数据库连接池）
     * @param tableNamePattern 表名或匹配模式（如支持大小写或通配符）
     * @param databaseType 数据库类型（如 MySQL、PostgreSQL，用于适配差异）
     * @return 表元数据，如果表不存在则返回 Optional.empty()
     * @throws SQLException SQL 异常
     */
    public static Optional<TableMetaData> load(final DataSource dataSource, final String tableNamePattern, final DatabaseType databaseType) throws SQLException {
        // 使用 MetaDataLoaderConnectionAdapter 包装 JDBC Connection，屏蔽不同数据库差异
        try (MetaDataLoaderConnectionAdapter connectionAdapter = new MetaDataLoaderConnectionAdapter(databaseType, dataSource.getConnection())) {
            // 根据数据库类型格式化表名（比如大小写敏感问题）
            String formattedTableNamePattern = databaseType.formatTableNamePattern(tableNamePattern);

            // 判断表是否存在，存在则加载元数据
            return isTableExist(connectionAdapter, formattedTableNamePattern)
                    ? Optional.of(new TableMetaData(tableNamePattern, ColumnMetaDataLoader.load(
                            connectionAdapter, formattedTableNamePattern, databaseType), IndexMetaDataLoader.load(connectionAdapter, formattedTableNamePattern), Collections.emptyList()))
                    : Optional.empty();
        }
    }

    /**
     * 判断指定表是否存在于当前连接的数据库中。
     *
     * @param connection JDBC 连接
     * @param tableNamePattern 表名或匹配模式
     * @return true 表存在；false 表不存在
     * @throws SQLException SQL 异常
     */
    private static boolean isTableExist(final Connection connection, final String tableNamePattern) throws SQLException {
        // 使用 JDBC 元数据接口获取当前库中是否有匹配表
        try (ResultSet resultSet = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), tableNamePattern, null)) {
            return resultSet.next(); // 有记录表示表存在
        }
    }
}
