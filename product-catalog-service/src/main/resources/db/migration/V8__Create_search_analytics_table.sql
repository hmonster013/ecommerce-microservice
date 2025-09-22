-- Create search_analytics table for tracking search behavior and analytics
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
    had_clicks BOOLEAN DEFAULT FALSE,
    first_click_position INTEGER,
    total_clicks INTEGER DEFAULT 0,
    led_to_purchase BOOLEAN DEFAULT FALSE,
    search_source VARCHAR(50),
    search_locale VARCHAR(10),
    is_autocomplete BOOLEAN DEFAULT FALSE,
    suggested_query VARCHAR(500),
    suggestion_accepted BOOLEAN DEFAULT FALSE,
    category_context VARCHAR(100),
    search_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT
);

-- Add comments for documentation
COMMENT ON TABLE search_analytics IS 'Tracks user search behavior and analytics for optimization';
COMMENT ON COLUMN search_analytics.search_query IS 'Original search query entered by user';
COMMENT ON COLUMN search_analytics.normalized_query IS 'Normalized version of search query for analytics';
COMMENT ON COLUMN search_analytics.result_count IS 'Number of results returned for this search';
COMMENT ON COLUMN search_analytics.execution_time_ms IS 'Search execution time in milliseconds';
COMMENT ON COLUMN search_analytics.user_session_id IS 'User session identifier for tracking';
COMMENT ON COLUMN search_analytics.user_ip_hash IS 'Hashed IP address for privacy';
COMMENT ON COLUMN search_analytics.applied_filters IS 'JSON representation of applied search filters';
COMMENT ON COLUMN search_analytics.had_clicks IS 'Whether user clicked on any search results';
COMMENT ON COLUMN search_analytics.led_to_purchase IS 'Whether search led to a purchase';
COMMENT ON COLUMN search_analytics.search_source IS 'Source of search (web, mobile, api)';
COMMENT ON COLUMN search_analytics.is_autocomplete IS 'Whether this was an autocomplete search';
COMMENT ON COLUMN search_analytics.suggested_query IS 'Alternative query suggested for no-result searches';
COMMENT ON COLUMN search_analytics.suggestion_accepted IS 'Whether user accepted the suggested query';
