#!/usr/bin/env python3
"""operational_url_ok / repair_declared_url — the promote-line safety bar.

WakeUP 6.6.2 catalog rows are absorbed unconditionally (user ruling), but an
operational catalog row must be openable by Android: JwSchoolInfo.hasUrl uses
java.net.URI(url) and requires scheme http(s) + non-null host + no userinfo.
Python's urlsplit is laxer than Java URI (it keeps 'http://是' and
'http://119.0.139.143.8899/jwglxt' alive, and 'REDACTED'-free space/NBSP URLs
pass valid_url/public_host in catalog_ownership). Ground truth verified against
temurin-17 java.net.URI: space/NBSP/full-width raise URISyntaxException in ANY
component (while a non-ASCII character in the fragment alone is tolerated);
bad hosts parse with getHost() == None.

repair_declared_url covers the one recoverable shape found in the shipped
assets: '<base-url> - <suffix>' (base and login path joined by ' - ' by the
upstream dump; the same shape sits in HEAD-era wakeup-batch-probe admissions,
e.g. 'https://yth.syzyedu.com/ - /'). Other dirty shapes (two URLs glued by
whitespace, Chinese-labelled 校外/校内 strings, 'http://是', schemeless blobs)
carry no unambiguous repair and are rejected instead of guessed.
"""
import unittest

from wakeup_662_batch_promote import operational_url_ok, repair_declared_url


class OperationalUrlOkTest(unittest.TestCase):
    def test_java_uri_rejects_are_rejected(self):
        # each row: parsed OK by Python urlsplit, fails java.net.URI or host=None
        for url in [
            'http://jwgl.bjvtc.com/xtgl/lo gin_slogin.html',        # space in path
            'http://是',                                            # host=None in Java
            'https://cas.gyu.edu.cn/lyuapServer/login\xa0\xa0 https://jwxt.gyu.edu.cn/jsxsd/',
            'http://119.0.139.143.8899/jwglxt',                     # 5-label numeric host
            'http://校外http//183.215.42.46:82/jwglxt/xtgl/login_slogin.html校内http://172.20.36.2:82/jwglxt/xtgl/',
            'http://192.168.88.114  59.61.86.138:18080',
            'http://登录',
            '111.75.199.71:8889',                                   # no scheme
            'tjdz.edu.cn/engine2/general-rest/outer-proxy-detail-url',
        ]:
            with self.subTest(url=url[:50]):
                self.assertFalse(operational_url_ok(url))

    def test_rfc1918_and_reserved_are_rejected(self):
        for url in [
            'http://10.134.240.60/',
            'http://172.20.36.2:82/jwglxt/xtgl/',
            'http://192.168.1.1/',
        ]:
            with self.subTest(url=url):
                self.assertFalse(operational_url_ok(url))

    def test_credentials_are_rejected(self):
        self.assertFalse(operational_url_ok('http://admin:pass@jw.example.edu.cn/'))

    def test_good_urls_from_the_catalog_pass(self):
        for url in [
            'https://jwxt.gyu.edu.cn/jsxsd/',
            'https://webvpn.jsu.edu.cn/https/77726476706e69737468656265737421e0f6528f693a7b45300d8db9d6562d/',
            'http://222.77.99.243:8091/jwglxt/xtgl/login_slogin.html',
            'https://hbut.jw.chaoxing.com/admin',
            # Java tolerates a non-ASCII character in the fragment only
            'https://jwxt.scvtc.edu.cn/#/jwxt/gly/login·',
            'http://localhost:8080/jw',
        ]:
            with self.subTest(url=url[:50]):
                self.assertTrue(operational_url_ok(url))


class RepairDeclaredUrlTest(unittest.TestCase):
    def test_dash_shape_rejoins_base_and_suffix(self):
        self.assertEqual(
            repair_declared_url('https://hbssyys.aixiaoyuan.cn/portal/ - /login'),
            'https://hbssyys.aixiaoyuan.cn/portal/login')
        self.assertEqual(
            repair_declared_url('https://yth.syzyedu.com/ - /'),
            'https://yth.syzyedu.com/')
        # base path empty, suffix absolute path -> suffix
        self.assertEqual(
            repair_declared_url('http://zhxy.jmjsxy.com - /index'),
            'http://zhxy.jmjsxy.com/index')

    def test_dash_with_redacted_suffix_keeps_base(self):
        self.assertEqual(
            repair_declared_url('http://218.7.19.201:19880/wui/index.html - /?logintype=1&_key=REDACTED'),
            'http://218.7.19.201:19880/wui/index.html')

    def test_repaired_output_is_operationally_valid(self):
        self.assertTrue(operational_url_ok(
            repair_declared_url('https://hbssyys.aixiaoyuan.cn/portal/ - /login')))

    def test_unrecoverable_shapes_left_untouched(self):
        for url in ['http://是', 'http://10.134.240.60/\xa0 http://10.134.240.68/',
                    'http://119.0.139.143.8899/jwglxt']:
            with self.subTest(url=url[:40]):
                self.assertEqual(repair_declared_url(url), url)


if __name__ == '__main__':
    unittest.main()
