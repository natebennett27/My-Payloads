package com.trio.today.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trio.today.domain.Bucket
import com.trio.today.domain.Recurrence
import com.trio.today.domain.Task
import com.trio.today.domain.TimeAnchor

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val bucket: String,
    val createdAt: Long,
    val remindAt: Long?,
    val timeAnchor: String,
    /** Recurrence serialised by [RecurrenceCodec]; null when the task does not repeat. */
    val recurrence: String?,
    val completedAt: Long?,
    val deferCount: Int,
    val sortOrder: Int,
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    bucket = runCatching { Bucket.valueOf(bucket) }.getOrDefault(Bucket.TODAY),
    createdAt = createdAt,
    remindAt = remindAt,
    timeAnchor = runCatching { TimeAnchor.valueOf(timeAnchor) }.getOrDefault(TimeAnchor.ANYTIME),
    recurrence = recurrence?.let(RecurrenceCodec::decode),
    completedAt = completedAt,
    deferCount = deferCount,
    sortOrder = sortOrder,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    bucket = bucket.name,
    createdAt = createdAt,
    remindAt = remindAt,
    timeAnchor = timeAnchor.name,
    recurrence = recurrence?.let(RecurrenceCodec::encode),
    completedAt = completedAt,
    deferCount = deferCount,
    sortOrder = sortOrder,
)

/**
 * Stores a [Recurrence] as a short string.
 *
 * A hand-rolled codec rather than a serialisation library: three shapes do not
 * justify the dependency, and the format stays readable in a database dump.
 */
object RecurrenceCodec {

    fun encode(recurrence: Recurrence): String = when (recurrence) {
        is Recurrence.Daily -> "daily"
        is Recurrence.Weekly -> "weekly:" + recurrence.days.joinToString(",") { it.value.toString() }
        is Recurrence.Monthly -> "monthly:${recurrence.dayOfMonth}"
    }

    fun decode(raw: String): Recurrence? = runCatching {
        when {
            raw == "daily" -> Recurrence.Daily

            raw.startsWith("weekly:") -> {
                val days = raw.removePrefix("weekly:")
                    .split(",")
                    .mapNotNull { it.toIntOrNull() }
                    .mapNotNull { value -> java.time.DayOfWeek.entries.firstOrNull { it.value == value } }
                    .toSet()
                if (days.isEmpty()) null else Recurrence.Weekly(days)
            }

            raw.startsWith("monthly:") ->
                raw.removePrefix("monthly:").toIntOrNull()
                    ?.takeIf { it in 1..31 }
                    ?.let(Recurrence::Monthly)

            else -> null
        }
    }.getOrNull()
}
