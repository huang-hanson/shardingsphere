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

package org.apache.shardingsphere.infra.federation.optimizer.metadata;

import lombok.Getter;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 联邦元数据（FederationMetaData）。
 *
 * 这个类的作用是将多个逻辑数据库的元数据信息集中管理，方便后续进行“跨库联合查询”。
 * 比如你有多个逻辑库，每个库里有自己的表结构，那这个类就把它们都组织到一个 Map 里，供联邦查询使用。
 */
@Getter
public final class FederationMetaData {
    // 保存所有逻辑库的元数据，key 是逻辑库名称（小写），value 是该库的联邦元数据
    private final Map<String, FederationDatabaseMetaData> databases;
    /**
     * 构造方法：将传入的逻辑库元数据（ShardingSphereMetaData）转换成联邦用的数据库元数据（FederationDatabaseMetaData）。
     *
     * @param metaDataMap 所有逻辑库的元数据信息，key 是逻辑库名，value 是 ShardingSphereMetaData（包含资源、规则、schema等信息）
     */
    public FederationMetaData(final Map<String, ShardingSphereMetaData> metaDataMap) {
        // 初始化 databases Map，预设容量 = metaDataMap.size()，负载因子 = 1（不考虑扩容，节省内存）
        databases = new LinkedHashMap<>(metaDataMap.size(), 1);
        for (Entry<String, ShardingSphereMetaData> entry : metaDataMap.entrySet()) {
            // key：逻辑库名称转为小写
            // value：构建 FederationDatabaseMetaData，包括库名和它对应的所有 schema
            databases.put(entry.getKey().toLowerCase(), new FederationDatabaseMetaData(entry.getKey(), entry.getValue().getSchemas()));
        }
    }
}
