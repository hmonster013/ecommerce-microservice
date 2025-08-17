-- Create review status enum
CREATE TYPE review_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'FLAGGED', 'SPAM', 'DELETED');

-- Create reviews table
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(255),
    comment TEXT NOT NULL,
    status review_status NOT NULL DEFAULT 'PENDING',
    helpful_count INTEGER NOT NULL DEFAULT 0 CHECK (helpful_count >= 0),
    not_helpful_count INTEGER NOT NULL DEFAULT 0 CHECK (not_helpful_count >= 0),
    verified_purchase BOOLEAN NOT NULL DEFAULT false,
    reviewer_name VARCHAR(255),
    reviewer_email VARCHAR(255),
    moderation_notes TEXT,
    moderated_by VARCHAR(255),
    moderated_at TIMESTAMP,
    ip_address VARCHAR(45), -- Support for IPv6
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_reviews_product_id ON reviews(product_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
CREATE INDEX idx_reviews_status ON reviews(status);
CREATE INDEX idx_reviews_rating ON reviews(rating);
CREATE INDEX idx_reviews_created_at ON reviews(created_at);
CREATE INDEX idx_reviews_helpful_count ON reviews(helpful_count);
CREATE INDEX idx_reviews_verified_purchase ON reviews(verified_purchase);

-- Index for finding reviews that need moderation
CREATE INDEX idx_reviews_moderation ON reviews(status) WHERE status IN ('PENDING', 'FLAGGED');

-- Index for public reviews (approved and visible)
CREATE INDEX idx_reviews_public ON reviews(product_id, status, created_at) WHERE status = 'APPROVED';

-- Prevent duplicate reviews from same user for same product
CREATE UNIQUE INDEX idx_reviews_user_product_unique ON reviews(product_id, user_id);

-- Create trigger to update updated_at
CREATE TRIGGER update_reviews_updated_at 
    BEFORE UPDATE ON reviews 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Create function to update product rating statistics
CREATE OR REPLACE FUNCTION update_product_rating_stats()
RETURNS TRIGGER AS $$
BEGIN
    -- This function can be used to update aggregate rating data
    -- For now, we'll just return the trigger result
    -- In the future, we might add a product_rating_stats table
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Create trigger to update product rating stats when reviews change
CREATE TRIGGER trigger_update_product_rating_stats
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_product_rating_stats();
