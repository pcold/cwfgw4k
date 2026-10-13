package com.cwfgw.db

import org.jooq.Record
import org.jooq.TableField

/**
 * For `NOT NULL` columns with a DB `DEFAULT`, which jOOQ types nullable anyway
 * (`withKotlinNotNullRecordAttributes` only narrows the no-default ones).
 */
internal fun <R : Record, T : Any> R.required(field: TableField<R, T?>): T =
    get(field) ?: throw ColumnNullViolation(field)

/** A nullable column reaches this through `?.let` at the call site, not a nullable overload here. */
internal fun <S : Any, T : Any> decoded(
    field: TableField<*, S?>,
    stored: S,
    decode: (S) -> T?,
): T = decode(stored) ?: throw ColumnDomainViolation(field, stored)

/** A type rather than `error(...)` so only the [TableField]'s own label can reach the message. */
internal class ColumnNullViolation(
    field: TableField<*, *>,
) : IllegalStateException("${field.columnLabel} was null")

/** [stored] stays out of [message]: a log writes the message but none of the fields. */
internal class ColumnDomainViolation(
    field: TableField<*, *>,
    val stored: Any,
) : IllegalStateException("${field.columnLabel} held a value outside its domain")

/** The bare-name branch exists only because jOOQ declares `getTable()` nullable; a generated field has one. */
private val TableField<*, *>.columnLabel: String
    get() = table?.let { owner -> "${owner.name}.$name" } ?: name
