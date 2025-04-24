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

package org.apache.shardingsphere.sql.parser.sql.common.statement.dml;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.assignment.InsertValuesSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.column.InsertColumnsSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.subquery.SubquerySegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.table.SimpleTableSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.AbstractSQLStatement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;

/**
* Insert statement.
*
* 表示 SQL 中的 INSERT 语句的抽象语法结构类。
* 这是一个抽象类，供具体的 INSERT 实现类继承使用。
*/
@Getter // Lombok 注解，自动为所有字段生成 getter 方法
@Setter // Lombok 注解，自动为所有字段生成 setter 方法
@ToString // Lombok 注解，自动生成 toString 方法，输出所有字段内容
public abstract class InsertStatement extends AbstractSQLStatement implements DMLStatement {

    // 表示插入的目标表
    private SimpleTableSegment table;

    // 表示插入语句中指定的列字段，如：INSERT INTO table (col1, col2)...
    private InsertColumnsSegment insertColumns;

    // 如果是 INSERT ... SELECT 语句，表示其 SELECT 子句部分
    private SubquerySegment insertSelect;

    // 表示 VALUES 部分，可以有多个 InsertValuesSegment（即插入的多行记录）
    private final Collection<InsertValuesSegment> values = new LinkedList<>();

    /**
     * 获取 insertColumns 字段的 Optional 封装形式。
     * 如果 insertColumns 为空，返回 Optional.empty()，否则返回 Optional.of(insertColumns)
     *
     * @return insert columns segment
     */
    public Optional<InsertColumnsSegment> getInsertColumns() {
        return Optional.ofNullable(insertColumns);
    }

    /**
     * 获取插入的列集合。
     * 如果 insertColumns 为空，则返回空集合；否则返回其中包含的列段列表。
     *
     * @return columns
     */
    public Collection<ColumnSegment> getColumns() {
        return null == insertColumns ? Collections.emptyList() : insertColumns.getColumns();
    }

    /**
     * 获取 insertSelect 字段的 Optional 封装形式。
     * 用于判断是否为 INSERT ... SELECT 类型语句。
     *
     * @return insert select segment
     */
    public Optional<SubquerySegment> getInsertSelect() {
        return Optional.ofNullable(insertSelect);
    }
}
