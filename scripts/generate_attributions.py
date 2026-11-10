#!/usr/bin/env python3
"""Render the reviewed attribution inventory into repository documentation."""
from pathlib import Path
import argparse
import json

ROOT = Path(__file__).resolve().parents[1]
LOCALES = {
    'values': ('依赖、素材与研究参考', ['源码移植与素材来源', '运行、构建与测试依赖', '日历与地理数据', '设计规范与官方资料', '液态玻璃调研', '厂商通知与小组件调研', '后台提醒可靠性调研'],
               '感谢以下项目的作者和维护者。各项的使用范围、证据记录与许可原文见仓库 ACKNOWLEDGEMENTS.md；调研条目不表示已集成。',
               '本项目的液态玻璃源码模块基于 SleepDown课程表 修改；原作者：xiaomanjun233；原项目：https://github.com/xiaomanjun233/SleepDown-Schedule。Sleepy 由本项目维护，修改包括 Android 构建接线、Compose 版本适配及 FLClash 底栏集成。SleepDown 自有内容采用非商业、源码可见许可 1.1；Kyant 原始代码保留 Apache-2.0。FLClash 的 Kotlin 与 AGSL 移植保留 GPL-3.0，Flutter 超椭圆算法移植保留 BSD-3-Clause。许可与第三方声明原文随应用分发。'),
    'values-zh-rTW': ('相依套件、素材與研究參考', ['原始碼移植與素材來源', '執行、建置與測試相依套件', '日曆與地理資料', '設計規範與官方資料', '液態玻璃調研', '廠商通知與小工具調研', '背景提醒可靠性調研'],
               '感謝以下專案的作者與維護者。各項使用範圍、證據記錄與授權原文見儲存庫 ACKNOWLEDGEMENTS.md；調研項目不表示已整合。',
               '本專案的液態玻璃原始碼模組基於 SleepDown課程表 修改；原作者：xiaomanjun233；原專案：https://github.com/xiaomanjun233/SleepDown-Schedule。Sleepy 由本專案維護，修改包括 Android 建置接線、Compose 版本適配及 FLClash 底欄整合。SleepDown 自有內容採用非商業、原始碼可見授權 1.1；Kyant 原始碼保留 Apache-2.0。FLClash 的 Kotlin 與 AGSL 移植保留 GPL-3.0，Flutter 超橢圓演算法移植保留 BSD-3-Clause。授權與第三方聲明原文隨應用程式分發。'),
    'values-en': ('Libraries, assets and research references', ['Source ports and assets', 'Runtime, build and test dependencies', 'Calendar and geographic data', 'Design guidance and official documentation', 'Liquid glass research', 'Vendor notification and widget research', 'Background reminder reliability research'],
               'Thank you to the authors and maintainers of these projects. ACKNOWLEDGEMENTS.md in the repository records usage, evidence and license texts. Research references do not imply integration.',
               'The liquid glass source module is modified from SleepDown Schedule by xiaomanjun233: https://github.com/xiaomanjun233/SleepDown-Schedule. Sleepy is maintained by this project. Changes include Android build wiring, Compose version adaptation and FLClash dock integration. SleepDown-owned content uses its source-available non-commercial license 1.1; original Kyant code retains Apache-2.0. The FLClash Kotlin and AGSL ports retain GPL-3.0, and the Flutter superellipse port retains BSD-3-Clause. Original license texts and third-party notices are bundled with the app.'),
    'values-ja': ('ライブラリ、素材、調査資料', ['移植コードと素材の出典', '実行・ビルド・テストの依存ライブラリ', 'カレンダーと地理データ', 'デザイン指針と公式資料', 'リキッドグラスの調査', 'メーカー通知とウィジェットの調査', 'バックグラウンド通知の信頼性調査'],
               '各プロジェクトの作者とメンテナーに感謝します。利用範囲、根拠、ライセンス原文はリポジトリの ACKNOWLEDGEMENTS.md に記載しています。調査対象であることは組み込みを意味しません。',
               'リキッドグラスのソースモジュールは xiaomanjun233 の SleepDown Schedule を基に変更しています：https://github.com/xiaomanjun233/SleepDown-Schedule。Sleepy は本プロジェクトが保守し、Android ビルド設定、Compose バージョン対応、FLClash ドック統合を変更しています。SleepDown 独自の内容には非商用・ソース公開ライセンス 1.1 が適用され、Kyant の原コードには Apache-2.0 が維持されます。FLClash の Kotlin・AGSL 移植には GPL-3.0、Flutter の超楕円アルゴリズム移植には BSD-3-Clause が維持されます。ライセンスと第三者声明の原文をアプリに同梱しています。'),
    'values-es': ('Bibliotecas, recursos y referencias', ['Código portado y recursos', 'Dependencias de ejecución, compilación y pruebas', 'Datos de calendario y geografía', 'Guías de diseño y documentación oficial', 'Investigación sobre cristal líquido', 'Investigación de notificaciones y widgets', 'Fiabilidad de recordatorios en segundo plano'],
               'Gracias a los autores y responsables de estos proyectos. ACKNOWLEDGEMENTS.md documenta el uso, las pruebas de procedencia y las licencias. Una referencia de investigación no implica integración.',
               'El módulo de cristal líquido se modificó a partir de SleepDown Schedule, de xiaomanjun233: https://github.com/xiaomanjun233/SleepDown-Schedule. Este proyecto mantiene Sleepy. Los cambios incluyen la configuración de Android, la adaptación de Compose y la integración de la barra FLClash. El contenido propio de SleepDown utiliza su licencia no comercial de código visible 1.1; el código original de Kyant conserva Apache-2.0. Las adaptaciones de FLClash a Kotlin y AGSL conservan GPL-3.0 y la de las superelipses de Flutter conserva BSD-3-Clause. La aplicación incluye las licencias originales y los avisos de terceros.'),
}
# The protocol audit includes both indirect and negative research candidates.
for locale, label in {
    'values': '教务协议调研补充', 'values-zh-rTW': '教務協定調研補充',
    'values-en': 'Additional academic-system research', 'values-ja': '教務システム調査の補足',
    'values-es': 'Investigación adicional de sistemas académicos',
}.items():
    LOCALES[locale][1].append(label)
