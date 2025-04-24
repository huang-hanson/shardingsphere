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

package org.apache.shardingsphere.sql.parser.core.database.visitor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.apache.shardingsphere.sql.parser.exception.SQLParsingException;
import org.apache.shardingsphere.sql.parser.spi.SQLVisitorFacade;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatementType;

import java.util.Properties;

/**
 * SQL 访问器工厂类。
 *
 * 作用：根据数据库类型、访问器类型、SQL 类型规则，动态创建对应的 ParseTreeVisitor。
 * 使用反射机制，支持多种 SQL 类型（如 DML/DDL/TCL 等）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SQLVisitorFactory {

    /**
     * 创建 SQL Parse Tree 访问器实例。
     *
     * @param databaseType 数据库类型，如 MySQL、PostgreSQL
     * @param visitorType 访问器类型，如 STATEMENT
     * @param visitorRule 访问规则（封装了 SQL 类型）
     * @param props 访问器配置属性
     * @param <T> 返回结果类型
     * @return ParseTreeVisitor 解析器
     */
    public static <T> ParseTreeVisitor<T> newInstance(final String databaseType, final String visitorType, final SQLVisitorRule visitorRule, final Properties props) {
        // 获取数据库 + 访问器类型 对应的访问器外观类（Facade）
        SQLVisitorFacade facade = SQLVisitorFacadeRegistry.getInstance().getSQLVisitorFacade(databaseType, visitorType);
        // 根据 SQL 类型创建具体访问器
        return createParseTreeVisitor(facade, visitorRule.getType(), props);
    }

    /**
     * 根据 SQL 类型，创建对应的 ParseTreeVisitor。
     *
     * @param visitorFacade SQLVisitor 外观类
     * @param type SQL 类型（如 DML/DDL）
     * @param props 配置参数
     * @param <T> 返回类型
     * @return SQL 树访问器
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows(ReflectiveOperationException.class)
    private static <T> ParseTreeVisitor<T> createParseTreeVisitor(final SQLVisitorFacade visitorFacade, final SQLStatementType type, final Properties props) {
        switch (type) {
            case DML:
                // 创建 DML（增删改）类型访问器
                return (ParseTreeVisitor<T>) visitorFacade.getDMLVisitorClass().getConstructor(Properties.class).newInstance(props);
            case DDL:
                // 创建 DDL（建表、修改表等）访问器
                return (ParseTreeVisitor<T>) visitorFacade.getDDLVisitorClass().getConstructor(Properties.class).newInstance(props);
            case TCL:
                // 创建 TCL（事务控制，如 COMMIT）访问器
                return (ParseTreeVisitor<T>) visitorFacade.getTCLVisitorClass().getConstructor(Properties.class).newInstance(props);
            case DCL:
                // 创建 DCL（权限控制）访问器
                return (ParseTreeVisitor<T>) visitorFacade.getDCLVisitorClass().getConstructor(Properties.class).newInstance(props);
            case DAL:
                // 创建 DAL（数据查询/操作，如 SHOW TABLES）访问器
                return (ParseTreeVisitor<T>) visitorFacade.getDALVisitorClass().getConstructor(Properties.class).newInstance(props);
            case RL:
                // 创建 RL（资源管理）访问器（如 RULE、RESOURCE 等）
                return (ParseTreeVisitor<T>) visitorFacade.getRLVisitorClass().getConstructor(Properties.class).newInstance(props);
            default:
                throw new SQLParsingException("Can not support SQL statement type: `%s`", type);
        }
    }
}
