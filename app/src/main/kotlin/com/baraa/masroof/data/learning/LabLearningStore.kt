package com.baraa.masroof.data.learning

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.ml.family.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LabLearningStore(private val context: Context) : AutoCloseable {
    private val databaseLazy = lazy {Room.databaseBuilder(context,LearningDatabase::class.java,NAME)
        .addMigrations(LearningDatabase.MIGRATION_1_2).build()}
    private val database get()=databaseLazy.value
    private val cacheFile = File(context.filesDir,"learning/family-model.json")
    fun loadSeed() = FamilyModelCodec.seed(requireNotNull(FamilyModel::class.java.getResourceAsStream("/ml/family-seed.json")).bufferedReader().use {it.readText()})
    suspend fun record(row: EngineInterpretationRow) = database.learning().record(row)
    suspend fun saveLabels(rows: List<LearningLabelRow>) { database.learning().replaceLabels(rows) }
    suspend fun saveFeedback(labels: List<LearningLabelRow>, feedback: List<LearningFeedbackRow>) = database.learning().replaceFeedback(labels, feedback)
    suspend fun feedback() = database.learning().feedback()
    suspend fun labels() = database.learning().labels()
    suspend fun interpretations() = database.learning().interpretations()
    suspend fun history(rawId: String) = database.learning().history(rawId)
    @Synchronized fun persist(seed: FamilyModelCodec.Seed, classifier: FamilyClassifier) {
        cacheFile.parentFile!!.mkdirs()
        val temporary=File(cacheFile.parentFile,cacheFile.name+".tmp")
        temporary.outputStream().use { stream -> stream.write(FamilyModelCodec.cache(seed,classifier).toByteArray(Charsets.UTF_8));stream.fd.sync() }
        try { Files.move(temporary.toPath(),cacheFile.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary.toPath(),cacheFile.toPath(),StandardCopyOption.REPLACE_EXISTING) }
    }
    override fun close() { if (databaseLazy.isInitialized()) database.close() }
    fun invalidate() { close();context.deleteDatabase(NAME);cacheFile.parentFile?.deleteRecursively() }
    companion object { const val NAME="learning.db" }
}
