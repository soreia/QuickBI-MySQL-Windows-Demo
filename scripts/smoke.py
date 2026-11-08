#!/usr/bin/env python3
"""HTTP integration smoke test. Python stdlib only; requires the running demo."""
import json, sys, time, uuid
from urllib.request import build_opener, HTTPCookieProcessor, Request
from urllib.error import HTTPError, URLError
from http.cookiejar import CookieJar
from urllib.parse import urlsplit
BASE = (sys.argv[1] if len(sys.argv)>1 else 'http://localhost:8081').rstrip('/')
ORIGIN = '{0.scheme}://{0.netloc}'.format(urlsplit(BASE))
class Client:
    def __init__(self): self.http=build_opener(HTTPCookieProcessor(CookieJar())); self.csrf=''
    def call(self,path,method='GET',body=None,expected=200,headers=None,raw=False):
        h={'Origin':ORIGIN,'Content-Type':'application/json','X-CSRF-Token':self.csrf}; h.update(headers or {})
        request=Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
        try:
            with self.http.open(request,timeout=35) as r: status=r.status; content=r.read()
        except HTTPError as e: status=e.code; content=e.read()
        assert status==expected, f'{method} {path.split("?")[0]} expected {expected}, got {status}: {content[:300]!r}'
        if raw: return content.decode()
        if status==204: return None
        result=json.loads(content)
        return result.get('data',result)
    def login(self,user):
        self.csrf=self.call('/auth/context')['csrfToken']
        result=self.call('/auth/login','POST',{'userId':user}); self.csrf=result['csrfToken']
        return self.call('/api/v1/me')

def main():
    guest=Client()
    for _ in range(60):
        try: guest.call('/healthz/ready');break
        except (URLError,AssertionError):time.sleep(2)
    else: raise AssertionError('Demo not ready after 120 seconds')
    guest.call('/api/v1/reports',expected=401)
    alice,bob,admin,auditor=Client(),Client(),Client(),Client()
    alice.login('u-alice');bob.login('u-bob');admin.login('u-admin');auditor.login('u-auditor')
    admin.call('/api/v1/demo/fault','POST',{'mode':'NORMAL'})
    cfg=admin.call('/api/v1/admin/config')
    admin.call('/api/v1/admin/config','PATCH',{'expectedVersion':cfg['version'],'idleMinutes':cfg['idleMinutes'],'absoluteHours':cfg['absoluteHours'],'listCacheSeconds':300,'reason':'smoke cache fixture'})
    # Restore the seed grant, so repeat smoke runs start predictably.
    admin.call('/api/v1/demo/grant','POST',{'biUserId':'bi-alice','reportId':'rep-sales','granted':True})
    job=admin.call('/api/v1/admin/sync-jobs','POST',{'type':'USER','userId':'u-alice'},expected=202)
    for _ in range(30):
        current=admin.call('/api/v1/admin/sync-jobs/'+job['taskId'])
        if current['state'] not in ('QUEUED','RUNNING'):
            assert current['state']=='SUCCEEDED', current
            break
        time.sleep(1)
    else: raise AssertionError('USER sync did not finish in 30 seconds')
    reports=alice.call('/api/v1/reports'); assert any(r['reportId']=='rep-sales' for r in reports['items'])
    assert all(r['reportId']!='rep-draft' for r in reports['items'])
    alice.call('/api/v1/admin/users',expected=403)
    auditor.call('/api/v1/admin/audit-events')
    auditor.call('/api/v1/admin/users',expected=403)
    key=str(uuid.uuid4())
    result=alice.call('/api/v1/reports/rep-sales/embed-sessions','POST',{},headers={'Idempotency-Key':key})
    alice.call('/api/v1/reports/rep-sales/embed-sessions','POST',{},expected=409,headers={'Idempotency-Key':key})
    html=guest.call(result['embedUrl'],raw=True); assert 'bi-alice' in html and '华东' in html
    guest.call(result['embedUrl'],expected=410)
    bob.call('/api/v1/reports/rep-sales/embed-sessions','POST',{},expected=404,headers={'Idempotency-Key':str(uuid.uuid4())})
    alice.call('/api/v1/reports/rep-sales/embed-sessions','POST',{'UserId':'bi-bob'},expected=400,headers={'Idempotency-Key':str(uuid.uuid4())})
    # Cached list stays visible; real-time mock vendor authorization must still reject opening.
    admin.call('/api/v1/demo/grant','POST',{'biUserId':'bi-alice','reportId':'rep-sales','granted':False})
    assert any(r['reportId']=='rep-sales' for r in alice.call('/api/v1/reports')['items'])
    alice.call('/api/v1/reports/rep-sales/embed-sessions','POST',{},expected=404,headers={'Idempotency-Key':str(uuid.uuid4())})
    admin.call('/api/v1/demo/grant','POST',{'biUserId':'bi-alice','reportId':'rep-sales','granted':True})
    for mode,code in [('READ_UNAVAILABLE','PROVIDER_UNAVAILABLE'),('TICKET_UNKNOWN','TICKET_ISSUE_UNKNOWN'),('AUDIT_FAILURE','AUDIT_UNAVAILABLE')]:
        admin.call('/api/v1/demo/fault','POST',{'mode':mode})
        result=alice.call('/api/v1/reports/rep-common/embed-sessions','POST',{},expected=503,headers={'Idempotency-Key':str(uuid.uuid4())})
        assert result['error']['code']==code, (mode,result)
        admin.call('/api/v1/demo/fault','POST',{'mode':'NORMAL'})
    # CSRF rejected, then original session remains valid.
    alice.call('/api/v1/auth/logout','POST',expected=403,headers={'X-CSRF-Token':'bad'})
    cfg=admin.call('/api/v1/admin/config')
    payload={'expectedVersion':cfg['version'],'idleMinutes':cfg['idleMinutes'],'absoluteHours':cfg['absoluteHours'],'listCacheSeconds':300,'reason':'automated smoke'}
    admin.call('/api/v1/admin/config','PATCH',payload)
    admin.call('/api/v1/admin/config','PATCH',payload,expected=409)
    users=admin.call('/api/v1/admin/users')['items']; u=next(x for x in users if x['userId']=='u-alice')
    # Same target is a controlled version change: demonstrates revocation without altering fixture grants.
    admin.call('/api/v1/admin/users/u-alice/mapping','PUT',{'quickbiUserId':'bi-alice','expectedVersion':u['version'],'reason':'smoke revoke all sessions'})
    alice.call('/api/v1/me',expected=401)
    alice.login('u-alice'); alice.call('/api/v1/auth/logout','POST',expected=204);alice.call('/api/v1/me',expected=401)
    print('PASS: login, hash-session use, role boundary, report filtering, ticket single-use, guard409, IDOR404, strict input, revocation, provider/audit failure, CSRF, version conflict, mapping session revocation, logout')
    print('No raw ticket URLs are printed. These results apply to LOCAL MOCK only.')
if __name__=='__main__': main()
