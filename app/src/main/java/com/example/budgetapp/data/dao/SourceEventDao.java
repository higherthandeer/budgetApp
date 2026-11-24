package com.example.budgetapp.data.dao;

import androidx.room.*;

import java.util.List;

import com.example.budgetapp.data.entity.SourceEventEntity;

@Dao
public interface SourceEventDao {
    @Query("SELECT COUNT(*) FROM source_event WHERE process_state IN ('NEW','ERROR')")
    int pendingCount();

    @Query("SELECT * FROM source_event WHERE process_state='REVIEW' ORDER BY captured_at_ms,id")
    List<SourceEventEntity> listReview();

    @Query("SELECT * FROM source_event WHERE process_state='REVIEW' ORDER BY captured_at_ms,id")
    androidx.lifecycle.LiveData<List<SourceEventEntity>> observeReview();

    @Query("UPDATE source_event SET transaction_id=:primaryId WHERE transaction_id=:duplicateId")
    int transferEvidence(String primaryId, String duplicateId);

    @Query("SELECT * FROM source_event WHERE raw_payload IS NOT NULL ORDER BY captured_at_ms DESC,id")
    List<SourceEventEntity> rawEvents();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(SourceEventEntity event);

    @Query("SELECT * FROM source_event WHERE source_type=:sourceType AND event_key=:eventKey")
    SourceEventEntity findByKey(String sourceType, String eventKey);

    @Update
    int update(SourceEventEntity event);

    @Query("SELECT * FROM source_event WHERE id=:id")
    SourceEventEntity find(String id);

    @Query("SELECT * FROM source_event WHERE process_state IN ('NEW','ERROR') ORDER BY captured_at_ms,id LIMIT :limit")
    List<SourceEventEntity> pending(int limit);

    @Query("SELECT * FROM source_event WHERE process_state='REVIEW' ORDER BY captured_at_ms,id")
    List<SourceEventEntity> reviews();

    @Query("SELECT * FROM source_event WHERE transaction_id=:id")
    List<SourceEventEntity> evidence(String id);
}
