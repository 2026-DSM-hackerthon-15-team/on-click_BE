ALTER TABLE stores
    ADD COLUMN owner_user_id BIGINT;

UPDATE stores
SET owner_user_id = (
    SELECT membership.user_id
    FROM user_store_memberships membership
    WHERE membership.store_id = stores.id
      AND membership.role = 'OWNER'
);

ALTER TABLE stores
    ALTER COLUMN owner_user_id SET NOT NULL;

ALTER TABLE stores
    ADD CONSTRAINT fk_stores_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users (id);

CREATE INDEX idx_stores_owner_user
    ON stores (owner_user_id);

DROP TABLE user_store_memberships;
