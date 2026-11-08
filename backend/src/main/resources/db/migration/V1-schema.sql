CREATE TABLE portaluser (
 id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 entra_oid VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 isid_raw VARCHAR(128) NOT NULL,
 normalized_isid VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
 display_name VARCHAR(256) NOT NULL,
 roles VARCHAR(100) NOT NULL,
 enterprise_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
 security_epoch BIGINT NOT NULL DEFAULT 1,
 PRIMARY KEY(id), UNIQUE KEY uq_user_tenant(tenant_id,id),
 UNIQUE KEY uq_oid(tenant_id,entra_oid), UNIQUE KEY uq_isid(tenant_id,normalized_isid),
 CHECK (enterprise_status IN ('ACTIVE','INACTIVE','UNKNOWN')), CHECK(security_epoch>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE usermapping (
 user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quickbi_user_id VARCHAR(256) COLLATE utf8mb4_0900_bin NULL,
 mapping_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED',
 bi_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN', version BIGINT NOT NULL DEFAULT 1,
 change_reason VARCHAR(500) NULL,
 UNIQUE KEY uq_bi(tenant_id,quickbi_user_id),
 FOREIGN KEY(tenant_id,user_id) REFERENCES portaluser(tenant_id,id),
 CHECK(mapping_status IN ('MAPPED','UNMAPPED','INVALID','CONFLICT','PENDING')),
 CHECK(bi_status IN ('ACTIVE','INACTIVE','UNKNOWN')),
 CHECK(mapping_status <> 'MAPPED' OR quickbi_user_id IS NOT NULL), CHECK(version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE portalsession (
 sid_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 epoch BIGINT NOT NULL, generation VARCHAR(100) NOT NULL,
 created_at DATETIME(6) NOT NULL, last_activity DATETIME(6) NOT NULL, revoked_at DATETIME(6) NULL,
 FOREIGN KEY(tenant_id,user_id) REFERENCES portaluser(tenant_id,id), KEY ix_session_user(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE reportresource (
 id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 workspace_id VARCHAR(256) NOT NULL, works_id VARCHAR(256) NOT NULL,
 name VARCHAR(512) NOT NULL, description TEXT NOT NULL,
 work_type VARCHAR(20) NOT NULL, published TINYINT NOT NULL, embed_enabled TINYINT NOT NULL,
 UNIQUE KEY uq_resource(tenant_id,workspace_id,works_id), UNIQUE KEY uq_resource_tenant(tenant_id,id),
 CHECK(published IN (0,1)), CHECK(embed_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
-- The next two tables are the local MOCK provider's authority, never production portal grants.
CREATE TABLE mockbiuser (
 id VARCHAR(256) PRIMARY KEY, active TINYINT NOT NULL, data_region VARCHAR(100) NOT NULL,
 CHECK(active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE mockgrant (
 bi_user_id VARCHAR(256) NOT NULL, report_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(bi_user_id,report_id), FOREIGN KEY(bi_user_id) REFERENCES mockbiuser(id),
 FOREIGN KEY(report_id) REFERENCES reportresource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE snapshot (
 id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 mapping_version BIGINT NOT NULL, generation BIGINT NOT NULL,
 fetched_at DATETIME(6) NOT NULL, complete TINYINT NOT NULL DEFAULT 1,
 UNIQUE KEY uq_snapshot_tenant(tenant_id,id),
 FOREIGN KEY(tenant_id,user_id) REFERENCES portaluser(tenant_id,id), CHECK(complete=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE snapshotitem (
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 snapshot_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 report_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(snapshot_id,report_id), FOREIGN KEY(tenant_id,snapshot_id) REFERENCES snapshot(tenant_id,id),
 FOREIGN KEY(tenant_id,report_id) REFERENCES reportresource(tenant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE usercoord (
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 current_snapshot VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 fence BIGINT NOT NULL DEFAULT 0, lease_owner VARCHAR(64) NULL, lease_until DATETIME(6) NULL,
 PRIMARY KEY(tenant_id,user_id), FOREIGN KEY(tenant_id,user_id) REFERENCES portaluser(tenant_id,id),
 FOREIGN KEY(tenant_id,current_snapshot) REFERENCES snapshot(tenant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE requestguard (
 tenant_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sid_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 report_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempt_id VARCHAR(64) NOT NULL, state VARCHAR(20) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,user_id,sid_hash,report_id,key_hash), KEY ix_guard_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE accessattempt (
 id VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL,
 report_id VARCHAR(64) NOT NULL, mapping_version BIGINT NOT NULL,
 state VARCHAR(24) NOT NULL, request_id VARCHAR(64) NOT NULL,
 provider_request_id VARCHAR(64) NULL, issued_at DATETIME(6) NULL, expires_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE auditevent (
 id VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
 actor_id VARCHAR(64) NULL, event_type VARCHAR(40) NOT NULL, result VARCHAR(30) NOT NULL,
 resource_id VARCHAR(64) NULL, request_id VARCHAR(64) NOT NULL,
 safe_details VARCHAR(1000) NOT NULL, event_time DATETIME(6) NOT NULL,
 KEY ix_audit_time(tenant_id,event_time), KEY ix_audit_actor(tenant_id,actor_id,event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE syncjob (
 id VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
 job_type VARCHAR(20) NOT NULL, parent_id VARCHAR(64) NULL, triggered_by VARCHAR(64) NOT NULL,
 state VARCHAR(20) NOT NULL, total INT NOT NULL DEFAULT 0,
 success INT NOT NULL DEFAULT 0, failed INT NOT NULL DEFAULT 0, skipped INT NOT NULL DEFAULT 0,
 lease_owner VARCHAR(64) NULL, lease_until DATETIME(6) NULL, fence BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, ended_at DATETIME(6) NULL,
 active_full_tenant VARCHAR(64) GENERATED ALWAYS AS
  (CASE WHEN job_type='FULL' AND state IN ('QUEUED','RUNNING') THEN tenant_id ELSE NULL END) STORED,
 UNIQUE KEY uq_active_full(active_full_tenant), KEY ix_jobs(state,created_at),
 CHECK(job_type IN ('FULL','USER','RETRY_FAILED')),
 CHECK(state IN ('QUEUED','RUNNING','SUCCEEDED','PARTIAL','FAILED','INTERRUPTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE syncitem (
 job_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL,
 state VARCHAR(24) NOT NULL, error_code VARCHAR(80) NULL, attempt_count INT NOT NULL DEFAULT 0,
 PRIMARY KEY(job_id,user_id), FOREIGN KEY(job_id) REFERENCES syncjob(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE systemconfig (
 tenant_id VARCHAR(64) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 1,
 idle_minutes INT NOT NULL DEFAULT 30, absolute_hours INT NOT NULL DEFAULT 8,
 cache_seconds INT NOT NULL DEFAULT 300,
 CHECK(idle_minutes BETWEEN 5 AND 60), CHECK(absolute_hours BETWEEN 1 AND 12), CHECK(cache_seconds BETWEEN 0 AND 300)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
