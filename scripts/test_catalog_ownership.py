import copy
import base64
import hashlib
import unittest
from catalog_ownership import apply_ownership, link_matches, source_proves

class OwnershipTest(unittest.TestCase):
    def setUp(self):
        self.row = dict(name='甲大学', catalogId='school:1', url='https://wrong.example/jw', type='zf_new', status='unverified')
        self.proof = dict(catalogId='school:1', name='甲大学', url=self.row['url'], status='unknown', sources=[])

    def source(self, kind, url=None, **kwargs):
        return dict(kind=kind, url=url or self.row['url'], checkedAt='2026-10-14', **kwargs)

    def test_verification_rejects_unrelated_or_incomplete_sources(self):
        self.proof['status'] = 'verified'
        for source in [
            {'kind': 'entry-page'},
            self.source('entry-page', observedText='乙大学'),
            self.source('entry-page', 'https://other.example', observedText='甲大学'),
            self.source('search-result', observedText='甲大学'),
            self.source('official-school-link', observedText='甲大学', linkedUrls=['https://wrong.example:8080/jw']),
            self.source('official-school-published-address', observedText='甲大学'),
        ]:
            self.proof['sources'] = [source]
            with self.subTest(source=source), self.assertRaises(ValueError):
                self.apply()

    def test_official_link_to_other_tenant_path_is_rejected(self):
        self.proof.update(status='verified', sources=[self.source('official-school-link', 'https://school.example/', observedText='甲大学', linkedUrls=['https://shared.example/tenant-a/login'])])
        self.row['url'] = 'https://shared.example/tenant-b/login'
        self.proof['url'] = self.row['url']
        with self.assertRaises(ValueError): self.apply()

    def test_parent_identity_cannot_verify_graduate_entry(self):
        self.row['name'] = self.proof['name'] = '甲大学 - 研究生'
        self.proof.update(status='verified', sources=[self.source('entry-page', observedText='甲大学统一认证')])
        with self.assertRaises(ValueError): self.apply()

    def test_official_homepage_self_link_does_not_verify_academic_path(self):
        self.proof.update(status='verified', sources=[self.source('official-school-link', 'https://wrong.example/', observedText='甲大学', linkedUrls=['https://wrong.example/'])])
        with self.assertRaises(ValueError): self.apply()

    def test_verified_matching_identity_and_target_is_operational(self):
        self.proof.update(status='verified', sources=[self.source('entry-page', observedText='甲大学教务管理系统')])
        self.assertEqual(self.row['url'], self.apply()[0]['url'])

    def test_campus_suffix_cannot_be_discarded(self):
        self.row['name'] = self.proof['name'] = '甲大学（乙校区）'
        self.proof.update(status='verified', sources=[self.source('entry-page', observedText='甲大学教务管理系统')])
        with self.assertRaises(ValueError): self.apply()

    def test_rename_requires_explicit_mapping_and_new_name_observation(self):
        self.proof.update(status='verified', sources=[self.source('renamed-school-page', identityName='乙大学', observedText='乙大学教务管理系统')])
        with self.assertRaises(ValueError): self.apply()
        rows = [copy.deepcopy(self.row)]
        apply_ownership(rows, {'entries': [self.proof]}, set(), {'甲大学': {'newName':'乙大学', 'evidence':'reviewed mapping'}})
        self.assertEqual(self.row['url'], rows[0]['url'])

    def apply(self, baseline=()):
        rows = [copy.deepcopy(self.row)]
        result = apply_ownership(rows, {'entries': [self.proof]}, set(baseline))
        return rows[0], result[0]

    def test_unknown_new_entry_is_not_operational(self):
        row, result = self.apply()
        self.assertEqual(('', None, 'pending', False), (row['url'], row['type'], row['status'], row['enableFetch']))

    def test_unreachable_page_does_not_invalidate_previous_import_evidence(self):
        self.row['status'] = 'supported'
        row, result = self.apply(['甲大学'])
        self.assertEqual(self.row, row)
        self.assertEqual('retained-existing-config-ownership-unresolved', result['action'])

    def test_contradicted_baseline_is_blocked_without_correction(self):
        self.proof['status'] = 'contradicted'
        row, _ = self.apply(['甲大学'])
        self.assertEqual('', row['url'])

    def test_correction_needs_official_and_live_identity_and_same_parser(self):
        self.proof['status'] = 'contradicted'
        correction = dict(status='verified', url='https://right.example/jw', parserFamily='zf_new', sources=[self.source('entry-page', 'https://right.example/jw', observedText='甲大学')])
        self.proof['proposedCorrection'] = correction
        self.assertEqual('', self.apply()[0]['url'])
        correction['sources'].append(self.source('official-school-published-address', 'https://school.example/notice', observedText='甲大学教务 https://right.example/jw'))
        row, _ = self.apply()
        self.assertEqual('https://right.example/jw', row['url'])
        self.assertEqual('unverified', row['status'])
        correction['parserFamily'] = 'qz'
        self.assertEqual('', self.apply()[0]['url'])

    def test_stale_school_url_missing_or_duplicate_proof_is_rejected(self):
        for field, value in [('name', '乙大学'), ('url','https://stale.example')]:
            proof = dict(self.proof, **{field:value})
            with self.assertRaises(ValueError): apply_ownership([self.row], {'entries':[proof]}, set())
        for proofs in ([], [self.proof, self.proof]):
            with self.assertRaises(ValueError): apply_ownership([self.row], {'entries':proofs}, set())

    def test_unrecognized_verdict_and_unattributed_verification_are_rejected(self):
        for status in ('future', 'verified'):
            self.proof['status'] = status
            with self.assertRaises(ValueError): self.apply()

    def test_graduate_identity_is_retained_when_blocked(self):
        self.row['status'] = 'grad_unverified'
        self.assertEqual('grad_pending', self.apply()[0]['status'])

    def test_published_address_rejects_different_ports_and_host_prefixes(self):
        for target, published in [
            ('http://jw.example:80/', 'http://jw.example:8088/'),
            ('http://jw.example/', 'http://jw.example:8088/'),
            ('https://jw.example/', 'https://notjw.example/'),
            ('https://jw.example/', 'https://jw.example.invalid/'),
        ]:
            self.row['url'] = self.proof['url'] = target
            self.proof.update(status='verified', sources=[self.source(
                'official-school-published-address', 'https://school.example/notice',
                observedText='甲大学教务 '+published)])
            with self.subTest(target=target, published=published), self.assertRaises(ValueError):
                self.apply()

