import unittest
from build_pages import snapshot


def release(version, date, names, **extra):
    return dict(tag_name=version, published_at=date, assets=[dict(name=name, download_count=count, browser_download_url='https://github.com/owner/project/releases/download/'+version+'/'+name, size=1024) for name, count in names], **extra)


class SnapshotTest(unittest.TestCase):
    def test_latest_available_per_platform_and_all_historical_downloads(self):
        result = snapshot([
            release('v1', '2026-01-01', [('app.msi', 5), ('app.dmg', 7), ('app.run', 2), ('checksums.txt', 100)]),
            release('v2', '2026-02-01', [('app.deb', 3), ('app.run', 4), ('app.tar.gz', 6)]),
            release('v3-beta', '2026-03-01', [('app.msi', 500)], prerelease=True),
            release('v4', '2026-04-01', [('app.dmg', 800)], draft=True),
        ])
        self.assertEqual(27, result['downloads'])
        self.assertEqual('v1', result['platforms']['windows']['version'])
        self.assertEqual('v1', result['platforms']['macos']['version'])
        self.assertEqual('v2', result['platforms']['linux']['version'])
        self.assertEqual('app.run', result['platforms']['linux']['assets'][0]['name'])
        self.assertEqual(15, result['platforms']['linux']['downloads'])

    def test_empty_releases_do_not_invent_downloads_or_links(self):
        result = snapshot([])
        self.assertEqual(0, result['downloads'])
        for platform in result['platforms'].values():
            self.assertIsNone(platform['version'])
            self.assertEqual([], platform['assets'])


if __name__ == '__main__':
    unittest.main()
