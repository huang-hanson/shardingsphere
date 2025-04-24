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

package org.apache.shardingsphere.sql.parser.api;

import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.apache.shardingsphere.sql.parser.core.ParseASTNode;
import org.apache.shardingsphere.sql.parser.core.database.visitor.SQLVisitorFactory;
import org.apache.shardingsphere.sql.parser.core.database.visitor.SQLVisitorRule;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.CommentSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.AbstractSQLStatement;

import java.util.Collection;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * SQLVisitorEngine 是 SQL 解析的访问器，用于将解析出的 AST（抽象语法树）转换为具体的 SQLStatement 对象。
 * 它基于 Visitor 模式实现，通过解析器访问器规则来还原 SQL 的语义结构。
 */
@RequiredArgsConstructor
public final class SQLVisitorEngine {

    // 数据库类型（如 MySQL、PostgreSQL、Oracle 等）
    private final String databaseType;

    // 访问器类型，通常是 "STATEMENT"，表示将 AST 转换为 SQLStatement
    private final String visitorType;

    // 是否解析注释（如 /* 注释内容 */ 或 -- 单行注释）
    private final boolean isParseComment;

    // 附加属性，可供访问器扩展使用
    private final Properties props;

    /**
     * 将解析后的 AST 节点转化为具体的 SQLStatement 对象（或其他类型 T）。
     *
     * @param parseASTNode SQL 的抽象语法树节点（ParseTree 的包装）
     * @param <T> SQL 访问器返回的结果类型
     * @return AST 转换后的 SQLStatement 或其他结构化对象
     */
    public <T> T visit(final ParseASTNode parseASTNode) {
        // 根据解析树根节点的类型，构造对应的访问器
        ParseTreeVisitor<T> visitor = SQLVisitorFactory.newInstance(
                databaseType,                                                       // 数据库类型
                visitorType,                                                        // 访问器类型（一般为 "STATEMENT"）
                SQLVisitorRule.valueOf(parseASTNode.getRootNode().getClass()),      // 访问器规则
props                                                                               // 属性参数
        );
        // 使用访问器访问 AST 的根节点，生成语义化 SQLStatement 对象
        T result = parseASTNode.getRootNode().accept(visitor);
        // 如果开启了解析注释，则提取 SQL 中的注释并附加到 SQLStatement 中
        if (isParseComment) {
            appendSQLComments(parseASTNode, result);
        }
        return result;
    }

    /**
     * 将 AST 中提取出的注释追加到 SQLStatement 对象中。
     *
     * @param parseASTNode 解析后的 AST 节点
     * @param visitResult 访问器返回的结果对象（需为 AbstractSQLStatement 类型）
     * @param <T> 泛型参数
     */
    private <T> void appendSQLComments(final ParseASTNode parseASTNode, final T visitResult) {
        // 判断访问结果是否为 AbstractSQLStatement 类型
        if (visitResult instanceof AbstractSQLStatement) {
            // 遍历隐藏 token（注释），将其转换为 CommentSegment，并加入 SQLStatement 中
            Collection<CommentSegment> commentSegments = parseASTNode.getHiddenTokens().stream().map(
                each -> new CommentSegment(each.getText(), each.getStartIndex(), each.getStopIndex())).collect(Collectors.toList());
            // 将注释段加入 SQLStatement 对象中
            ((AbstractSQLStatement) visitResult).getCommentSegments().addAll(commentSegments);
        }
    }
}
