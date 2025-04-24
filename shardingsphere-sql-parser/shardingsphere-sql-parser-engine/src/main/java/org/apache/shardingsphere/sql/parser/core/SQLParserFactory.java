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

package org.apache.shardingsphere.sql.parser.core;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CodePointBuffer;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;
import org.apache.shardingsphere.sql.parser.api.parser.SQLLexer;
import org.apache.shardingsphere.sql.parser.api.parser.SQLParser;

import java.nio.CharBuffer;

/**
 * SQL 解析器工厂类（用于动态创建 ANTLR SQL 解析器对象）。
 *
 * 特点：
 *  - 禁止实例化（私有构造器）
 *  - 使用反射机制创建 Lexer 和 Parser
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SQLParserFactory {

    /**
     * 创建 SQL 解析器实例（组合词法分析器和语法分析器）。
     *
     * @param sql SQL 原始文本
     * @param lexerClass 词法分析器类（ANTLR 生成）
     * @param parserClass 语法分析器类（ANTLR 生成）
     * @return SQL 解析器实例（Parser）
     */
    public static SQLParser newInstance(final String sql, final Class<? extends SQLLexer> lexerClass, final Class<? extends SQLParser> parserClass) {
        // 创建 TokenStream 并构造 SQLParser
        return createSQLParser(createTokenStream(sql, lexerClass), parserClass);
    }

    /**
     * 反射创建 SQL 语法分析器（Parser）。
     *
     * @param tokenStream 词法分析器输出的 Token 流
     * @param parserClass 语法分析器类
     * @return SQLParser 实例
     */
    @SneakyThrows(ReflectiveOperationException.class)
    private static SQLParser createSQLParser(final TokenStream tokenStream, final Class<? extends SQLParser> parserClass) {
        // 使用 TokenStream 构造语法分析器
        SQLParser result = parserClass.getConstructor(TokenStream.class).newInstance(tokenStream);
        // 设置错误处理策略为 BailErrorStrategy，遇错立即抛异常（性能更高，便于回退 LL 模式）
        ((Parser) result).setErrorHandler(new BailErrorStrategy());
        // 移除默认控制台错误监听器，避免控制台输出干扰
        ((Parser) result).removeErrorListener(ConsoleErrorListener.INSTANCE);
        return result;
    }

    /**
     * 创建 ANTLR 所需的 TokenStream。
     *
     * @param sql SQL 文本
     * @param lexerClass 词法分析器类
     * @return Token 流对象
     */
    @SneakyThrows(ReflectiveOperationException.class)
    private static TokenStream createTokenStream(final String sql, final Class<? extends SQLLexer> lexerClass) {
        // 构造词法分析器（Lexer）
        Lexer lexer = (Lexer) lexerClass.getConstructor(CharStream.class).newInstance(getSQLCharStream(sql));
        // 同样移除默认的 ConsoleErrorListener
        lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
        // 将词法分析器输出的词元封装为 TokenStream，供语法分析器使用
        return new CommonTokenStream(lexer);
    }

    /**
     * 将字符串 SQL 转换为 ANTLR 所需的 CharStream。
     *
     * @param sql SQL 文本
     * @return 字符流
     */
    private static CharStream getSQLCharStream(final String sql) {
        // 创建 ANTLR 兼容的字符缓冲区
        CodePointBuffer buffer = CodePointBuffer.withChars(CharBuffer.wrap(sql.toCharArray()));
        // 基于缓冲区构造字符流
        return CodePointCharStream.fromBuffer(buffer);
    }
}
