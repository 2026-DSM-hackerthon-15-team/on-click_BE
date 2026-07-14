ALTER TABLE stores
    ADD COLUMN industry VARCHAR(30) NOT NULL DEFAULT 'OTHER';

ALTER TABLE stores
    ADD COLUMN road_address VARCHAR(255);

ALTER TABLE stores
    ADD CONSTRAINT ck_stores_industry
        CHECK (industry IN (
            'RESTAURANT',
            'CAFE',
            'RETAIL',
            'SERVICE',
            'OTHER'
        ));
