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

package org.apache.shardingsphere.sharding.yaml.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.shardingsphere.infra.yaml.config.pojo.YamlRuleConfiguration;
import org.apache.shardingsphere.infra.yaml.config.pojo.algorithm.YamlShardingSphereAlgorithmConfiguration;
import org.apache.shardingsphere.infra.yaml.config.pojo.rulealtered.YamlOnRuleAlteredActionConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.rule.YamlShardingAutoTableRuleConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.rule.YamlTableRuleConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.strategy.keygen.YamlKeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.strategy.sharding.YamlShardingStrategyConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sharding rule configuration for YAML.
 */
@Getter
@Setter
public final class YamlShardingRuleConfiguration implements YamlRuleConfiguration {

    /**
     * 分库分表的表规则配置（标准分片表）
     * key 为逻辑表名，value 为对应的分片配置
     */
    private Map<String, YamlTableRuleConfiguration> tables = new LinkedHashMap<>();
    /**
     * 自动分片表规则配置（使用 inline 或 hint 自动分片）
     * key 为逻辑表名，value 为对应的自动分片配置
     */
    private Map<String, YamlShardingAutoTableRuleConfiguration> autoTables = new LinkedHashMap<>();
    /**
     * 绑定表集合（多个逻辑表具有相同的分片策略并且可以联表查询）
     */
    private Collection<String> bindingTables = new ArrayList<>();
    /**
     * 广播表集合（每个分片库中都存在完整一份，通常是字典表）
     */
    private Collection<String> broadcastTables = new ArrayList<>();
    /**
     * 默认的分库策略（当逻辑表未指定时使用此策略）
     */
    private YamlShardingStrategyConfiguration defaultDatabaseStrategy;
    /**
     * 默认的分表策略（当逻辑表未指定时使用此策略）
     */
    private YamlShardingStrategyConfiguration defaultTableStrategy;
    /**
     * 默认的主键生成策略（当逻辑表未指定时使用此策略）
     */
    private YamlKeyGenerateStrategyConfiguration defaultKeyGenerateStrategy;
    /**
     * 分片算法配置集合
     * key 为算法名称，value 为算法的 YAML 配置
     */
    private Map<String, YamlShardingSphereAlgorithmConfiguration> shardingAlgorithms = new LinkedHashMap<>();
    /**
     * 主键生成器配置集合
     * key 为生成器名称，value 为生成器的 YAML 配置
     */
    private Map<String, YamlShardingSphereAlgorithmConfiguration> keyGenerators = new LinkedHashMap<>();
    /**
     * 默认的分片列名称（用于配置不指定表时的默认分片列）
     */
    private String defaultShardingColumn;
    /**
     * 数据扩容/收缩使用的任务名称
     */
    private String scalingName;
    /**
     * 数据扩容/收缩相关的动作配置
     * key 为逻辑表名，value 为规则变更后执行的操作
     */
    private Map<String, YamlOnRuleAlteredActionConfiguration> scaling = new LinkedHashMap<>();
    /**
     * 返回 ShardingRuleConfiguration 类型，用于反序列化转换
     */
    @Override
    public Class<ShardingRuleConfiguration> getRuleConfigurationType() {
        return ShardingRuleConfiguration.class;
    }
}
