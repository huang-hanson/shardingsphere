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

package org.apache.shardingsphere.driver.jdbc.adapter;

import lombok.Getter;
import org.apache.shardingsphere.driver.jdbc.adapter.invocation.MethodInvocationRecorder;

import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;

/**
 * Adapter for {@code java.sql.Wrapper}.
 * JDBC 包装器（Wrapper）的抽象适配器基类，实现标准的 {@link java.sql.Wrapper} 接口。
 * 用于为 ShardingSphere 的 JDBC 对象（如 Connection/Statement）提供统一的包装行为。
 */
@Getter
public abstract class WrapperAdapter implements Wrapper {
    /**
     * 方法调用记录器，用于追踪 JDBC 对象（如 Statement）的方法调用链。
     * 通常用于调试或监控场景。
     */
    private final MethodInvocationRecorder<Statement> methodInvocationRecorder = new MethodInvocationRecorder<>();

    /**
     * 实现 {@link Wrapper#unwrap(Class)} 方法，用于将当前对象解包为指定类型的实例。
     *
     * @param iface 目标类型（通常是 JDBC 接口，如 Connection.class）
     * @param <T>   泛型类型
     * @return 当前对象（如果类型匹配）
     * @throws SQLException 如果类型不匹配，抛出 SQL 异常
     */
    @SuppressWarnings("unchecked")
    @Override
    public final <T> T unwrap(final Class<T> iface) throws SQLException {
        if (isWrapperFor(iface)) {
            // 类型检查通过，直接返回当前对象（强制类型转换）
            return (T) this;
        }
        // 类型不匹配时抛出标准 SQL 异常
        throw new SQLException(String.format("[%s] cannot be unwrapped as [%s]", getClass().getName(), iface.getName()));
    }

    /**
     * 实现 {@link Wrapper#isWrapperFor(Class)} 方法，检查当前对象是否可解包为指定类型。
     *
     * @param iface 目标类型
     * @return true 如果当前对象是目标类型的实例，否则 false
     */
    @Override
    public final boolean isWrapperFor(final Class<?> iface) {
        return iface.isInstance(this);
    }
}
