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

package org.apache.shardingsphere.infra.route.engine.impl;

import org.apache.shardingsphere.infra.binder.LogicSQL;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.context.RouteMapper;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.infra.route.engine.SQLRouteExecutor;

import java.util.Collections;

/**
 * 全量 SQL 路由执行器。
 *
 * 该类的作用是将一条 SQL 请求路由到所有数据源（dataSource）上，常用于广播类 SQL，
 * 比如：建表语句、全量查询等需要在所有数据源上执行的情况。
 */
public final class AllSQLRouteExecutor implements SQLRouteExecutor {

    /**
     * 执行路由逻辑，生成包含所有数据源的 RouteContext。
     *
     * @param logicSQL 逻辑 SQL 对象（已绑定参数及上下文）
     * @param metaData 当前逻辑库的元数据，包括数据源信息等
     * @return 路由上下文，包含所有数据源的 RouteUnit
     */
    @Override
    public RouteContext route(final LogicSQL logicSQL, final ShardingSphereMetaData metaData) {
        // 创建空的路由上下文容器
        RouteContext result = new RouteContext();
        // 遍历所有数据源（通常是多个物理库的别名，如 ds0, ds1）
        for (String each : metaData.getResource().getDataSources().keySet()) {
            // 将每个数据源都添加为一个 RouteUnit
            // RouteMapper(sourceName, actualName) 表示逻辑与实际数据源之间的映射（此处一一对应）
            result.getRouteUnits().add(new RouteUnit(new RouteMapper(each, each), Collections.emptyList()));
        }
        return result;
    }
}
