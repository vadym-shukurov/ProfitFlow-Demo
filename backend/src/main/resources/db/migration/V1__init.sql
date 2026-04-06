CREATE TABLE resource_cost (
    id UUID PRIMARY KEY,
    label VARCHAR(512) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    CONSTRAINT chk_resource_cost_amount CHECK (amount >= 0)
);

CREATE TABLE activity (
    id UUID PRIMARY KEY,
    name VARCHAR(256) NOT NULL
);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    name VARCHAR(256) NOT NULL
);

CREATE TABLE resource_activity_rule (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES resource_cost (id) ON DELETE CASCADE,
    activity_id UUID NOT NULL REFERENCES activity (id) ON DELETE CASCADE,
    driver_weight NUMERIC(19, 4) NOT NULL,
    CONSTRAINT uq_ra_resource_activity UNIQUE (resource_id, activity_id),
    CONSTRAINT chk_ra_weight CHECK (driver_weight >= 0)
);

CREATE TABLE activity_product_rule (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES activity (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    driver_weight NUMERIC(19, 4) NOT NULL,
    CONSTRAINT uq_ap_activity_product UNIQUE (activity_id, product_id),
    CONSTRAINT chk_ap_weight CHECK (driver_weight >= 0)
);

CREATE INDEX idx_ra_resource ON resource_activity_rule (resource_id);
CREATE INDEX idx_ap_activity ON activity_product_rule (activity_id);
