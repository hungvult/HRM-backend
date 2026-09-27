CREATE TABLE IF NOT EXISTS api_logs (
    id BIGSERIAL PRIMARY KEY,
    c TEXT,
    cmd VARCHAR(200),
    url TEXT,
    ip VARCHAR(100),
    t TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    l VARCHAR(30) NOT NULL DEFAULT 'Information',
    http_method VARCHAR(10),
    request_url TEXT,
    token_code TEXT,
    request_body TEXT,
    response_body TEXT,
    response_status INTEGER,
    execution_time_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_api_logs_t ON api_logs (t DESC);
CREATE INDEX IF NOT EXISTS idx_api_logs_url ON api_logs (url);
CREATE INDEX IF NOT EXISTS idx_api_logs_cmd ON api_logs (cmd);
CREATE INDEX IF NOT EXISTS idx_api_logs_response_status ON api_logs (response_status);
