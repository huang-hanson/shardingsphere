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

package org.apache.shardingsphere.driver.jdbc.adapter.invocation;

import org.apache.shardingsphere.driver.jdbc.adapter.executor.ForceExecuteCallback;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 方法调用记录器，用于跟踪并回放目标对象的方法调用。
 * 通常用于 JDBC 对象（如 Statement、Connection）的代理场景，实现方法调用的延迟执行或重放。
 *
 * @param <T> 目标对象类型（需与 ForceExecuteCallback 的泛型类型一致）
 */
public final class MethodInvocationRecorder<T> {
    /**
     * 方法调用记录映射表：
     * - Key: 方法名称（如 "executeQuery"）
     * - Value: 对应的回调逻辑（通过 ForceExecuteCallback 封装）
     *
     * 使用 LinkedHashMap 保证方法调用的记录顺序与回放顺序一致。
     */
    private final Map<String, ForceExecuteCallback<T>> methodInvocations = new LinkedHashMap<>();

    /**
     * 记录一个方法调用。
     *
     * @param methodName 方法名称（用于唯一标识调用）
     * @param callback   方法实际执行逻辑的回调接口
     *
     * @example
     * recorder.record("executeQuery", stmt -> stmt.executeQuery(sql));
     */
    public void record(final String methodName, final ForceExecuteCallback<T> callback) {
        methodInvocations.put(methodName, callback);
    }

    /**
     * 回放所有已记录的方法调用。
     * 按照方法记录的先后顺序依次执行，若任一回调抛出异常，则终止回放并向上抛出。
     *
     * @param target 目标对象（回调逻辑的执行载体）
     * @throws SQLException 如果回调执行过程中抛出 SQL 异常
     *
     * @example
     * recorder.replay(realStatement); // 在真实 Statement 上重放所有记录的方法
     */
    public void replay(final T target) throws SQLException {
        for (ForceExecuteCallback<T> each : methodInvocations.values()) {
            each.execute(target);
        }
    }
}
