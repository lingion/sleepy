"""Deterministic URL provenance redaction; never log matched values."""
import hashlib,re
from urllib.parse import unquote,urlsplit,urlunsplit
REDACTED='REDACTED'
SAFE_KEYS={'clientid','oauthclientid','responsetype','redirecturi','redirecturl','redirectauthurl','authorizeurl','authtype','authntrycount','oauthlogin','oauthcallback','applicationcode','orgcode','schoolcode','spcode','codechallengemethod'}
ASSIGN=re.compile(r'(?i)(?=([?&;]|^)([A-Za-z_][\w.-]*)(=)([^&#;\s<>"\']*))')
JSON_ASSIGN=re.compile(r'''(?i)(["'])([A-Za-z_][\w.-]*)\1\s*:\s*(["'])(.*?)\3''')
USERINFO=re.compile(r'(?i)(https?://)([^/@\s?#]+)@')
PATH_SESSION=re.compile(r'(?i)(/\(S\()([^)]*)(\)\))')
def sensitive(key):
 k=re.sub(r'[^a-z0-9]','',key.lower())
 if k in SAFE_KEYS:return False
 return any(word in k for word in ('password','passwd','token','secret','session','ticket')) or k in {'pwd','pass','username','userid','user','loginname','account','authorization','auth','authcode','code','credential','credentials','samlresponse','samlrequest','relaystate','state','codeverifier','codechallenge','apikey','key','sid','jwt','accesskey','signature','sign'}
def sanitize_url(value):
 """Redact at every percent-encoding layer; changed provenance retains original SHA256."""
 changes=set()
 def clean(s,depth=0):
  if depth>10:raise ValueError('URL encoding depth exceeds safety bound')
  def assignment(m):
   if sensitive(m.group(2)) and m.group(4) not in ('',REDACTED):
    changes.add(m.group(2));return m.group(1)+m.group(2)+m.group(3)+REDACTED
   return m.group(0)
  for match in reversed(list(ASSIGN.finditer(s))):
   if sensitive(match.group(2)) and match.group(4) not in ('',REDACTED):
    changes.add(match.group(2));s=s[:match.start(4)]+REDACTED+s[match.end(4):]
  def json_assignment(m):
   if sensitive(m.group(2)) and m.group(4) not in ('',REDACTED):
    changes.add(m.group(2));return m.group(1)+m.group(2)+m.group(1)+':'+m.group(3)+REDACTED+m.group(3)
   return m.group(0)
  s=JSON_ASSIGN.sub(json_assignment,s)
  def userinfo(m):
   if m.group(2)!=REDACTED:changes.add('userinfo')
   return m.group(1)+REDACTED+'@'
  s=USERINFO.sub(userinfo,s)
  def path_session(m):
   if m.group(2)!=REDACTED:changes.add('path-session')
   return m.group(1)+REDACTED+m.group(3)
  s=PATH_SESSION.sub(path_session,s)
  decoded=unquote(s)
  if decoded!=s:
   before=set(changes);safe_decoded=clean(decoded,depth+1)
   if safe_decoded!=decoded:
    # Provenance-only redaction can expose formerly nested separators. Metadata
    # explicitly identifies a display copy, not an operational login URL.
    s=safe_decoded
  return s
 safe=clean(value)
 return safe,sorted(changes)
def audit(value,path=''):
 findings=[]
 if isinstance(value,dict):
  for k,v in value.items():findings.extend(audit(v,path+'/'+k))
 elif isinstance(value,list):
  for i,v in enumerate(value):findings.extend(audit(v,path+'/'+str(i)))
 elif isinstance(value,str) and ('://' in value or '%3a' in value.lower() or '%253a' in value.lower()):
  safe,keys=sanitize_url(value)
  if safe!=value:findings.append({'path':path,'keys':keys})
 return findings

def sanitize_tree(value,path=''):
 events=[]
 if isinstance(value,dict):
  for k in list(value):
   v=value[k]
   if isinstance(v,str) and ('://' in v or '%3a' in v.lower() or '%253a' in v.lower()):
    safe,keys=sanitize_url(v)
    if safe!=v:
     value[k]=safe
     meta={'originalSha256':hashlib.sha256(v.encode()).hexdigest(),'redactedParameters':keys,'displayCopyOnly':True}
     previous=value.setdefault('urlRedactions',{}).get(k)
     if previous:
      meta['originalSha256']=previous['originalSha256']
      meta['redactedParameters']=sorted(set(meta['redactedParameters'])|set(previous['redactedParameters']))
     value['urlRedactions'][k]=meta
     events.append({'path':path+'/'+k,'parameters':keys})
   elif isinstance(v,(dict,list)):events.extend(sanitize_tree(v,path+'/'+k))
 elif isinstance(value,list):
  for i,v in enumerate(value):events.extend(sanitize_tree(v,path+'/'+str(i)))
 return events
