package com.example.merrygoround.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import android.content.Context


@Database(
    entities = [
        UserEntity::class,
        SessionEntity::class,
        GroupEntity::class,
        MembershipEntity::class,
        InvitationEntity::class,
        CycleEntity::class,
        ContributionEntity::class,
        PayoutEntity::class,
        AuditEventEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun groupDao(): GroupDao
    abstract fun membershipDao(): MembershipDao
    abstract fun invitationDao(): InvitationDao
    abstract fun cycleDao(): CycleDao
    abstract fun contributionDao(): ContributionDao
    abstract fun payoutDao(): PayoutDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val NAME = "merry_go_round.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()

        /** In-memory database for tests. */
        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        fun destroyInstance() {
            instance?.close()
            instance = null
        }
    }
}
