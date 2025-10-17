-- Create search_analytics table for tracking search behavior
CREATE TABLE search_analytics (
    id BIGSERIAL PRIMARY KEY,
    search_query VARCHAR(500) NOT NULL,
    normalized_query VARCHAR(500),
    result_count BIGINT NOT NULL,
    execution_time_ms BIGINT,
    user_session_id VARCHAR(100),
    user_ip_hash VARCHAR(64),
    user_agent VARCHAR(500),
    applied_filters TEXT,
    sort_criteria VARCHAR(100),
    page_number INTEGER,
    page_size INTEGER,
    had_clicks BOOLEAN DEFAULT false,
    first_click_position INTEGER,
    total_clicks INTEGER DEFAULT 0,
    led_to_purchase BOOLEAN DEFAULT false,
    search_source VARCHAR(50),
    search_locale VARCHAR(10),
    is_autocomplete BOOLEAN DEFAULT false,
    suggested_query VARCHAR(500),
    suggestion_accepted BOOLEAN DEFAULT false,
    category_context VARCHAR(100),
    search_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT
);

-- Create indexes for search analytics queries
CREATE INDEX idx_search_query ON search_analytics(search_query);
CREATE INDEX idx_search_date ON search_analytics(search_date);
CREATE INDEX idx_result_count ON search_analytics(result_count);
CREATE INDEX idx_user_session ON search_analytics(user_session_id);

-- Additional indexes for common analytics queries
CREATE INDEX idx_normalized_query ON search_analytics(normalized_query);
CREATE INDEX idx_had_clicks ON search_analytics(had_clicks);
CREATE INDEX idx_led_to_purchase ON search_analytics(led_to_purchase);
CREATE INDEX idx_search_source ON search_analytics(search_source);

-- Comment on table
COMMENT ON TABLE search_analytics IS 'Tracks user search behavior and analytics for product search functionality';
