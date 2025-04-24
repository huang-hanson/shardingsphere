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

package org.apache.shardingsphere.mode.metadata;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.executor.kernel.ExecutorEngine;
import org.apache.shardingsphere.infra.federation.optimizer.context.OptimizerContext;
import org.apache.shardingsphere.infra.federation.optimizer.context.OptimizerContextFactory;
import org.apache.shardingsphere.infra.lock.ShardingSphereLock;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.mode.metadata.persist.MetaDataPersistService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * 元数据上下文容器类，包含ShardingSphere的所有元数据上下文信息。
 * 实现了AutoCloseable接口，用于在关闭时释放资源。
 */
@RequiredArgsConstructor// Lombok注解，自动生成包含所有final字段的构造函数
@Getter// Lombok注解，自动生成所有字段的getter方法
public final class MetaDataContexts implements AutoCloseable {
    // 元数据持久化服务，负责元数据的存储和读取
    private final MetaDataPersistService metaDataPersistService;
    // 存储所有schema的元数据，key为schema名称，value为对应的元数据
    private final Map<String, ShardingSphereMetaData> metaDataMap;
    // 全局规则元数据，适用于所有schema的规则
    private final ShardingSphereRuleMetaData globalRuleMetaData;
    // 执行引擎，用于任务执行
    private final ExecutorEngine executorEngine;
    // 优化器上下文，包含SQL优化相关的信息
    private final OptimizerContext optimizerContext;
    // 配置属性，包含ShardingSphere实例的所有配置项
    private final ConfigurationProperties props;
    /**
     * 构造函数（简化版），使用默认值初始化部分字段
     * @param metaDataPersistService 元数据持久化服务
     */
    public MetaDataContexts(final MetaDataPersistService metaDataPersistService) {
        this(metaDataPersistService, new LinkedHashMap<>(), new ShardingSphereRuleMetaData(Collections.emptyList(), Collections.emptyList()), null, 
                OptimizerContextFactory.create(new HashMap<>(), new ShardingSphereRuleMetaData(Collections.emptyList(), Collections.emptyList())), new ConfigurationProperties(new Properties()));
    }

    /**
     * 获取元数据持久化服务（Optional包装）
     * @return 可能存在的元数据持久化服务
     */
    public Optional<MetaDataPersistService> getMetaDataPersistService() {
        return Optional.ofNullable(metaDataPersistService);
    }

    /**
     * 获取所有schema名称
     * @return schema名称集合
     */
    public Collection<String> getAllSchemaNames() {
        return metaDataMap.keySet();
    }

    /**
     * 根据schema名称获取对应的元数据
     * @param schemaName schema名称
     * @return 对应的元数据对象
     */
    public ShardingSphereMetaData getMetaData(final String schemaName) {
        return metaDataMap.get(schemaName);
    }

    /**
     * 获取分布式锁（当前实现返回空Optional）
     * @return 可能存在的分布式锁
     */
    public Optional<ShardingSphereLock> getLock() {
        return Optional.empty();
    }
    /**
     * 关闭资源，释放执行引擎和持久化仓库的连接
     */
    @Override
    public void close() throws Exception {
        executorEngine.close();
        if (null != metaDataPersistService) {
            metaDataPersistService.getRepository().close();
        }
    }
}
