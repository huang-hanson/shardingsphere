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

package org.apache.shardingsphere.spring.boot.datasource;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.infra.datasource.pool.creator.DataSourcePoolCreator;
import org.apache.shardingsphere.infra.datasource.props.DataSourceProperties;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.sharding.support.InlineExpressionParser;
import org.apache.shardingsphere.spring.boot.util.PropertyUtil;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.util.StringUtils;

import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data source map setter.
 * 数据源映射设置工具类，用于根据 Spring Boot 的环境配置动态构建数据源映射。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSourceMapSetter {
    /**
     * yml 配置文件中数据源配置的前缀
     * 示例：spring.shardingsphere.datasource.
     */
    private static final String PREFIX = "spring.shardingsphere.datasource.";
    /**
     * 单个数据源名称字段（可能存在于 yml 中）
     */
    private static final String DATA_SOURCE_NAME = "name";
    /**
     * 多个数据源名称字段（推荐使用该字段）
     */
    private static final String DATA_SOURCE_NAMES = "names";
    /**
     * 数据源类型字段
     */
    private static final String DATA_SOURCE_TYPE = "type";
    /**
     * JNDI 数据源名称字段
     */
    private static final String JNDI_NAME = "jndi-name";

    /**
     * 获取数据源映射表（逻辑库名 -> DataSource 实例）
     *
     * @param environment Spring Boot 的 Environment 环境对象
     * @return 数据源名称与对应 DataSource 的映射
     */
    public static Map<String, DataSource> getDataSourceMap(final Environment environment) {
        Map<String, DataSource> result = new LinkedHashMap<>();
        // 遍历所有配置的数据源名称，逐个构造 DataSource 并加入结果集
        for (String each : getDataSourceNames(environment)) {
            try {
                result.put(each, getDataSource(environment, each));
            } catch (final ReflectiveOperationException ex) {
                throw new ShardingSphereException("Can't find data source type.", ex);
            } catch (final NamingException ex) {
                throw new ShardingSphereException("Can't find JNDI data source.", ex);
            }
        }
        return result;
    }

    /**
     * 解析 yml 中配置的数据源名称
     * 支持 name（单数据源）或 names（多数据源，支持 inline 表达式）
     *
     * @param environment Spring Boot 环境
     * @return 数据源名称列表
     */
    private static List<String> getDataSourceNames(final Environment environment) {
        StandardEnvironment standardEnv = (StandardEnvironment) environment;
        standardEnv.setIgnoreUnresolvableNestedPlaceholders(true);
        String dataSourceNames = standardEnv.getProperty(PREFIX + DATA_SOURCE_NAME);
        if (StringUtils.isEmpty(dataSourceNames)) {
            dataSourceNames = standardEnv.getProperty(PREFIX + DATA_SOURCE_NAMES);
        }
        // 使用 Inline 表达式解析器处理 names 字符串，如 ds_${0..1} -> [ds_0, ds_1]
        return new InlineExpressionParser(dataSourceNames).splitAndEvaluate();
    }

    /**
     * 获取指定名称的数据源
     *
     * @param environment    Spring Boot 环境
     * @param dataSourceName 数据源逻辑名称
     * @return 构建好的 DataSource 实例
     * @throws ReflectiveOperationException 类型解析错误
     * @throws NamingException              JNDI 查找失败
     */
    @SuppressWarnings("unchecked")
    private static DataSource getDataSource(final Environment environment, final String dataSourceName) throws ReflectiveOperationException, NamingException {
        // 将 yml 中某个数据源配置块读取为 Map 对象
        Map<String, Object> dataSourceProps = PropertyUtil.handle(environment, String.join("", PREFIX, dataSourceName), Map.class);
        // 检查该数据源配置是否存在
        Preconditions.checkState(!dataSourceProps.isEmpty(), "Wrong datasource [%s] properties.", dataSourceName);
        // 若配置了 JNDI 数据源，则通过 JNDI 查找方式获取
        if (dataSourceProps.containsKey(JNDI_NAME)) {
            return getJNDIDataSource(dataSourceProps.get(JNDI_NAME).toString());
        }
        // 否则使用连接池属性创建 DataSource 实例
        return DataSourcePoolCreator.create(new DataSourceProperties(dataSourceProps.get(DATA_SOURCE_TYPE).toString(), PropertyUtil.getCamelCaseKeys(dataSourceProps)));
    }

    /**
     * 获取 JNDI 数据源
     *
     * @param jndiName JNDI 名称
     * @return JNDI 数据源实例
     * @throws NamingException JNDI 查找异常
     */
    private static DataSource getJNDIDataSource(final String jndiName) throws NamingException {
        JndiObjectFactoryBean bean = new JndiObjectFactoryBean();
        bean.setResourceRef(true);
        bean.setJndiName(jndiName);
        bean.setProxyInterface(DataSource.class);
        bean.afterPropertiesSet();
        // 获取真实目标对象（可能是代理对象）
        return (DataSource) AopProxyUtils.getTarget(bean.getObject());
    }
}
