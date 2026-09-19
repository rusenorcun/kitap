ALTER TABLE conversations ADD COLUMN active_key BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversations DROP CONSTRAINT uq_conversations_kind_ref;
ALTER TABLE conversations ADD CONSTRAINT uq_conversations_active_ref UNIQUE (kind, ref_id, active_key);

UPDATE conversations
SET active_key = id
WHERE kind = 'REQUEST'
  AND NOT EXISTS (
      SELECT 1 FROM requests r
      WHERE r.id = conversations.ref_id
        AND r.status <> 'OPEN'
        AND r.student_id = conversations.user_a_id
        AND r.fulfilled_by_id = conversations.user_b_id
        AND (r.fulfilled_at IS NULL OR r.fulfilled_at <= conversations.created_at)
  );
