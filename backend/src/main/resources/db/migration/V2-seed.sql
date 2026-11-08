INSERT INTO portaluser(id,tenant_id,entra_oid,isid_raw,normalized_isid,display_name,roles) VALUES
 ('u-alice','demo-tenant','oid-alice','TEST001','TEST001','Alice / 华东销售','USER'),
 ('u-bob','demo-tenant','oid-bob','TEST002','TEST002','Bob / 华南财务','USER'),
 ('u-admin','demo-tenant','oid-admin','ADMIN001','ADMIN001','门户管理员','ADMIN'),
 ('u-auditor','demo-tenant','oid-auditor','AUDIT001','AUDIT001','只读审计员','AUDITOR'),
 ('u-unmapped','demo-tenant','oid-unmapped','TEST003','TEST003','尚未映射用户','USER');
INSERT INTO usermapping(user_id,tenant_id,quickbi_user_id,mapping_status,bi_status) VALUES
 ('u-alice','demo-tenant','bi-alice','MAPPED','ACTIVE'),
 ('u-bob','demo-tenant','bi-bob','MAPPED','ACTIVE'),
 ('u-admin','demo-tenant',NULL,'UNMAPPED','UNKNOWN'),
 ('u-auditor','demo-tenant',NULL,'UNMAPPED','UNKNOWN'),
 ('u-unmapped','demo-tenant',NULL,'UNMAPPED','UNKNOWN');
INSERT INTO usercoord(tenant_id,user_id) SELECT tenant_id,id FROM portaluser;
INSERT INTO systemconfig(tenant_id) VALUES ('demo-tenant');
INSERT INTO mockbiuser VALUES ('bi-alice',1,'华东'),('bi-bob',1,'华南'),('bi-free',1,'测试区');
INSERT INTO reportresource VALUES
 ('rep-sales','demo-tenant','ws-sales','works-sales','区域销售看板','仅 Alice 可见，展示模拟华东数据','DASHBOARD',1,1),
 ('rep-finance','demo-tenant','ws-finance','works-finance','财务分析工作簿','仅 Bob 可见，展示模拟华南数据','WORKBOOK',1,1),
 ('rep-common','demo-tenant','ws-common','works-common','经营概览','两位用户均可访问，但展示各自区域数据','DASHBOARD',1,1),
 ('rep-draft','demo-tenant','ws-sales','works-draft','未发布报表','不可展示的过滤用例','DASHBOARD',0,1);
INSERT INTO mockgrant VALUES ('bi-alice','rep-sales'),('bi-alice','rep-common'),('bi-alice','rep-draft'),('bi-bob','rep-finance'),('bi-bob','rep-common');
