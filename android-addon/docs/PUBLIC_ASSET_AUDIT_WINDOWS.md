# Windows 公共资源字节审计

固定 PUBLIC 与内嵌候选中的 35 项允许资源均与提交 `7c413543ce5cab0ada229b5fb6008f1f800b02ac` 的原始 Git blob 逐字节一致。此前 Windows 工作区对以下三项文本资源进行了 CRLF 检出，导致 `PrivateArtifactAudit.py:58` 的严格比较失败；这不是凭据模式命中。

## 修复范围

- 根目录 `.gitattributes` 仅为下表三个源路径指定 `text eol=lf`。
- `addon_assets.py` 显式以 UTF-8 读取 gallery catalog。
- 修复前先验证三个工作区文件仅在 CRLF/LF 上与 Git blob 不同，再恢复 LF；资源内容无变更。
- 保留原审计的全部断言、允许清单、摘要校验及凭据模式检查。

| 源路径 | LF 字节数 | SHA-256 |
|---|---:|---|
| `android-addon/assets/reader-demo-7392.txt` | 11961 | `369b632f58e96d625731765ff2159d453fefcdca6c4e33d710878fdb7706f643` |
| `android-addon/THIRD_PARTY_MUSIC_LICENSE.txt` | 1304 | `b7d3e4167c952a2b150b07ba3b59e806d569701c3f8a7d346a8fd0180f7cd65b` |
| `android-addon/assets/card.schema.json` | 2465 | `db38d98ed4cf7678f881cb67ac4489d44c2fc72c705bdb022df6ad6a58c396cd` |

已有检出不会因新增属性自动重写。应先确认没有用户内容修改，再针对这三个路径恢复受审 LF 字节；不要批量重写资源，也不要修改全局 Git 配置。

## 原命令验收记录

以下命令从仓库根目录运行，`python` 应替换为本机现有 Python 可执行文件。本轮使用 Python 的 `-X utf8=0`，确认 `sys.flags.utf8_mode == 0`、`locale.getencoding() == "cp1252"`，以验证显式编码修复。

```powershell
python -B -X utf8=0 android-addon/tests/ReaderDemoTest.py
python -B -X utf8=0 android-addon/tests/PrivateArtifactAudit.py artifacts/embedded-aa-bootstrap-20261030-005400/TurboIO-AA-bootstrap-signed.apk --dex artifacts/embedded-aa-bootstrap-20261030-005400/assembly/dex/classes.dex
git diff --check
```

三条命令均通过。ReaderDemoTest 验证固定摘要、字节数和行数；PrivateArtifactAudit 原样输出：

```json
{"checked_owned_inputs":173,"credential_pattern_hits":[],"public_assets_match_sources":true,"test_doubles_absent":true,"addon_dex_sha256":"2b99b505630dd9531ff4ac2cef10755164159d9365cc71971984cd486fc5aac0"}
```

本次通过使用实际工作区资源，不使用此前诊断中的 Git blob 内存替换。历史 `private-artifact-audit-utf8.log` 仍准确记录修复前失败，未覆盖该日志。

`git ls-files --eol` 确认三个路径均为 `i/lf w/lf attr/text eol=lf`。对每个路径比较 `git cat-file --filters HEAD:<path>`、`git cat-file blob HEAD:<path>` 和工作区原始字节，均严格相等，证明当前 Git 检出过滤器保留受审字节。

不要给这条嵌入审计命令添加 `--source <固定PUBLIC>`：通用审计的该选项用于在官方宿主新增 DEX 槽位，本例独立装配有意替换已有 `classes4.dex`。来源保真由固定整包哈希和成员差异检查另行验证。

## 单字节篡改拒绝复核

本轮分别在内存中对三个 APK 资源成员的读取结果翻转一个字节，执行未改动的原审计。三个用例全部被原字节相等断言拒绝，未修改 APK、资源文件或审计脚本。以下程序可从仓库根目录通过 `python -B -X utf8=0 -` 的标准输入运行；它只输出结果和资源路径。

```python
import contextlib, io, pathlib, runpy, sys, zipfile
root = pathlib.Path.cwd()
apk = root / "artifacts/embedded-aa-bootstrap-20261030-005400/TurboIO-AA-bootstrap-signed.apk"
dex = apk.parent / "assembly/dex/classes.dex"
audit = root / "android-addon/tests/PrivateArtifactAudit.py"
read = zipfile.ZipFile.read
for member in (
    "assets/turboio/reader/demo-7392.txt",
    "assets/turboio/licenses/music-mit.txt",
    "assets/turboio/card.schema.json",
):
    calls = []
    def changed(self, name, *args, **kwargs):
        data = read(self, name, *args, **kwargs)
        if pathlib.Path(self.filename).resolve() == apk and name == member:
            result = bytes([data[0] ^ 1]) + data[1:]
            assert len(result) == len(data)
            assert sum(a != b for a, b in zip(data, result)) == 1
            calls.append(name)
            return result
        return data
    zipfile.ZipFile.read = changed
    sys.argv = [str(audit), str(apk), "--dex", str(dex)]
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(audit), run_name="__main__")
    except AssertionError as error:
        assert str(error) == "Asset differs from reviewed source: " + member
        assert calls == [member]
        print("single-byte change rejected:", member)
    else:
        raise AssertionError("Tampered resource accepted")
    finally:
        zipfile.ZipFile.read = read
```

## 产物身份与结论边界

修复后重新计算的整包 SHA-256 保持不变：

- 固定 PUBLIC：`artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk`，`d39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0`。
- 内部候选：`artifacts/embedded-aa-bootstrap-20261030-005400/TurboIO-AA-bootstrap-signed.apk`，`adcda2d906730a4fcc1de3c935fb00bcf90baf8c111e701896d0ecba2f67a67a`。

这是本地构建环境及公共资源审计修复。未修改 AA 仓库、未重新构建或签名 APK、未安装或执行设备操作。通过有界凭据模式检查不替代完整安全审查、签名验收或业务运行验收，也不自动授权公开发布。
