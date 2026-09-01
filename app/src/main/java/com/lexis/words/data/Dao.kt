package com.lexis.words.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks ORDER BY position ASC, id ASC")
    suspend fun observeAllOnce(): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun getById(id: Long): BlockEntity?

    @Insert
    suspend fun insert(block: BlockEntity): Long

    @Update
    suspend fun update(block: BlockEntity)

    @Delete
    suspend fun delete(block: BlockEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM blocks")
    suspend fun nextPosition(): Int
}

@Dao
interface WordListDao {
    @Query("SELECT * FROM word_lists WHERE blockId = :blockId ORDER BY position ASC, id ASC")
    fun observeForBlock(blockId: Long): Flow<List<WordListEntity>>

    @Query("SELECT * FROM word_lists ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<WordListEntity>>

    @Query("SELECT * FROM word_lists ORDER BY position ASC, id ASC")
    suspend fun observeAllOnce(): List<WordListEntity>

    @Query("SELECT * FROM word_lists WHERE id = :id")
    suspend fun getById(id: Long): WordListEntity?

    @Insert
    suspend fun insert(list: WordListEntity): Long

    @Update
    suspend fun update(list: WordListEntity)

    @Delete
    suspend fun delete(list: WordListEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM word_lists WHERE blockId = :blockId")
    suspend fun nextPosition(blockId: Long): Int
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE listId = :listId ORDER BY createdAt DESC")
    fun observeForList(listId: Long): Flow<List<WordEntity>>

    @Query("SELECT * FROM words")
    fun observeAll(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words")
    suspend fun observeAllOnce(): List<WordEntity>

    @Query("SELECT * FROM words WHERE listId = :listId")
    suspend fun getForList(listId: Long): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE listId = :listId")
    suspend fun countForList(listId: Long): Int

    @Query(
        """SELECT words.* FROM words
           INNER JOIN word_lists ON words.listId = word_lists.id
           WHERE word_lists.blockId = :blockId"""
    )
    suspend fun getForBlock(blockId: Long): List<WordEntity>

    @Insert
    suspend fun insert(word: WordEntity): Long

    @Insert
    suspend fun insertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    @Delete
    suspend fun delete(word: WordEntity)

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: Long): WordEntity?
}
