package com.chronie.homemoney.di

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chronie.homemoney.data.local.AppDatabase
import com.chronie.homemoney.data.local.DatabaseMigrations
import com.chronie.homemoney.data.local.dao.ChatDao
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.MemberDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.nio.charset.StandardCharsets
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Hilt DI module for database dependencies.
 *
 * Provides:
 * - SQLCipher-encrypted Room database with auto-generated passphrases
 *   stored in EncryptedSharedPreferences for defense-in-depth.
 * - All DAO instances (Expense, Member, SyncQueue, Budget).
 *
 * The passphrase is generated once on first launch (32 random chars),
 * stored securely via Android Keystore-backed encryption, and reused
 * across app restarts.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    init {
        System.loadLibrary("sqlcipher")
    }
    
    private const val DB_PASSPHRASE_KEY = "db_passphrase"
    private const val ENCRYPTED_PREFS_FILE = "secure_prefs"
    
    /**
     * Provides database passphrase instance
     * Uses EncryptedSharedPreferences for secure storage
     */
    // security-crypto is deprecated as a whole (no Jetpack replacement yet);
    // migrating away requires a custom Keystore/DataStore solution.
    @Suppress("DEPRECATION")
    @Provides
    @Singleton
    fun provideDatabasePassphrase(@ApplicationContext context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        // Get or generate passphrase
        var passphrase = sharedPreferences.getString(DB_PASSPHRASE_KEY, null)
        if (passphrase == null) {
            // Generate random passphrase
            passphrase = generateRandomPassphrase()
            sharedPreferences.edit { putString(DB_PASSPHRASE_KEY, passphrase) }
        }
        
        return passphrase.toByteArray(StandardCharsets.UTF_8)
    }
    
    /**
     * Generates random passphrase
     */
    private fun generateRandomPassphrase(): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()"
        return (1..32)
            .map { charset.random() }
            .joinToString("")
    }
    
    /**
     * Provides AppDatabase instance
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphrase: ByteArray
    ): AppDatabase {
        val factory = SupportOpenHelperFactory(passphrase)
        
        return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                AppDatabase.DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(*DatabaseMigrations.getAllMigrations())
            .fallbackToDestructiveMigration(false)
            .build()
    }
    
    /**
     * Provides ExpenseDao instance
     */
    @Provides
    fun provideExpenseDao(database: AppDatabase): ExpenseDao {
        return database.expenseDao()
    }
    
    /**
     * Provides MemberDao instance
     */
    @Provides
    fun provideMemberDao(database: AppDatabase): MemberDao {
        return database.memberDao()
    }
    
    /**
     * Provides SyncQueueDao instance
     */
    @Provides
    fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }
    
    /**
     * Provides BudgetDao instance
     */
    @Provides
    fun provideBudgetDao(database: AppDatabase): com.chronie.homemoney.data.local.dao.BudgetDao {
        return database.budgetDao()
    }

    /** Chat history DAO (local-only tables, protected by the same SQLCipher key). */
    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }
}
