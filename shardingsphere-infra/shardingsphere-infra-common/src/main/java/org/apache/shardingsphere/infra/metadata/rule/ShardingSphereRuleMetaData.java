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

package org.apache.shardingsphere.infra.metadata.rule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.config.RuleConfiguration;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * ShardingSphere 规则元数据类。
 * 存储所有规则配置（RuleConfiguration）及其对应的规则实现（ShardingSphereRule），
 * 并提供按类型查找规则或配置的能力。
 */
@RequiredArgsConstructor
@Getter
public final class ShardingSphereRuleMetaData {
    /**
     * 规则配置集合（例如：分片规则、加密规则、读写分离规则等）
     */
    private final Collection<RuleConfiguration> configurations;
    /**
     * 实际规则对象集合（由配置生成的规则实现类）
     */
    private final Collection<ShardingSphereRule> rules;

    /**
     * 按类型查找所有匹配的规则实现
     *
     * @param clazz 目标规则的类型
     * @param <T>   规则类型（ShardingSphereRule 的子类）
     * @return 符合条件的规则集合
     */
    public <T extends ShardingSphereRule> Collection<T> findRules(final Class<T> clazz) {
        List<T> result = new LinkedList<>();
        for (ShardingSphereRule each : rules) {
            // 判断该规则是否是目标类型或其子类
            if (clazz.isAssignableFrom(each.getClass())) {
                result.add(clazz.cast(each));
            }
        }
        return result;
    }

    /**
     * 按类型查找所有匹配的规则配置
     *
     * @param clazz 目标配置的类型
     * @param <T>   配置类型（RuleConfiguration 的子类）
     * @return 符合条件的配置集合
     */
    public <T extends RuleConfiguration> Collection<T> findRuleConfiguration(final Class<T> clazz) {
        Collection<T> result = new LinkedList<>();
        for (RuleConfiguration each : configurations) {
            if (clazz.isAssignableFrom(each.getClass())) {
                result.add(clazz.cast(each));
            }
        }
        return result;
    }

    /**
     * 按类型查找**单个**规则配置（如果存在多个，只取第一个）
     *
     * @param clazz 目标配置类型
     * @param <T>   配置类型（RuleConfiguration 的子类）
     * @return 匹配到的单个配置，若未找到返回 Optional.empty()
     */
    public <T extends RuleConfiguration> Optional<T> findSingleRuleConfiguration(final Class<T> clazz) {
        Collection<T> foundRuleConfig = findRuleConfiguration(clazz);
        return foundRuleConfig.isEmpty() ? Optional.empty() : Optional.of(foundRuleConfig.iterator().next());
    }

    /**
     * 按类型查找**单个**规则对象（如果存在多个，只取第一个）
     *
     * @param clazz 目标规则类型
     * @param <T>   规则类型（ShardingSphereRule 的子类）
     * @return 匹配到的单个规则，若未找到返回 Optional.empty()
     */
    public <T extends ShardingSphereRule> Optional<T> findSingleRule(final Class<T> clazz) {
        Collection<T> foundRules = findRules(clazz);
        return foundRules.isEmpty() ? Optional.empty() : Optional.of(foundRules.iterator().next());
    }
}