class StrictEndpointTest(unittest.TestCase):
    cases = [
        ('https://shared.example/login', 'https://shared.example/tenant-b/login'),
        ('https://shared.example/sso/tenant-a', 'https://shared.example/tenant-b/login'),
        ('https://shared.example/jwapp/tenant-a/login', 'https://shared.example/jwapp/tenant-b/login'),
        ('https://shared.school.edu.cn/', 'https://shared.school.edu.cn/tenant-b/login'),
        ('https://shared.example/jwapp', 'https://shared.example/jwapp/tenant-b/login'),
        ('https://shared.example/login?tenant=a', 'https://shared.example/login?tenant=b'),
        ('https://shared.example/tenant-a/login', 'https://shared.example/'),
        ('http://shared.example/login', 'https://shared.example/login'),
        ('https://shared.example/login#/tenant-a', 'https://shared.example/login#/tenant-b'),
        ('https://shared.example/login/', 'https://shared.example/login'),
    ]

    def proof(self, kind, observed='甲大学', **kwargs):
        return dict(kind=kind, url='https://school.edu.cn/notice', checkedAt='2026-10-15', observedText=observed, **kwargs)

    def test_all_review_counterexamples_fail_for_links_and_published_urls(self):
        for link, target in self.cases:
            with self.subTest(link=link, target=target):
                self.assertFalse(link_matches(link, target, 'https://www.school.edu.cn/'))
                self.assertFalse(source_proves(self.proof('official-school-link', linkedUrls=[link]), '甲大学', target))
                self.assertFalse(source_proves(self.proof('official-school-published-address', '甲大学 '+link), '甲大学', target))

    def test_exact_path_query_fragment_and_root_normalization(self):
        for url in ['https://shared.example/tenant/login?a=1#scope', 'http://shared.example/']:
            self.assertTrue(link_matches(url, url))
            self.assertTrue(source_proves(self.proof('official-school-published-address', '甲大学 '+url), '甲大学', url))
        self.assertTrue(link_matches('https://shared.example', 'https://shared.example:443/'))
        self.assertFalse(link_matches('https://shared.example:bad/', 'https://shared.example/'))

    def test_directed_archived_redirect_is_required(self):
        root, target = 'https://shared.example/', 'https://shared.example/tenant/login?school=1'
        source = self.proof('official-school-link', linkedUrls=[root])
        capture = dict(archive='research/capture.json', checkedAt='2026-10-15', requestedUrl=root, bodySha256='a'*64, redirects=[dict(http=302, **{'from':root, 'to':target})])
        source['endpointRedirects'] = [capture]
        self.assertTrue(source_proves(source, '甲大学', target))
        for patch in [dict(bodySha256=''), dict(archive=''), dict(requestedUrl='https://other.example/'), dict(redirects=[dict(http=200, **{'from':root, 'to':target})]), dict(redirects=[dict(http=302, **{'from':'https://other.example/', 'to':target})]), dict(redirects=[dict(http=302, **{'from':root, 'to':'https://shared.example/login'})])]:
            source['endpointRedirects'] = [dict(capture, **patch)]
            with self.subTest(patch=patch):self.assertFalse(source_proves(source, '甲大学', target))

    def test_same_sso_destination_does_not_prove_two_tenants(self):
        root, target, sso = 'https://shared.example/tenant-a', 'https://shared.example/tenant-b', 'https://sso.example/login'
        captures=[dict(archive='capture.json', checkedAt='2026-10-15', requestedUrl=u, bodySha256='a'*64, redirects=[dict(http=302, **{'from':u, 'to':sso})]) for u in [root,target]]
        self.assertFalse(source_proves(self.proof('official-school-link', linkedUrls=[root], endpointRedirects=captures), '甲大学', target))

    def test_published_host_fragment_or_whitespace_cannot_prove_full_path(self):
        for observed in ['甲大学 shared.example', '甲大学 https://shared.example', '甲大学 https://sha red.example/tenant/login']:
            self.assertFalse(source_proves(self.proof('official-school-published-address', observed), '甲大学', 'https://shared.example/tenant/login'))

