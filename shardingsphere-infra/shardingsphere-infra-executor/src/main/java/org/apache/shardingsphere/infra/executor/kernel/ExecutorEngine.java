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

package org.apache.shardingsphere.infra.executor.kernel;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.Getter;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroup;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupContext;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutorCallback;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutorDataMap;
import org.apache.shardingsphere.infra.executor.kernel.thread.ExecutorServiceManager;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 执行器引擎（ExecutorEngine）。
 *
 * 这个类用于管理 SQL 执行的并发线程池，并封装了串行或并行执行任务的流程逻辑。
 * 它是整个 SQL 执行调度的核心，可以根据配置决定是串行还是并行执行。
 */
@Getter
public final class ExecutorEngine implements AutoCloseable {
    // 管理线程池的工具类，用于获取线程池对象SQLStatementParserEngineFactory
    private final ExecutorServiceManager executorServiceManager;
    /**
     * 构造方法。
     *
     * @param executorSize 线程池的线程数
     */
    public ExecutorEngine(final int executorSize) {
        executorServiceManager = new ExecutorServiceManager(executorSize);
    }

    /**
     * 执行入口方法（简化版本，默认并行执行）。
     *
     * @param executionGroupContext 执行分组上下文
     * @param callback 每组执行的回调
     * @param <I> 输入数据类型
     * @param <O> 输出结果类型
     * @return 执行结果列表
     * @throws SQLException 如果执行失败
     */
    public <I, O> List<O> execute(final ExecutionGroupContext<I> executionGroupContext, final ExecutorCallback<I, O> callback) throws SQLException {
        return execute(executionGroupContext, null, callback, false);
    }

    /**
     * 执行主方法（支持串行或并行、支持设置首次回调）。
     *
     * @param executionGroupContext 执行组上下文
     * @param firstCallback 第一个执行组使用的回调（可选）
     * @param callback 其余执行组使用的回调
     * @param serial 是否串行执行
     * @param <I> 输入类型
     * @param <O> 输出类型
     * @return 执行结果集合
     * @throws SQLException 执行失败时抛出
     */
    public <I, O> List<O> execute(final ExecutionGroupContext<I> executionGroupContext,
                                  final ExecutorCallback<I, O> firstCallback, final ExecutorCallback<I, O> callback, final boolean serial) throws SQLException {
        if (executionGroupContext.getInputGroups().isEmpty()) {
            return Collections.emptyList();
        }
        return serial ? serialExecute(executionGroupContext.getInputGroups().iterator(), firstCallback, callback)
                : parallelExecute(executionGroupContext.getInputGroups().iterator(), firstCallback, callback);
    }
    // 串行执行所有任务组
    private <I, O> List<O> serialExecute(final Iterator<ExecutionGroup<I>> executionGroups, final ExecutorCallback<I, O> firstCallback, final ExecutorCallback<I, O> callback) throws SQLException {
        ExecutionGroup<I> firstInputs = executionGroups.next();
        // 执行第一个任务组（可以使用专属回调）
        List<O> result = new LinkedList<>(syncExecute(firstInputs, null == firstCallback ? callback : firstCallback));
        // 其余任务组用统一回调串行执行
        while (executionGroups.hasNext()) {
            result.addAll(syncExecute(executionGroups.next(), callback));
        }
        return result;
    }
    // 并行执行剩余任务组，第一个任务组同步执行
    private <I, O> List<O> parallelExecute(final Iterator<ExecutionGroup<I>> executionGroups, final ExecutorCallback<I, O> firstCallback, final ExecutorCallback<I, O> callback) throws SQLException {
        ExecutionGroup<I> firstInputs = executionGroups.next();
        Collection<ListenableFuture<Collection<O>>> restResultFutures = asyncExecute(executionGroups, callback);
        return getGroupResults(syncExecute(firstInputs, null == firstCallback ? callback : firstCallback), restResultFutures);
    }
    // 同步执行某一个 ExecutionGroup
    private <I, O> Collection<O> syncExecute(final ExecutionGroup<I> executionGroup, final ExecutorCallback<I, O> callback) throws SQLException {
        return callback.execute(executionGroup.getInputs(), true, ExecutorDataMap.getValue());
    }
    // 异步执行所有剩余 ExecutionGroup
    private <I, O> Collection<ListenableFuture<Collection<O>>> asyncExecute(final Iterator<ExecutionGroup<I>> executionGroups, final ExecutorCallback<I, O> callback) {
        Collection<ListenableFuture<Collection<O>>> result = new LinkedList<>();
        while (executionGroups.hasNext()) {
            result.add(asyncExecute(executionGroups.next(), callback));
        }
        return result;
    }
    // 异步提交单个任务组到线程池
    private <I, O> ListenableFuture<Collection<O>> asyncExecute(final ExecutionGroup<I> executionGroup, final ExecutorCallback<I, O> callback) {
        Map<String, Object> dataMap = ExecutorDataMap.getValue();
        return executorServiceManager.getExecutorService().submit(() -> callback.execute(executionGroup.getInputs(), false, dataMap));
    }
    // 获取所有执行结果（同步结果 + 异步 future 结果）
    private <O> List<O> getGroupResults(final Collection<O> firstResults, final Collection<ListenableFuture<Collection<O>>> restFutures) throws SQLException {
        List<O> result = new LinkedList<>(firstResults);
        for (ListenableFuture<Collection<O>> each : restFutures) {
            try {
                result.addAll(each.get());
            } catch (final InterruptedException | ExecutionException ex) {
                return throwException(ex);
            }
        }
        return result;
    }
    // 处理异常抛出
    private <O> List<O> throwException(final Exception exception) throws SQLException {
        if (exception.getCause() instanceof SQLException) {
            throw (SQLException) exception.getCause();
        }
        throw new ShardingSphereException(exception);
    }
    // 优雅关闭线程池
    @Override
    public void close() {
        executorServiceManager.close();
    }
}
