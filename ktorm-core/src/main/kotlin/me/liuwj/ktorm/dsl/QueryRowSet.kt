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

package me.liuwj.ktorm.dsl

import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.expression.ColumnExpression
import me.liuwj.ktorm.schema.Column
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.*
import java.sql.Array
import java.sql.Date
import java.util.*
import javax.sql.rowset.CachedRowSet

/**
 * Special implementation of [ResultSet], used to hold the [Query] results for Ktorm.
 *
 * Different from normal result sets, this class provides additional features:
 *
 * - **Available offline:** It’s connection independent, it remains available after the connection closed, and it’s
 * not necessary to be closed after being used. Ktorm creates [QueryRowSet] objects with all data being retrieved from
 * the result set into memory, so we just need to wait for GC to collect them after they are not useful.
 *
 * - **Indexed access operator:** It overloads the indexed access operator, so we can use square brackets `[]` to obtain
 * the value by giving a specific [Column] instance. It’s not easy to get wrong by the benefit of the compiler’s static
 * checking, but we can still use getXxx functions in the [ResultSet] to obtain our results by labels or column indices.
 *
 * This class is implemented based on [CachedRowSet], more details can be found in its documentation.
 *
 * @see Query.rowSet
 * @see CachedRowSet
 */
class QueryRowSet internal constructor(
    private val query: Query,
    private val nativeRowSet: CachedRowSet
) : CachedRowSet by nativeRowSet {

    private val resultLabels by lazy(LazyThreadSafetyMode.NONE) {
        val labels = HashMap<String, Int>()
        val metaData = nativeRowSet.metaData

        for (i in 1..metaData.columnCount) {
            val label = metaData.getColumnLabel(i)
            labels.putIfAbsent(label.toUpperCase(), i)
        }

        labels as Map<String, Int>
    }

    private val queryLabels by lazy(LazyThreadSafetyMode.NONE) {
        query.expression
            .findDeclaringColumns()
            .filter { it.declaredName != null }
            .associate { it.declaredName!! to (it.expression as? ColumnExpression<*>)?.name }
    }

    /**
     * Obtain the value of the specific [Column] instance.
     */
    operator fun <C : Any> get(column: Column<C>): C? {
        // Try to get result by label first.
        if (column.label in queryLabels) {
            return column.sqlType.getResult(this, column.label)
        }

        // Try to find labels by name.
        val labels = queryLabels.filterValues { it == column.name }
        if (labels.size > 1) {
            throw IllegalArgumentException(warningConfusedColumnName(column.name))
        }
        if (labels.size == 1) {
            return column.sqlType.getResult(this, labels.keys.first())
        }

        // Falling through, try to get result by column name directly(select * ).
        return column.sqlType.getResult(this, column.name)
    }

    /**
     * Check if the specific [Column] exists in this result set.
     *
     * Note that if the column exists but its value is null, this function still returns `true`.
     */
    fun hasColumn(column: Column<*>): Boolean {
        // Try to find by label first.
        if (column.label in queryLabels) {
            return true
        }

        // Try to find labels by name.
        val labels = queryLabels.filterValues { it == column.name }
        val found = when (labels.size) {
            0 -> false
            1 -> true
            else -> true.also {
                val logger = Database.global.logger
                if (logger != null && logger.isWarnEnabled()) {
                    logger.warn(warningConfusedColumnName(column.name))
                }
            }
        }

        if (found) {
            return true
        } else {
            // Falling through, search column name in resultLabels directly(select *).
            return column.name.toUpperCase() in resultLabels
        }
    }

    private fun warningConfusedColumnName(name: String): String {
        return "Confused column name, there are more than one column named '$name' in query: \n\n${query.sql}\n"
    }

    /**
     * Maps the given column label to its column index.
     *
     * This function overrides the implementation of [com.sun.rowset.CachedRowSetImpl.findColumn] to fix a legacy bug,
     * we can found this problem on StackOverflow:
     *
     * https://stackoverflow.com/questions/15184709/cachedrowsetimpl-getstring-based-on-column-label-throws-invalid-column-name
     */
    override fun findColumn(columnLabel: String?): Int {
        if (columnLabel == null) {
            throw NullPointerException("columnLabel")
        }
        return resultLabels[columnLabel.toUpperCase()] ?: nativeRowSet.findColumn(columnLabel)
    }

    override fun toCollection(column: String?): Collection<*> {
        return this.toCollection(findColumn(column))
    }

    override fun getString(columnLabel: String?): String? {
        return this.getString(findColumn(columnLabel))
    }

    override fun getBoolean(columnLabel: String?): Boolean {
        return this.getBoolean(findColumn(columnLabel))
    }

    override fun getByte(columnLabel: String?): Byte {
        return this.getByte(findColumn(columnLabel))
    }

    override fun getShort(columnLabel: String?): Short {
        return this.getShort(findColumn(columnLabel))
    }

    override fun getInt(columnLabel: String?): Int {
        return this.getInt(findColumn(columnLabel))
    }

    override fun getLong(columnLabel: String?): Long {
        return this.getLong(findColumn(columnLabel))
    }

    override fun getFloat(columnLabel: String?): Float {
        return this.getFloat(findColumn(columnLabel))
    }

    override fun getDouble(columnLabel: String?): Double {
        return this.getDouble(findColumn(columnLabel))
    }

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun getBigDecimal(columnLabel: String?, scale: Int): BigDecimal? {
        return this.getBigDecimal(findColumn(columnLabel), scale)
    }

    override fun getBytes(columnLabel: String?): ByteArray? {
        return this.getBytes(findColumn(columnLabel))
    }

    override fun getDate(columnLabel: String?): Date? {
        return this.getDate(findColumn(columnLabel))
    }

    override fun getTime(columnLabel: String?): Time? {
        return this.getTime(findColumn(columnLabel))
    }

    override fun getTimestamp(columnLabel: String?): Timestamp? {
        return this.getTimestamp(findColumn(columnLabel))
    }

    override fun getAsciiStream(columnLabel: String?): InputStream? {
        return this.getAsciiStream(findColumn(columnLabel))
    }

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun getUnicodeStream(columnLabel: String?): InputStream? {
        return this.getUnicodeStream(findColumn(columnLabel))
    }

    override fun getBinaryStream(columnLabel: String?): InputStream? {
        return this.getBinaryStream(findColumn(columnLabel))
    }

    override fun getObject(columnLabel: String?): Any? {
        return this.getObject(findColumn(columnLabel))
    }

    override fun getCharacterStream(columnLabel: String?): Reader? {
        return this.getCharacterStream(findColumn(columnLabel))
    }

    override fun getBigDecimal(columnLabel: String?): BigDecimal? {
        return this.getBigDecimal(findColumn(columnLabel))
    }

    override fun columnUpdated(columnName: String?): Boolean {
        return this.columnUpdated(findColumn(columnName))
    }

    override fun updateNull(columnLabel: String?) {
        this.updateNull(findColumn(columnLabel))
    }

    override fun updateBoolean(columnLabel: String?, x: Boolean) {
        this.updateBoolean(findColumn(columnLabel), x)
    }

    override fun updateByte(columnLabel: String?, x: Byte) {
        this.updateByte(findColumn(columnLabel), x)
    }

    override fun updateShort(columnLabel: String?, x: Short) {
        this.updateShort(findColumn(columnLabel), x)
    }

    override fun updateInt(columnLabel: String?, x: Int) {
        this.updateInt(findColumn(columnLabel), x)
    }

    override fun updateLong(columnLabel: String?, x: Long) {
        this.updateLong(findColumn(columnLabel), x)
    }

    override fun updateFloat(columnLabel: String?, x: Float) {
        this.updateFloat(findColumn(columnLabel), x)
    }

    override fun updateDouble(columnLabel: String?, x: Double) {
        this.updateDouble(findColumn(columnLabel), x)
    }

    override fun updateBigDecimal(columnLabel: String?, x: BigDecimal?) {
        this.updateBigDecimal(findColumn(columnLabel), x)
    }

    override fun updateString(columnLabel: String?, x: String?) {
        this.updateString(findColumn(columnLabel), x)
    }

    override fun updateBytes(columnLabel: String?, x: ByteArray?) {
        this.updateBytes(findColumn(columnLabel), x)
    }

    override fun updateDate(columnLabel: String?, x: Date?) {
        this.updateDate(findColumn(columnLabel), x)
    }

    override fun updateTime(columnLabel: String?, x: Time?) {
        this.updateTime(findColumn(columnLabel), x)
    }

    override fun updateTimestamp(columnLabel: String?, x: Timestamp?) {
        this.updateTimestamp(findColumn(columnLabel), x)
    }

    override fun updateAsciiStream(columnLabel: String?, x: InputStream?, length: Int) {
        this.updateAsciiStream(findColumn(columnLabel), x, length)
    }

    override fun updateBinaryStream(columnLabel: String?, x: InputStream?, length: Int) {
        this.updateBinaryStream(findColumn(columnLabel), x, length)
    }

    override fun updateCharacterStream(columnLabel: String?, reader: Reader?, length: Int) {
        this.updateCharacterStream(findColumn(columnLabel), reader, length)
    }

    override fun updateObject(columnLabel: String?, x: Any?, scaleOrLength: Int) {
        this.updateObject(findColumn(columnLabel), x, scaleOrLength)
    }

    override fun updateObject(columnLabel: String?, x: Any?) {
        this.updateObject(findColumn(columnLabel), x)
    }

    override fun getObject(columnLabel: String?, map: Map<String, Class<*>>?): Any? {
        return this.getObject(findColumn(columnLabel), map)
    }

    override fun getRef(columnLabel: String?): Ref? {
        return this.getRef(findColumn(columnLabel))
    }

    override fun getBlob(columnLabel: String?): Blob? {
        return this.getBlob(findColumn(columnLabel))
    }

    override fun getClob(columnLabel: String?): Clob? {
        return this.getClob(findColumn(columnLabel))
    }

    override fun getArray(columnLabel: String?): Array? {
        return this.getArray(findColumn(columnLabel))
    }

    override fun getDate(columnLabel: String?, cal: Calendar?): Date? {
        return this.getDate(findColumn(columnLabel), cal)
    }

    override fun getTime(columnLabel: String?, cal: Calendar?): Time? {
        return this.getTime(findColumn(columnLabel), cal)
    }

    override fun getTimestamp(columnLabel: String?, cal: Calendar?): Timestamp? {
        return this.getTimestamp(findColumn(columnLabel), cal)
    }

    override fun updateRef(columnLabel: String?, x: Ref?) {
        this.updateRef(findColumn(columnLabel), x)
    }

    override fun updateClob(columnLabel: String?, x: Clob?) {
        this.updateClob(findColumn(columnLabel), x)
    }

    override fun updateBlob(columnLabel: String?, x: Blob?) {
        this.updateBlob(findColumn(columnLabel), x)
    }

    override fun updateArray(columnLabel: String?, x: Array?) {
        this.updateArray(findColumn(columnLabel), x)
    }

    override fun getURL(columnLabel: String?): URL? {
        return this.getURL(findColumn(columnLabel))
    }
}
