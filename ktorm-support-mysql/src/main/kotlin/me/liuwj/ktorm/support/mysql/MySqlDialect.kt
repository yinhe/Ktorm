/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.liuwj.ktorm.support.mysql

import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.database.SqlDialect
import me.liuwj.ktorm.expression.*
import me.liuwj.ktorm.schema.IntSqlType

/**
 * [SqlDialect] implementation for MySQL database.
 */
@Suppress("ProtectedInFinal", "ProtectedMemberInFinalClass", "NON_FINAL_MEMBER_IN_FINAL_CLASS")
object MySqlDialect : SqlDialect {

    override fun createSqlFormatter(database: Database, beautifySql: Boolean, indentSize: Int): SqlFormatter {
        return MySqlFormatter(database, beautifySql, indentSize)
    }

    private class MySqlFormatter(database: Database, beautifySql: Boolean, indentSize: Int)
        : SqlFormatter(database, beautifySql, indentSize) {

        override fun visit(expr: SqlExpression): SqlExpression {
            val result = when (expr) {
                is InsertOrUpdateExpression -> visitInsertOrUpdate(expr)
                is BulkInsertExpression -> visitBulkInsert(expr)
                else -> super.visit(expr)
            }

            check(result === expr) { "SqlFormatter cannot modify the expression trees." }
            return result
        }

        override fun visitQuerySource(expr: QuerySourceExpression): QuerySourceExpression {
            return when (expr) {
                is NaturalJoinExpression -> visitNaturalJoin(expr)
                else -> super.visitQuerySource(expr)
            }
        }

        override fun writePagination(expr: QueryExpression) {
            newLine(Indentation.SAME)
            write("limit ?, ? ")
            _parameters += ArgumentExpression(expr.offset ?: 0, IntSqlType)
            _parameters += ArgumentExpression(expr.limit ?: Int.MAX_VALUE, IntSqlType)
        }

        protected open fun visitInsertOrUpdate(expr: InsertOrUpdateExpression): InsertOrUpdateExpression {
            write("insert into ${expr.table.name.quoted} (")
            for ((i, assignment) in expr.assignments.withIndex()) {
                if (i > 0) write(", ")
                write(assignment.column.name.quoted)
            }
            write(") values ")
            writeValues(expr.assignments)

            if (expr.updateAssignments.isNotEmpty()) {
                write("on duplicate key update ")
                visitColumnAssignments(expr.updateAssignments)
            }

            return expr
        }

        protected open fun visitBulkInsert(expr: BulkInsertExpression): BulkInsertExpression {
            write("insert into ${expr.table.name.quoted} (")
            for ((i, assignment) in expr.assignments[0].withIndex()) {
                if (i > 0) write(", ")
                write(assignment.column.name.quoted)
            }
            write(") values ")

            for ((i, assignments) in expr.assignments.withIndex()) {
                if (i > 0) {
                    removeLastBlank()
                    write(", ")
                }
                writeValues(assignments)
            }

            return expr
        }

        private fun writeValues(assignments: List<ColumnAssignmentExpression<*>>) {
            write("(")
            visitExpressionList(assignments.map { it.expression as ArgumentExpression })
            removeLastBlank()
            write(") ")
        }

        protected open fun visitNaturalJoin(expr: NaturalJoinExpression): NaturalJoinExpression {
            visitQuerySource(expr.left)
            newLine(Indentation.SAME)
            write("natural join ")
            visitQuerySource(expr.right)
            return expr
        }
    }
}
