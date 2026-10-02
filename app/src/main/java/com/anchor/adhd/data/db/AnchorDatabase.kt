package com.anchor.adhd.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.anchor.adhd.data.model.AiJobEntity
import com.anchor.adhd.data.model.AiJobStatus
import com.anchor.adhd.data.model.AiJobType
import com.anchor.adhd.data.model.AssignmentEntity
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.CbtCardEntity
import com.anchor.adhd.data.model.CbtMomentTag
import com.anchor.adhd.data.model.ChatTurnEntity
import com.anchor.adhd.data.model.ChatUndoOpEntity
import com.anchor.adhd.data.model.CompanionStateEntity
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.FocusEndTag
import com.anchor.adhd.data.model.FocusGardenEntity
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.model.HabitAutoSource
import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.CheckInEntity
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.TemptationBundleEntity
import com.anchor.adhd.data.model.FunLinkEntity
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        AssignmentEntity::class,
        CalendarEventEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        ReplanItemEntity::class,
        FocusSessionEntity::class,
        BlockRuleEntity::class,
        CheckInEntity::class,
        CompanionStateEntity::class,
        FocusGardenEntity::class,
        AiJobEntity::class,
        CbtCardEntity::class,
        TemptationBundleEntity::class,
        HabitEntity::class,
        HabitCompletionEntity::class,
        ChatTurnEntity::class,
        ChatUndoOpEntity::class,
        FunLinkEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(AnchorConverters::class)
abstract class AnchorDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun routineDao(): RoutineDao
    abstract fun replanDao(): ReplanDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun checkInDao(): CheckInDao
    abstract fun companionDao(): CompanionDao
    abstract fun aiJobDao(): AiJobDao
    abstract fun cbtCardDao(): CbtCardDao
    abstract fun temptationBundleDao(): TemptationBundleDao
    abstract fun habitDao(): HabitDao
    abstract fun chatTurnDao(): ChatTurnDao
    abstract fun chatUndoDao(): ChatUndoDao
    abstract fun funLinkDao(): FunLinkDao

    companion object {
        @Volatile
        private var instance: AnchorDatabase? = null

        fun getInstance(context: Context): AnchorDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnchorDatabase::class.java,
                    "anchor.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Wordle', 'https://www.nytimes.com/games/wordle', '🟩', 0, 1)")
                            db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Connections', 'https://www.nytimes.com/games/connections', '🟪', 1, 1)")
                            db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Chess Puzzles', 'https://lichess.org/training', '♟️', 2, 1)")
                            db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Contexto', 'https://contexto.me', '🧩', 3, 1)")
                        }
                    })
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class AnchorConverters {
    @TypeConverter fun fromInboxState(v: InboxState) = v.name
    @TypeConverter fun toInboxState(v: String) = enumOrDefault(v, InboxState.TODAY)

    @TypeConverter fun fromDifficulty(v: TaskDifficulty) = v.name
    @TypeConverter fun toDifficulty(v: String) = enumOrDefault(v, TaskDifficulty.MEDIUM)

    @TypeConverter fun fromEnergy(v: EnergyLevel) = v.name
    @TypeConverter fun toEnergy(v: String) = enumOrDefault(v, EnergyLevel.OK)

    @TypeConverter fun fromBlockType(v: BlockRuleType) = v.name
    @TypeConverter fun toBlockType(v: String) = enumOrDefault(v, BlockRuleType.SESSION)

    @TypeConverter fun fromAiType(v: AiJobType) = v.name
    @TypeConverter fun toAiType(v: String) = enumOrDefault(v, AiJobType.BREAKDOWN)

    @TypeConverter fun fromAiStatus(v: AiJobStatus) = v.name
    @TypeConverter fun toAiStatus(v: String) = enumOrDefault(v, AiJobStatus.PENDING)

    @TypeConverter fun fromFocusEndTag(v: FocusEndTag?) = v?.name
    @TypeConverter fun toFocusEndTag(v: String?) = v?.let { enumOrNull<FocusEndTag>(it) }

    @TypeConverter fun fromCbtMomentTag(v: CbtMomentTag) = v.name
    @TypeConverter fun toCbtMomentTag(v: String) = enumOrDefault(v, CbtMomentTag.GENERAL)

    @TypeConverter fun fromHabitScheduleType(v: HabitScheduleType) = v.name
    @TypeConverter fun toHabitScheduleType(v: String) = enumOrDefault(v, HabitScheduleType.DAILY)

    @TypeConverter fun fromHabitAutoSource(v: HabitAutoSource) = v.name
    @TypeConverter fun toHabitAutoSource(v: String) = enumOrDefault(v, HabitAutoSource.NONE)

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        enumOrNull<T>(value) ?: default

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        runCatching { enumValueOf<T>(value) }.getOrNull()
}
