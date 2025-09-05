-- Create template_contents table

CREATE TABLE template_contents (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    country_code VARCHAR(10),
    content_version INTEGER NOT NULL DEFAULT 1,
    title VARCHAR(500),
    subject VARCHAR(1000),
    content TEXT,
    plain_text_content TEXT,
    html_content TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_fallback BOOLEAN NOT NULL DEFAULT false,
    media_attachments JSONB DEFAULT '[]',
    content_blocks JSONB DEFAULT '{}',
    variables JSONB DEFAULT '{}',
    metadata JSONB DEFAULT '{}',
    
    -- Approval workflow fields
    approved_by BIGINT,
    approved_at TIMESTAMP,
    rejected_by BIGINT,
    rejected_at TIMESTAMP,
    rejection_reason VARCHAR(1000),
    published_at TIMESTAMP,
    expires_at TIMESTAMP,
    
    -- SEO and accessibility
    alt_text VARCHAR(500),
    meta_description VARCHAR(1000),
    keywords VARCHAR(1000),
    
    -- A/B testing
    ab_test_group VARCHAR(50),
    ab_test_weight INTEGER DEFAULT 100,
    
    -- Base entity fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255),
    
    -- Foreign key constraint
    CONSTRAINT fk_template_content_template FOREIGN KEY (template_id) REFERENCES notification_templates(id)
);

-- Create indexes
CREATE INDEX idx_template_content_template_id ON template_contents(template_id);
CREATE INDEX idx_template_content_language ON template_contents(language_code);
CREATE INDEX idx_template_content_status ON template_contents(status);
CREATE INDEX idx_template_content_version ON template_contents(template_id, content_version);

-- Add comments
COMMENT ON TABLE template_contents IS 'Rich content management for notification templates';
COMMENT ON COLUMN template_contents.template_id IS 'Reference to notification template';
COMMENT ON COLUMN template_contents.language_code IS 'Language code (e.g., en, vi, fr)';
COMMENT ON COLUMN template_contents.country_code IS 'Country code (e.g., US, VN, FR)';
COMMENT ON COLUMN template_contents.content_version IS 'Version number of the content';
COMMENT ON COLUMN template_contents.status IS 'Content status (DRAFT, APPROVED, PUBLISHED, etc.)';
COMMENT ON COLUMN template_contents.is_default IS 'Default content for this language';
COMMENT ON COLUMN template_contents.is_fallback IS 'Fallback content';
COMMENT ON COLUMN template_contents.ab_test_group IS 'A/B testing group identifier';
COMMENT ON COLUMN template_contents.ab_test_weight IS 'Weight for A/B testing (0-100)';
