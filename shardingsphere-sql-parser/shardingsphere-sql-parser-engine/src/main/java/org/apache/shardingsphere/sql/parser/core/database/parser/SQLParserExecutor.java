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

package org.apache.shardingsphere.sql.parser.core.database.parser;

import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.apache.shardingsphere.sql.parser.api.parser.SQLParser;
import org.apache.shardingsphere.sql.parser.core.ParseASTNode;
import org.apache.shardingsphere.sql.parser.core.SQLParserFactory;
import org.apache.shardingsphere.sql.parser.exception.SQLParsingException;
import org.apache.shardingsphere.sql.parser.spi.DatabaseTypedSQLParserFacade;

/**
 * SQL 解析执行器（SQLParserExecutor）。
 *
 * 作用：
 *  - 使用 ANTLR 执行 SQL 的词法和语法解析。
 *  - 使用两阶段解析（SLL → LL）提升性能和容错能力。
 *  - 解析结果为 ParseASTNode（抽象语法树）。
 */
@RequiredArgsConstructor
public final class SQLParserExecutor {

    // 当前数据库类型（如 MySQL、PostgreSQL、Oracle 等）
    private final String databaseType;

    /**
     * 执行 SQL 的解析过程，返回 AST 节点。
     *
     * @param sql 要解析的 SQL 语句
     * @return SQL 解析结果（AST）
     */
    public ParseASTNode parse(final String sql) {
        // 两阶段解析
        ParseASTNode result = twoPhaseParse(sql);

        // 如果解析结果的根节点是错误节点，则抛出异常
        if (result.getRootNode() instanceof ErrorNode) {
            throw new SQLParsingException("Unsupported SQL of `%s`", sql);
        }
        return result;
    }

    /**
     * 两阶段 SQL 解析策略（SLL -> LL）。
     *
     * 原理：
     *  - 首先使用 SLL 模式解析，速度快，但对语法容错能力弱。
     *  - 如果 SLL 模式失败（ParseCancellationException），则回退使用 LL 模式，容错强但速度慢。
     *
     * @param sql SQL 语句
     * @return 解析得到的 AST 节点
     */
    private ParseASTNode twoPhaseParse(final String sql) {
        // 从 SPI 注册表中根据数据库类型获取解析器门面类（包括词法和语法类）
        DatabaseTypedSQLParserFacade sqlParserFacade = DatabaseTypedSQLParserFacadeRegistry.getFacade(databaseType);
        // 创建具体的 ANTLR SQL 解析器
        SQLParser sqlParser = SQLParserFactory.newInstance(
                sql,
                sqlParserFacade.getLexerClass(),    // 获取词法分析器类
                sqlParserFacade.getParserClass()    // 获取语法分析器类
        );
        try {
            // 第一次尝试使用 SLL（单向预测）模式，效率更高
            ((Parser) sqlParser).getInterpreter().setPredictionMode(PredictionMode.SLL);
            return (ParseASTNode) sqlParser.parse();
        } catch (final ParseCancellationException ex) {
            // SLL 失败后，回退为 LL（上下文无关文法）模式，提高容错能力
            ((Parser) sqlParser).reset();
            ((Parser) sqlParser).getInterpreter().setPredictionMode(PredictionMode.LL);
            try {
                return (ParseASTNode) sqlParser.parse();
            } catch (final ParseCancellationException e) {
                // LL 模式也失败，说明 SQL 语法非法，抛出解析异常
                throw new SQLParsingException("You have an error in your SQL syntax");
            }
        }
    }
}
