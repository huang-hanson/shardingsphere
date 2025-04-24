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
import org.apache.shardingsphere.infra.metadata.schema.model.IndexMetaData;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;

/**
 * 索引元数据加载器，用于从数据库加载指定表的索引信息。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IndexMetaDataLoader {
    // 索引名称字段，ResultSet中列名，通常是 getIndexInfo 查询返回的字段之一
    private static final String INDEX_NAME = "INDEX_NAME";
    // Oracle 特殊错误码，如果在视图上调用 getIndexInfo，会抛出该错误码的 SQLException
    private static final int ORACLE_VIEW_NOT_APPROPRIATE_VENDOR_CODE = 1702;

    /**
     * 加载表的索引元数据列表。
     *
     * 注意：在某些 JDBC 实现中（如 Oracle），getIndexInfo 返回的结果中可能包含不是索引的统计信息，这些记录的 INDEX_NAME 为 null，应当跳过。
     *
     * @param connection JDBC 连接对象
     * @param table 表名
     * @return 索引元数据集合
     * @throws SQLException 如果数据库操作失败，则抛出异常
     */
    @SuppressWarnings("CollectionWithoutInitialCapacity")
    public static Collection<IndexMetaData> load(final Connection connection, final String table) throws SQLException {
        // 用于存放返回的索引元数据，使用 HashSet 自动去重
        Collection<IndexMetaData> result = new HashSet<>();
        try (
                // 调用 JDBC 的元数据接口获取指定表的索引信息
                ResultSet resultSet = connection.getMetaData().getIndexInfo(
                        connection.getCatalog(),  // 当前 catalog（一般可为 null）
                        connection.getSchema(),   // 当前 schema（根据数据库类型决定是否生效）
                        table,                    // 表名
                        false,                    // unique: 是否只返回唯一索引（false 表示全部索引）
                        false                     // approximate: 是否允许返回近似结果（false 表示精确结果）
                )
        ) {
            while (resultSet.next()) {
                // 获取索引名字段
                String indexName = resultSet.getString(INDEX_NAME);
                // Oracle 等数据库中可能会返回一些统计信息，它们的 INDEX_NAME 为 null，应当跳过
                if (null != indexName) {
                    result.add(new IndexMetaData(indexName));
                }
            }
        } catch (final SQLException ex) {
            // 如果是 Oracle 数据库中视图引起的 1702 错误，不抛出，表示忽略该异常（可能是非表结构）
            if (ORACLE_VIEW_NOT_APPROPRIATE_VENDOR_CODE != ex.getErrorCode()) {
                throw ex;
            }
        }
        return result;
    }
}
