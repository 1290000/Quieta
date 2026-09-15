# 文档与资源

`docs/` 保存长期维护的说明，不作为调试产物或参考仓库的存放目录。

| 路径 | 用途 |
|------|------|
| `build-windows.md` | Windows 本机构建方法 |
| `installerx-interactions.md` | InstallerX 交互适配、上游来源与验收说明 |
| `root-backend.md` | 独立 Root 后端、管理器识别与真机验收方法 |
| `startup-performance.md` | LibChecker 启动架构参考、线程约束与性能验证方法 |
| `icon-concepts/SELECTED-app-icon.png` | 唯一正式应用图标源图 |
| `icon-concepts/generate_mipmaps.py` | 从正式源图生成 Android 图标资源 |

## 临时资料

截图、录屏帧、UIAutomator XML、图标草稿及临时上游源码放仓库外，或放根目录的 `artifacts/`（已被 Git 忽略）。验收结论写入相关文档，无须将全部过程文件提交。上游实现已移植后，保留来源、版本和许可说明，不再保存重复源码。

新增 Markdown 文档可直接提交。新增确需长期保留的图片或脚本，必须同时更新本表、`.gitignore` 例外及 `scripts/check_repo_hygiene.py` 中的资源白名单，不可用 `git add -f` 代替审批。

暂存改动后，在仓库根目录执行：

```powershell
python scripts/check_repo_hygiene.py
python -m unittest discover -s scripts -p "test_*.py"
```

检查使用 Git 索引，因此会检查强制添加的文件，也能正确处理已暂存的删除。CI 在每次 push 和 pull request 上运行相同检查；它不会删除本地临时文件，也不扫描历史提交。

## 图标生成

需要 Python 3 和 Pillow（仅开发时使用）。在仓库根目录运行 `python docs/icon-concepts/generate_mipmaps.py`。脚本从自身位置解析仓库路径，将图标写入 `app/src/main/res/`；只有需要重新生成正式图标时才运行，并审阅生成资源的差异。
