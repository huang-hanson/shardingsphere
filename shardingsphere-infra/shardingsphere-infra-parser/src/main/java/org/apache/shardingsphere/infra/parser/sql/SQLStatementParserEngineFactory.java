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

package org.apache.shardingsphere.infra.parser.sql;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.sql.parser.api.CacheOption;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL 语句解析引擎工厂类。
 *
 * 该类用于为指定数据库类型创建或获取一个 SQL 语句解析器（SQLStatementParserEngine）。
 * 它采用缓存池的方式复用解析器实例，避免重复创建，提高性能。
 *
 * 此类为工具类，禁止实例化，因此使用了私有构造器。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SQLStatementParserEngineFactory {

    /**
     * 缓存不同数据库类型对应的 SQL 语句解析器。
     *
     * key：数据库类型（如 "MySQL"、"PostgreSQL"）
     * value：对应的 SQLStatementParserEngine 实例
     *
     * 使用 ConcurrentHashMap 保证线程安全，适用于并发场景。
     */
    private static final Map<String, SQLStatementParserEngine> ENGINES = new ConcurrentHashMap<>();

    /**
     * 获取指定数据库类型的 SQL 语句解析引擎。
     *
     * 如果缓存中已存在对应类型的解析器，则直接返回；
     * 如果不存在，则使用提供的缓存选项和是否解析注释的配置创建一个新的解析器并缓存。
     *
     * @param databaseType 数据库类型（如 MySQL、PostgreSQL 等）
     * @param sqlStatementCacheOption SQL 语句缓存选项，用于缓存 SQL 语句解析结果
     * @param parseTreeCacheOption 解析树缓存选项，用于缓存 SQL 的语法树
     * @param isParseComment 是否解析 SQL 中的注释（true 表示解析，false 表示忽略注释）
     * @return SQL 语句解析引擎
     */
    public static SQLStatementParserEngine getSQLStatementParserEngine(final String databaseType, 
                                                                       final CacheOption sqlStatementCacheOption, final CacheOption parseTreeCacheOption, final boolean isParseComment) {
        // 先尝试从缓存中获取对应数据库类型的解析器
        SQLStatementParserEngine result = ENGINES.get(databaseType);
        // 如果缓存中没有，则创建新的解析器并放入缓存（使用 computeIfAbsent 保证线程安全且只初始化一次）
        if (null == result) {
            result = ENGINES.computeIfAbsent(databaseType, key -> new SQLStatementParserEngine(key, sqlStatementCacheOption, parseTreeCacheOption, isParseComment));
        }
        // 返回解析器
        return result;
    }
}
