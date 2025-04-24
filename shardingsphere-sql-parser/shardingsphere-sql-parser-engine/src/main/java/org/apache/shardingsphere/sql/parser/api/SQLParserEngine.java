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

package org.apache.shardingsphere.sql.parser.api;

import com.google.common.cache.LoadingCache;
import org.apache.shardingsphere.sql.parser.core.ParseASTNode;
import org.apache.shardingsphere.sql.parser.core.database.cache.ParseTreeCacheBuilder;
import org.apache.shardingsphere.sql.parser.core.database.parser.SQLParserExecutor;

/**
 * SQL解析引擎，用于将原始SQL字符串解析为抽象语法树（AST）节点。
 * 这是 ShardingSphere 5.x 中封装的核心SQL解析组件。
 */
public final class SQLParserEngine {

    // 实际执行SQL解析任务的执行器，负责调用 ANTLR 解析器生成语法树
    private final SQLParserExecutor sqlParserExecutor;

    // 缓存解析结果，避免重复解析相同SQL。key 是 SQL 字符串，value 是对应的 AST 节点
    private final LoadingCache<String, ParseASTNode> parseTreeCache;

    /**
     * 构造函数，根据数据库类型创建解析执行器，并初始化解析结果缓存。
     *
     * @param databaseType 数据库类型，如 MySQL、PostgreSQL 等
     * @param cacheOption 缓存选项，用于配置缓存的大小、过期时间等
     */
    public SQLParserEngine(final String databaseType, final CacheOption cacheOption) {
        // 初始化解析执行器，根据数据库类型加载对应的 ANTLR 语法解析器
        sqlParserExecutor = new SQLParserExecutor(databaseType);
        // 创建解析缓存，缓存解析后的 AST 结果，提升性能
        parseTreeCache = ParseTreeCacheBuilder.build(cacheOption, databaseType);
    }

    /**
     * 解析 SQL 为抽象语法树（AST）。
     *
     * @param sql 要解析的 SQL 字符串
     * @param useCache 是否使用缓存（true 表示先查缓存，否则直接解析）
     * @return 解析得到的 AST 根节点（ParseASTNode）
     */
    public ParseASTNode parse(final String sql, final boolean useCache) {
        // 如果使用缓存，尝试从缓存中获取解析结果
        // 若缓存未命中，则自动调用 SQLParserExecutor 进行解析，并写入缓存
        return useCache ? parseTreeCache.getUnchecked(sql) : sqlParserExecutor.parse(sql);
    }
}
