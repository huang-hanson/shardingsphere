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

package org.apache.shardingsphere.infra.rewrite.engine;

import org.apache.shardingsphere.infra.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.infra.rewrite.engine.result.GenericSQLRewriteResult;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteUnit;
import org.apache.shardingsphere.infra.rewrite.sql.impl.DefaultSQLBuilder;

/**
 * 通用 SQL 改写引擎，用于处理无需路由或全库广播场景的 SQL 改写。
 * 功能：根据 SQLRewriteContext 中的改写令牌（SQLToken）生成最终可执行的 SQL 和参数列表。
 */
public final class GenericSQLRewriteEngine {

    /**
     * 执行 SQL 和参数改写。
     * 处理流程：
     * 1. 使用 SQLBuilder 根据上下文中的 SQLToken 重建 SQL
     * 2. 获取参数构建器生成的最终参数列表
     * 3. 包装为不可变的改写结果
     *
     * @param sqlRewriteContext SQL 改写上下文（包含原始SQL、参数、表元数据及生成的SQLToken）
     * @return 通用SQL改写结果（包含最终SQL和参数列表）
     */
    public GenericSQLRewriteResult rewrite(final SQLRewriteContext sqlRewriteContext) {
        return new GenericSQLRewriteResult(new SQLRewriteUnit(new DefaultSQLBuilder
                (sqlRewriteContext).toSQL(),                                                 // 1. 使用默认SQL构建器生成最终SQL（自动应用所有SQLToken）
                sqlRewriteContext.getParameterBuilder().getParameters()                      // 2. 从参数构建器中获取改写后的参数列表（可能被加密/改写规则修改）
        ));
    }
}