LOCALES['values-zh-rCN'] = LOCALES['values']


def render(manifest):
    entries = manifest['entries']
    groups = manifest['groups']
    outputs = {}
    lines = ['# 引用来源与致谢', '',
             '感谢为 Sleepy 提供源码、数据、协议资料、设计参考和反馈的作者、维护者与使用者。', '',
             f'本次核对日期为 {manifest["audited_at"]}，代码基线为 `{manifest["baseline"]}`。下表补齐现有学校与教务协议致谢之外的 {len(entries)} 项来源，并保留已有致谢入口。每项分别记录用途、许可状态和本仓库中的依据；调研、历史原型和间接参考不等同于当前依赖。', '',
             '## 源码来源声明', '', LOCALES['values'][3], '',
             'SleepDown 的导入修订未在现有记录中固定，当前保留的许可文本是本次查询快照。这项出处补全不表示已经确认定制代码的分发许可兼容性。ChinaGeoJson 的代码许可与 DataV 数据授权也分别记录，沿用原数据清单中的未确认状态。', '',
             '## 贡献者与既有教务致谢', '',
             '感谢 lingion、jim139129、Kerry1020、YYiChen、Cold577、LzBsA、echo0731、X-art-hash，以及提供学校样本、真机测试、问题反馈、翻译和使用建议的同学。贡献者账户已与 GitHub contributors 接口和已合并 PR 交叉核对。', '',
             '[完整贡献历史](https://github.com/lingion/sleepy/graphs/contributors)与[问题及反馈](https://github.com/lingion/sleepy/issues)保留持续更新的记录。dependabot 依赖维护记录见仓库提交历史。', '',
             '既有教务来源包括 WakeUp / WakeupSchedule_BUPT、拾光 shiguang_warehouse、LumaSchedule、各学校维护者和协议交叉验证项目。原有逐校、逐仓与六语言致谢全部保留：', '',
             '- [官网开源许可与致谢](https://sleepy.is-my.app/licenses/)',
             '- [历史学校、协议与六语言声明](docs/attribution-audit/legacy-credits.json)',
             '- [解析器来源说明](app/src/main/assets/PARSERS_NOTICE.json)',
             '- [拾光脚本许可](app/src/main/assets/third_party/shiguang/LICENSE-MIT.txt)',
             '- [学校及协议研究归档](docs/history/)',
             '- [WakeUp 目录引用记录](docs/wakeup-catalog-absorb-2026-10-28/attribution-candidates.json)', '',
             '## 清单维护', '',
             '下列清单的真源为 [ATTRIBUTIONS.json](app/src/main/assets/ATTRIBUTIONS.json)。修改后运行 `python3 scripts/generate_attributions.py` 同步本文。运行 `python3 scripts/export_licenses.py --site /path/to/sleepy-site` 同步官网来源数据与许可原文，运行 `python3 scripts/generate_attributions.py --check` 检查漂移。未识别许可证或历史链接不可用的条目保留未确认状态，不推定为可复用代码。GitHub API 的许可字段是核验当天的元数据，不替代具体版本的许可原文。', '']
    for group, label in zip(groups, LOCALES['values'][1]):
        lines += ['## ' + label, '', '| 项目与作者 | 许可记录 | 用途 | 本仓库依据 |', '| --- | --- | --- | --- |']
        for x in entries:
            if x['group'] != group: continue
            ev = '；'.join(f'[{p}]({p})' for p in x['evidence'])
            status = '；历史链接本次未确认' if x['verification']['status'] == 'unverified' else ''
            cells = [f'[{x["project"]}]({x["url"]})，{x["author"]}', x['license'] + status, x['usage'], ev]
            lines.append('| ' + ' | '.join(c.replace('|', '\\|').replace('\n', ' ') for c in cells) + ' |')
        lines.append('')
    lines += ['## 厂商与平台资料', '',
              '[完整平台资料引用索引](docs/attribution-audit/platform-sources.md)逐条链接到现有仓库中的研究记录，覆盖 Android、Apple、华为、荣耀、小米、OPPO、一加、realme、vivo、魅族、三星及相关社区资料。索引保留历史出处，不宣称所有页面目前可访问。', '',
              '[液态玻璃原始研究来源](docs/attribution-audit/glass-research-sources.md)保留 Apple 设计指导、AOSP 模糊处理与 Kyant 文档。', '',
              '后台方案还有仅记录简称而无法唯一定位的对象：LinkSheet、Fankes、NewPipe、tusky、kdeconnect、osmand、organicmaps、libretorrent、droid-ify、MiPushFramework。感谢这些被调研项目；原始记录没有足够信息确定具体仓库，因此不补造 owner/repo 或许可证。', '',
              '## 随包保留的许可与声明', '',
              '以下文件位于 Android assets 中，随应用分发。SleepDown 第三方声明保留上游原文，描述的是上游项目的依赖范围，不表示 Sleepy 引入了其中的全部依赖。', '']
    lines += [f'- [{Path(n["path"]).name}](app/src/main/assets/{n["path"]})' for n in manifest['notices']]
    lines += ['', '核心来源原有记录也保留于 [Kyant 模块](third-party/kyant-backdrop/README.md)、[FLClash 许可](third-party/notices/FLClash-GPL-3.0.txt)、[地理数据出处](app/src/main/assets/school_city_boundaries.source.json)和 [NLSC 声明](app/src/main/assets/third_party/NLSC-NOTICE.txt)。', '']
    outputs[ROOT / 'ACKNOWLEDGEMENTS.md'] = '\n'.join(lines)
    return outputs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'app/src/main/assets/ATTRIBUTIONS.json').read_text())
    stale = []
    for path, content in render(manifest).items():
        if args.check:
            if not path.exists() or path.read_text() != content: stale.append(str(path.relative_to(ROOT)))
        else:
            path.write_text(content)
    if stale:
        raise SystemExit('Attribution outputs are stale: ' + ', '.join(stale))
    print(f'Attributions {"verified" if args.check else "generated"}: {len(manifest["entries"])} entries.')


if __name__ == '__main__':
    main()
