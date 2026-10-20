package com.baraa.masroof.lab.replica.data.learning

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.bank.learning.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LabLearningStore(private val context: Context) : AutoCloseable {
    private val databaseLazy = lazy {Room.databaseBuilder(context,LearningDatabase::class.java,NAME).build()}
    private val database get()=databaseLazy.value
    val cacheFile = File(context.filesDir,"learning/family-model.json")
    fun loadSeed() = FamilyModelCodec.seed(context.assets.open("ml/family-seed.json").bufferedReader().use {it.readText()})
    fun cached(seed: FamilyModelCodec.Seed): List<HumanFamilyLabel> = if (cacheFile.exists()) FamilyModelCodec.cachedLabels(cacheFile.readText(),seed) else emptyList()
    suspend fun record(row: EngineInterpretationRow) = database.learning().record(row)
    suspend fun saveLabels(rows: List<LearningLabelRow>) { database.learning().replaceLabels(rows) }
    suspend fun labels() = database.learning().labels().filter {it.featuresVersion==FamilyFeatures.VERSION}.map { row ->
        HumanFamilyLabel(row.bankId,row.rawId,row.decisionId,com.baraa.masroof.lab.bank.MessageFamily.valueOf(row.family),row.template,
            FamilyModelCodec.terms(Json.parseToJsonElement(row.featuresJson)),row.sourceAt,row.provenance)
    }
    suspend fun history(rawId: String) = database.learning().history(rawId)
    @Synchronized fun persist(seed: FamilyModelCodec.Seed, labels: List<HumanFamilyLabel>, classifier: FamilyClassifier) {
        cacheFile.parentFile!!.mkdirs()
        val temporary=File(cacheFile.parentFile,cacheFile.name+".tmp")
        temporary.outputStream().use { stream -> stream.write(FamilyModelCodec.cache(seed,labels,classifier).toByteArray(Charsets.UTF_8));stream.fd.sync() }
        try { Files.move(temporary.toPath(),cacheFile.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary.toPath(),cacheFile.toPath(),StandardCopyOption.REPLACE_EXISTING) }
    }
    override fun close() { if (databaseLazy.isInitialized()) database.close() }
    fun invalidate() { close();context.deleteDatabase(NAME);cacheFile.parentFile?.deleteRecursively() }
    companion object { const val NAME="learning.db" }
}
