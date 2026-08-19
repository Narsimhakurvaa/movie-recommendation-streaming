-- ---------------------------------------------------------------------------
-- V4 - Keep movies.average_rating / movies.rating_count consistent.
--
-- Rationale: every discovery query sorts or filters on the aggregate rating.
-- Computing it with a correlated subquery would make listing pages O(ratings).
-- A trigger keeps the denormalised columns correct even when rows are changed
-- outside the application (data imports, admin SQL, tests), which application
-- level recalculation alone cannot guarantee.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION refresh_movie_rating_aggregate(target_movie_id BIGINT)
RETURNS VOID AS $$
BEGIN
    UPDATE movies m
       SET average_rating = COALESCE(agg.avg_score, 0),
           rating_count   = COALESCE(agg.total, 0),
           updated_at     = NOW()
      FROM (
            SELECT ROUND(AVG(score)::NUMERIC, 2) AS avg_score,
                   COUNT(*)                      AS total
              FROM ratings
             WHERE movie_id = target_movie_id
           ) agg
     WHERE m.id = target_movie_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_ratings_refresh_aggregate()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        PERFORM refresh_movie_rating_aggregate(OLD.movie_id);
        RETURN OLD;
    END IF;

    PERFORM refresh_movie_rating_aggregate(NEW.movie_id);
    -- An UPDATE may move a rating between movies; refresh the old one too.
    IF (TG_OP = 'UPDATE' AND OLD.movie_id <> NEW.movie_id) THEN
        PERFORM refresh_movie_rating_aggregate(OLD.movie_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ratings_refresh_aggregate
    AFTER INSERT OR UPDATE OR DELETE ON ratings
    FOR EACH ROW EXECUTE FUNCTION trg_ratings_refresh_aggregate();

-- Generic updated_at maintenance for mutable entities.
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_set_updated_at   BEFORE UPDATE ON users   FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER movies_set_updated_at  BEFORE UPDATE ON movies  FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER reviews_set_updated_at BEFORE UPDATE ON reviews FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
