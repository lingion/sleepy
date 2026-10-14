"""Synthetic URL-boundary tests; no real credential fixtures."""
import hashlib,unittest
from urllib.parse import quote
from catalog_url_safety import sanitize_url,sanitize_tree,audit
class UrlSafetyTest(unittest.TestCase):
 def test_nested_service_urls_are_redacted(self):
  nested='https://app.example/login?token=synthetic-secret&client_id=public-client&response_type=code'
  for depth in range(1,5):
   value=nested
   for _ in range(depth):value=quote(value,safe='')
   original='https://sso.example/login?service='+value
   cleaned,keys=sanitize_url(original)
   self.assertNotIn('synthetic-secret',cleaned);self.assertIn('token',keys)
   self.assertIn('client_id=public-client',cleaned);self.assertIn('response_type=code',cleaned)
   self.assertFalse(audit({'url':cleaned}))
 def test_json_login_context_is_redacted(self):
  value='https://example.edu/login?context='+quote('{"username":"synthetic-user","password":"synthetic-pass"}')
  cleaned,keys=sanitize_url(value)
  self.assertNotIn('synthetic-user',cleaned);self.assertNotIn('synthetic-pass',cleaned)
  self.assertEqual(set(keys),{'username','password'})
 def test_sessions_and_userinfo_are_redacted(self):
  for value in ['https://synthetic-user:synthetic-pass@example.edu/login','https://example.edu/(S(synthetic-session))/default2.aspx','https://example.edu/login;jsessionid=synthetic-session?ticket=synthetic-ticket']:
   cleaned,keys=sanitize_url(value);self.assertNotIn('synthetic-',cleaned);self.assertTrue(keys)
 def test_hash_and_idempotence_preserve_traceability(self):
  raw='https://example.edu/?access_token=synthetic-secret';obj={'sourceId':'fixture','sourceRow':7,'url':raw}
  sanitize_tree(obj);self.assertEqual(obj['urlRedactions']['url']['originalSha256'],hashlib.sha256(raw.encode()).hexdigest())
  self.assertEqual(obj['sourceRow'],7);self.assertFalse(sanitize_tree(obj));self.assertFalse(audit(obj))
 def test_public_parameters_stay_unchanged(self):
  raw='https://example.edu/login?client_id=public&response_type=code&schoolCode=123&authType=cas&applicationCode=jwxt'
  self.assertEqual(sanitize_url(raw),(raw,[]))
 def test_audit_catches_new_nested_sensitive_field(self):
  self.assertTrue(audit({'url':'https://sso.example/?service=https%3A%2F%2Fapp.example%2F%3FsessionToken%3Dsynthetic'}))
if __name__=='__main__':unittest.main()
