"""
Sticly Manager - GitHub Senkronizasyonu
=======================================
Git repository islemleri.
"""

import subprocess
import logging
from pathlib import Path
from datetime import datetime
from typing import Tuple, List, Optional

from .utils import SCRIPT_DIR

logger = logging.getLogger("sticly")


class GitHubSync:
    """GitHub senkronizasyon islemlerini yoneten sinif"""

    def __init__(self, repo_path: Optional[Path] = None):
        self.repo_path = repo_path or SCRIPT_DIR.parent

    @property
    def is_git_repo(self) -> bool:
        """Git repo mu kontrol et"""
        return (self.repo_path / ".git").exists()

    def get_status(self) -> Tuple[bool, str, List[str]]:
        """Git durumunu al

        Returns:
            (has_changes, status_text, changed_files)
        """
        if not self.is_git_repo:
            return False, "Git repo bulunamadi", []

        try:
            result = subprocess.run(
                ["git", "status", "--porcelain"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            if not result.stdout.strip():
                return False, "Degisiklik yok", []

            changed_files = []
            for line in result.stdout.strip().split('\n'):
                if line.strip():
                    status = line[:2]
                    file_path = line[3:]
                    changed_files.append({
                        "status": status.strip(),
                        "path": file_path
                    })

            return True, result.stdout, [f["path"] for f in changed_files]

        except Exception as e:
            logger.error(f"Git status hatasi: {e}")
            return False, str(e), []

    def get_diff(self) -> str:
        """Staged ve unstaged degisiklikleri goster"""
        if not self.is_git_repo:
            return ""

        try:
            # Unstaged
            result_unstaged = subprocess.run(
                ["git", "diff"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            # Staged
            result_staged = subprocess.run(
                ["git", "diff", "--cached"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            diff = ""
            if result_staged.stdout:
                diff += "=== STAGED CHANGES ===\n" + result_staged.stdout + "\n"
            if result_unstaged.stdout:
                diff += "=== UNSTAGED CHANGES ===\n" + result_unstaged.stdout

            return diff

        except Exception as e:
            logger.error(f"Git diff hatasi: {e}")
            return ""

    def get_recent_commits(self, count: int = 5) -> List[dict]:
        """Son commit'leri al"""
        if not self.is_git_repo:
            return []

        try:
            result = subprocess.run(
                ["git", "log", f"-{count}", "--pretty=format:%H|%s|%an|%ar"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            commits = []
            for line in result.stdout.strip().split('\n'):
                if line:
                    parts = line.split('|')
                    if len(parts) >= 4:
                        commits.append({
                            "hash": parts[0][:7],
                            "message": parts[1],
                            "author": parts[2],
                            "time": parts[3]
                        })

            return commits

        except Exception as e:
            logger.error(f"Git log hatasi: {e}")
            return []

    def get_current_branch(self) -> str:
        """Mevcut branch adini al"""
        if not self.is_git_repo:
            return ""

        try:
            result = subprocess.run(
                ["git", "branch", "--show-current"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )
            return result.stdout.strip()
        except:
            return ""

    def add_all(self) -> bool:
        """Tum degisiklikleri stage'e ekle"""
        if not self.is_git_repo:
            return False

        try:
            subprocess.run(
                ["git", "add", "-A"],
                cwd=self.repo_path,
                check=True,
                capture_output=True
            )
            logger.info("Degisiklikler stage'e eklendi")
            return True
        except Exception as e:
            logger.error(f"Git add hatasi: {e}")
            return False

    def commit(self, message: Optional[str] = None) -> Tuple[bool, str]:
        """Commit olustur"""
        if not self.is_git_repo:
            return False, "Git repo bulunamadi"

        if message is None:
            message = f"Sticker guncelleme - {datetime.now().strftime('%Y-%m-%d %H:%M')}"

        try:
            result = subprocess.run(
                ["git", "commit", "-m", message],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            if result.returncode == 0:
                logger.info(f"Commit olusturuldu: {message}")
                return True, "Commit basarili"
            else:
                return False, result.stderr or result.stdout

        except Exception as e:
            logger.error(f"Git commit hatasi: {e}")
            return False, str(e)

    def push(self) -> Tuple[bool, str]:
        """Remote'a push et"""
        if not self.is_git_repo:
            return False, "Git repo bulunamadi"

        try:
            result = subprocess.run(
                ["git", "push"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            if result.returncode == 0:
                logger.info("Push basarili")
                return True, "Push basarili"
            else:
                error_msg = result.stderr or result.stdout
                logger.error(f"Push hatasi: {error_msg}")
                return False, error_msg

        except Exception as e:
            logger.error(f"Git push hatasi: {e}")
            return False, str(e)

    def pull(self) -> Tuple[bool, str]:
        """Remote'dan pull et"""
        if not self.is_git_repo:
            return False, "Git repo bulunamadi"

        try:
            result = subprocess.run(
                ["git", "pull"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            if result.returncode == 0:
                logger.info("Pull basarili")
                return True, result.stdout or "Pull basarili"
            else:
                return False, result.stderr or result.stdout

        except Exception as e:
            logger.error(f"Git pull hatasi: {e}")
            return False, str(e)

    def sync(self, message: Optional[str] = None) -> Tuple[bool, str]:
        """Tam senkronizasyon: add, commit, push"""
        has_changes, _, _ = self.get_status()

        if not has_changes:
            return True, "Degisiklik yok, senkronize edilecek bir sey yok"

        # Add
        if not self.add_all():
            return False, "Degisiklikler stage'e eklenemedi"

        # Commit
        success, msg = self.commit(message)
        if not success:
            return False, f"Commit hatasi: {msg}"

        # Push
        success, msg = self.push()
        if not success:
            return False, f"Push hatasi: {msg}"

        return True, "Senkronizasyon tamamlandi"

    def check_conflicts(self) -> bool:
        """Conflict var mi kontrol et"""
        if not self.is_git_repo:
            return False

        try:
            result = subprocess.run(
                ["git", "status", "--porcelain"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            # UU = both modified (conflict)
            for line in result.stdout.split('\n'):
                if line.startswith('UU') or line.startswith('AA') or line.startswith('DD'):
                    return True

            return False

        except:
            return False

    def get_remote_url(self) -> str:
        """Remote URL'i al"""
        if not self.is_git_repo:
            return ""

        try:
            result = subprocess.run(
                ["git", "remote", "get-url", "origin"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )
            return result.stdout.strip()
        except:
            return ""

    def is_ahead_of_remote(self) -> Tuple[bool, int]:
        """Remote'un gerisinde mi kontrol et"""
        if not self.is_git_repo:
            return False, 0

        try:
            # Fetch
            subprocess.run(
                ["git", "fetch"],
                cwd=self.repo_path,
                capture_output=True
            )

            # Commit farki
            result = subprocess.run(
                ["git", "rev-list", "--count", "HEAD...@{u}"],
                cwd=self.repo_path,
                capture_output=True,
                text=True
            )

            if result.stdout.strip():
                count = int(result.stdout.strip())
                return count > 0, count

            return False, 0

        except:
            return False, 0
