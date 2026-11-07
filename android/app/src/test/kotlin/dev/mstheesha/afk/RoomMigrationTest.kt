package dev.mstheesha.afk

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class RoomMigrationTest {

    @Test
    fun migrationVersionMatches() {
        assertEquals(7, AfkDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, AfkDatabase.MIGRATION_7_8.endVersion)
    }

    @Test
    fun migrationExecutesExpectedAlterStatements() {
        val executedStatements = mutableListOf<String>()

        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        }

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler,
        ) as SupportSQLiteDatabase

        AfkDatabase.MIGRATION_7_8.migrate(dbProxy)

        assertEquals(5, executedStatements.size)
        assertTrue(executedStatements.any { it.contains("disconnectOnLowHealth INTEGER NOT NULL DEFAULT 0") })
        assertTrue(executedStatements.any { it.contains("lowHealthThreshold INTEGER NOT NULL DEFAULT 6") })
        assertTrue(executedStatements.any { it.contains("disconnectOnDeath INTEGER NOT NULL DEFAULT 0") })
        assertTrue(executedStatements.any { it.contains("disconnectOnKick INTEGER NOT NULL DEFAULT 0") })
        assertTrue(executedStatements.any { it.contains("notifyOnMention INTEGER NOT NULL DEFAULT 1") })
    }
}
