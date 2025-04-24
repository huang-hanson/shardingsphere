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

package org.apache.shardingsphere.infra.federation.optimizer.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.federation.optimizer.context.parser.OptimizerParserContext;
import org.apache.shardingsphere.infra.federation.optimizer.context.planner.OptimizerPlannerContext;
import org.apache.shardingsphere.infra.federation.optimizer.metadata.FederationMetaData;
import org.apache.shardingsphere.parser.rule.SQLParserRule;

import java.util.Map;

/**
 * 优化器上下文（OptimizerContext）。
 *
 * 该类是 ShardingSphere 中 SQL 优化器的上下文容器，保存了 SQL 解析规则、联合元数据、
 * 以及各个逻辑库对应的解析器上下文与执行计划上下文。
 */
@RequiredArgsConstructor
@Getter
public final class OptimizerContext {
    /**
     * SQL 解析规则。
     * 包含 SQL 的词法、语法解析规则，通常在启动时初始化。
     */
    private final SQLParserRule sqlParserRule;
    /**
     * 联邦元数据（FederationMetaData）。
     * 用于跨库或跨数据源查询场景中，构建统一的元数据视图。
     */
    private final FederationMetaData federationMetaData;
    /**
     * SQL 解析上下文映射。
     * key 是逻辑数据库名，value 是 OptimizerParserContext，主要包含解析过程中使用的表结构等元信息。
     */
    private final Map<String, OptimizerParserContext> parserContexts;
    /**
     * SQL 执行计划上下文映射。
     * key 是逻辑数据库名，value 是 OptimizerPlannerContext，保存生成执行计划所需的上下文。
     */
    private final Map<String, OptimizerPlannerContext> plannerContexts;
}