class RawMetaChainTest(unittest.TestCase):
    root = 'http://120.224.233.70:8189/'
    target = root + 'jwglxt/xtgl/login_slogin.html'
    notice = 'https://school.edu.cn/notice'

    def capture(self, url, body='', status=200, location=None):
        headers = 'HTTP/1.1 ' + str(status) + ' Response' + chr(13) + chr(10)
        if location is not None:
            headers += 'Location: ' + location + chr(13) + chr(10)
        result = dict(url=url, checkedAt='2026-10-15', encoding='utf-8')
        for key, value in [('body', body), ('headers', headers)]:
            raw = value.encode()
            result[key+'Base64'] = base64.b64encode(raw).decode()
            result[key+'Sha256'] = hashlib.sha256(raw).hexdigest()
        return result

    def setUp(self):
        self.source = dict(kind='official-school-meta-chain', url=self.notice, checkedAt='2026-10-15', metaChain=dict(
            archive='research/raw-chain.json',
            publication=self.capture(self.notice, '<title>甲大学</title>教务网址：<span>1</span><span>20.224.233.70:8189</span>。'),
            publicationQuote='教务网址：120.224.233.70:8189。', publishedRoot=self.root,
            responses=[
                self.capture(self.root, '<meta http-equiv=refresh content="1;url=/jwglxt">'),
                self.capture(self.root+'jwglxt', status=302, location='/jwglxt/'),
                self.capture(self.root+'jwglxt/', status=302, location='/jwglxt/xtgl/login_slogin.html'),
                self.capture(self.target, '<h1>甲大学教务系统</h1>'),
            ]))

    def valid(self):
        return source_proves(self.source, '甲大学', self.target)

    def test_literal_adjacent_html_text_and_complete_chain_pass(self):
        self.assertTrue(self.valid())

    def test_correction_with_chain_stays_unauthenticated(self):
        row = dict(name='甲大学', catalogId='one', url='https://wrong.example/', type='zf_new', status='unverified')
        correction = dict(status='verified', url=self.target, parserFamily='zf_new', sources=[self.source, dict(kind='entry-page', url=self.target, checkedAt='2026-10-15', observedText='甲大学')])
        proof = dict(name=row['name'], catalogId='one', url=row['url'], status='contradicted', proposedCorrection=correction)
        result = apply_ownership([row], {'entries':[proof]}, set())
        self.assertEqual(self.target, row['url'])
        self.assertEqual('unverified', row['status'])
        self.assertFalse(row['enableFetch'])
        self.assertFalse(result[0]['authenticatedImportTested'])

    def test_raw_meta_destination_overrides_hand_written_to(self):
        captures = self.source['metaChain']['responses']
        captures[0] = self.capture(self.root, '<meta http-equiv=refresh content="1;url=/different">')
        captures[0]['to'] = captures[1]['url']
        self.assertFalse(self.valid())

    def test_raw_body_and_header_hash_tampering_fails(self):
        for index, key in [(0,'bodyBase64'), (1,'headersBase64')]:
            self.setUp()
            self.source['metaChain']['responses'][index][key] = base64.b64encode(b'tampered').decode()
            self.assertFalse(self.valid())

    def test_missing_malformed_or_multiple_meta_refresh_fails(self):
        for body in ['', '<script>location="/jwglxt"</script>', '<meta http-equiv=refresh content="url=/jwglxt">', '<meta http-equiv=refresh content="1;url=/jwglxt">'*2]:
            self.source['metaChain']['responses'][0] = self.capture(self.root, body)
            self.assertFalse(self.valid())

    def test_missing_wrong_or_external_location_fails(self):
        for destination in [None, '/different', 'https://other.example/jwglxt/']:
            self.source['metaChain']['responses'][1] = self.capture(self.root+'jwglxt', status=302, location=destination)
            self.assertFalse(self.valid())

    def test_incomplete_chain_and_wrong_final_identity_fail(self):
        for body in ['乙大学教务系统', '<script>甲大学</script>', '<meta http-equiv=refresh content="1;url=/other">甲大学']:
            self.source['metaChain']['responses'][-1] = self.capture(self.target, body)
            self.assertFalse(self.valid())
        self.setUp()
        self.source['metaChain']['responses'].pop(1)
        self.assertFalse(self.valid())

    def test_wrong_publication_address_scope_or_quote_fails(self):
        for text in ['甲大学教务网址：120.224.233.71:8189。', '甲大学教务网址：120.224.233.70:81890。', '甲大学教务网址：120.224.233.70:8189/tenant。', '甲大学教务网址：1 20.224.233.70:8189。', '乙大学教务网址：120.224.233.70:8189。']:
            self.setUp()
            chain = self.source['metaChain']
            chain['publication'] = self.capture(self.notice, text)
            chain['publicationQuote'] = text
            self.assertFalse(self.valid())
        self.setUp()
        self.source['metaChain']['publicationQuote'] = 'fabricated quote'
        self.assertFalse(self.valid())

    def test_metadata_and_publication_capture_integrity_required(self):
        for key in ['archive', 'publishedRoot', 'publicationQuote']:
            self.setUp()
            self.source['metaChain'][key] = ''
            self.assertFalse(self.valid())
        for key, value in [('bodySha256','a'*64), ('url','https://different.example/notice'), ('checkedAt','')]:
            self.setUp()
            self.source['metaChain']['publication'][key] = value
            self.assertFalse(self.valid())

    def test_exact_target_and_graduate_scope_stay_strict(self):
        for target in [self.target+'?tenant=other', self.target+'/', self.target.replace('http:', 'https:')]:
            self.assertFalse(source_proves(self.source, '甲大学', target))
        self.assertFalse(source_proves(self.source, '甲大学 - 研究生', self.target))

    def test_sensitive_headers_are_rejected_even_with_valid_hash(self):
        for header in ['Set-Cookie', 'Authorization', 'X-Session-Token']:
            self.setUp()
            capture = self.source['metaChain']['responses'][0]
            raw = base64.b64decode(capture['headersBase64']) + (header + ': synthetic-placeholder' + chr(13) + chr(10)).encode()
            capture['headersBase64'] = base64.b64encode(raw).decode()
            capture['headersSha256'] = hashlib.sha256(raw).hexdigest()
            self.assertFalse(self.valid())

    def test_script_and_nonempty_input_snapshots_are_rejected(self):
        for body in ['甲大学<script>var value=1;</script>', '甲大学<input name=csrftoken value=synthetic-placeholder>']:
            self.source['metaChain']['responses'][-1] = self.capture(self.target, body)
            self.assertFalse(self.valid())

if __name__ == '__main__': unittest.main()
