package com.baraa.masroof.lab.beta.replica

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** All symbolic production storage names resolve inside the Lab namespace. */
class LabStorageContext(context: Context, private val prefix: String = PREFIX) : ContextWrapper(context) {
    init { require(prefix in setOf(PREFIX, "lab_reference_")) }
    override fun getApplicationContext(): Context = this
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        LabStorageContext(baseContext.createConfigurationContext(overrideConfiguration), prefix)
    override fun getDatabasePath(name: String): File {
        val file = File(name)
        if (file.isAbsolute) {
            require(file.canonicalFile.parentFile == baseContext.getDatabasePath("unused").parentFile?.canonicalFile && file.name.startsWith(prefix)) { "Database outside Lab" }
            return file
        }
        require('/' !in name && '\\' !in name)
        return baseContext.getDatabasePath(if (name.startsWith(prefix)) name else prefix + name)
    }
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory, PRESERVE_CORRUPT)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory, PRESERVE_CORRUPT)
    override fun deleteDatabase(name: String): Boolean = baseContext.deleteDatabase(getDatabasePath(name).absolutePath)
    override fun databaseList(): Array<String> = baseContext.databaseList().filter { it.startsWith(prefix) }.toTypedArray()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        require('/' !in name && '\\' !in name)
        val scoped = if (name.startsWith(prefix)) name else prefix + name
        synchronized(PREF_LOCK) {
            val index = baseContext.getSharedPreferences(prefix + "storage_index", MODE_PRIVATE)
            val names = index.getStringSet("names", emptySet())!!.toSet()
            if (scoped !in names) check(index.edit().putStringSet("names", names + scoped).commit())
        }
        return baseContext.getSharedPreferences(scoped, mode)
    }
    override fun deleteSharedPreferences(name: String): Boolean = baseContext.deleteSharedPreferences(if (name.startsWith(prefix)) name else prefix + name)
    private fun directory(parent: File, name: String = prefix.removeSuffix("_")): File = File(parent, name).apply { mkdirs() }
    override fun getFilesDir(): File = directory(baseContext.filesDir)
    override fun getCacheDir(): File = directory(baseContext.cacheDir)
    override fun getCodeCacheDir(): File = directory(baseContext.codeCacheDir)
    override fun getNoBackupFilesDir(): File = directory(baseContext.noBackupFilesDir)
    override fun getDir(name: String, mode: Int): File {
        require('/' !in name && '\\' !in name); return directory(filesDir, name)
    }
    override fun getExternalFilesDir(type: String?): File? = baseContext.getExternalFilesDir(type)?.let { directory(it) }
    override fun getExternalFilesDirs(type: String?): Array<File> = baseContext.getExternalFilesDirs(type).mapNotNull { item -> item?.let { directory(it) } }.toTypedArray()
    override fun getExternalCacheDir(): File? = baseContext.externalCacheDir?.let { directory(it) }
    override fun getExternalCacheDirs(): Array<File> = baseContext.externalCacheDirs.mapNotNull { item -> item?.let { directory(it) } }.toTypedArray()
    companion object {
        const val PREFIX = "lab_replica_"
        internal const val INDEX = "lab_replica_storage_index"
        private val PREF_LOCK = Any()
        // Android's default corruption handler deletes the file before the recovery UI can open.
        private val PRESERVE_CORRUPT = DatabaseErrorHandler { throw android.database.sqlite.SQLiteDatabaseCorruptException("Lab database preserved for explicit recovery") }
    }
}
