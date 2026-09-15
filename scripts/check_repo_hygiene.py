"""Reject temporary artifacts in the Git index, including forced additions."""

from pathlib import Path, PurePosixPath
import subprocess
import sys


DOC_ASSETS = {
    "docs/icon-concepts/SELECTED-app-icon.png": "Approved launcher icon source",
    "docs/icon-concepts/generate_mipmaps.py": "Launcher resource generator",
}


def violation(path):
    """Return the policy failure for a Git path, or None when permitted."""
    parts = PurePosixPath(path).parts
    if not parts:
        return None
    if parts[0].lower() == "artifacts":
        return "artifacts/ is local-only"
    if parts[0].lower() == "docs":
        if parts[0] == "docs" and (path.endswith(".md") or path in DOC_ASSETS):
            return None
        return "docs/ permits Markdown and explicitly approved assets only"
    return None


def main():
    root = Path(__file__).resolve().parents[1]
    try:
        result = subprocess.run(
            ["git", "ls-files", "--cached", "-z"],
            cwd=root, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Cannot inspect Git index: {error}", file=sys.stderr)
        return 2
    paths = result.stdout.decode("utf-8", errors="surrogateescape").split("\0")
    failures = [(path, violation(path)) for path in paths if path]
    failures = [(path, reason) for path, reason in failures if reason]
    if failures:
        for path, reason in failures:
            print(f"{path}: {reason}", file=sys.stderr)
        print("See docs/README.md for storage and approval rules.", file=sys.stderr)
        return 1
    print("Repository hygiene check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
