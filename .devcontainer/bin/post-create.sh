#!/usr/bin/env bash
# =============================================================
# post-create.sh
#   devcontainer.json の postCreateCommand から呼ばれるスクリプト。
#   コンテナ作成時に「1回だけ」実行される初期セットアップ処理。
# =============================================================

# エラーが起きたら即停止し、未定義変数をエラーにし、パイプ途中の失敗も検知する。
set -euo pipefail

log() { echo -e "\033[1;32m[post-create]\033[0m $*"; }

# -------------------------------------------------------------
# セットアップ失敗を知らせる
#   sudo は常に削除する方針なので、失敗時はコンテナの作り直しを促す。
# -------------------------------------------------------------
fail_notice() {
  echo -e "\033[1;31m[post-create] ✖ セットアップに失敗しました。終了コード: $1\033[0m" >&2
  echo -e "\033[1;31m[post-create]   sudo は削除済みのため、スクリプトの再実行では復旧できません。\033[0m" >&2
  echo -e "\033[1;31m[post-create]   上のエラーを確認し、原因を直してからコンテナを作り直してください。\033[0m" >&2
  echo -e "\033[1;31m[post-create]   VS Code では 'Dev Containers: Rebuild Container' を実行してください。\033[0m" >&2
}

# -------------------------------------------------------------
# sudo を削除する (スクリプト終了時に必ず実行される)
#   途中の処理が失敗しても sudo が残らないように、trap で EXIT 時に呼ぶ。
#   これ以降に root 権限が必要な処理を書く場合は、この関数より前に終わらせること。
# -------------------------------------------------------------
remove_sudo() {
  # 元の終了コードを残し、削除処理は途中で失敗しても最後まで続ける。
  local status=$?
  set +e

  if ! command -v sudo >/dev/null 2>&1; then
    log "sudo は既に削除されています。"
    [[ "$status" -eq 0 ]] || fail_notice "$status"
    exit "$status"
  fi

  log "sudo を削除します。"

  # 次の3つを、1回の sudo (root のシェル) の中で、この順番で実行する。
  #   1. sudoers.d にある自分用の設定 (NOPASSWD など) を削除する。
  #   2. sudo パッケージ自体を削除する。
  #      root にパスワードが無いと削除を拒否されるので SUDO_FORCE_REMOVE=yes を付ける。
  #   3. sudo / wheel グループに入っていれば外す。
  #      sudo の権限がグループ経由の場合、先に外すと 2 の sudo が通らなくなる。
  #      また sudo を消した後は gpasswd を実行できないので、root のシェルの中で最後に行う。
  #   (実行中の sudo プロセスはメモリ上にあるので、sudo を消しても最後まで動く。)
  user="$(whoami)"
  if ! sudo sh -c '
    user="$1"
    rm -f "/etc/sudoers.d/$user" || exit 1
    DEBIAN_FRONTEND=noninteractive SUDO_FORCE_REMOVE=yes apt-get purge -y sudo || exit 1
    for group in sudo wheel; do
      if id -nG "$user" | tr " " "\n" | grep -qx "$group"; then
        gpasswd -d "$user" "$group" || exit 1
      fi
    done
  ' sh "$user"; then
    log "sudo の削除に失敗しました。"
    exit 1
  fi

  if [[ "$status" -eq 0 ]]; then
    log "セットアップ完了！ 🎉 このコンテナには sudo がありません。"
  else
    # 失敗しても sudo は消す方針なので、やり直しはコンテナの作り直しになる。
    fail_notice "$status"
  fi
  exit "$status"
}
trap remove_sudo EXIT

# -------------------------------------------------------------
# 名前付きボリュームの所有者を自分にする
#   名前付きボリュームは root 所有で作られることがあり、
#   そのままだと Claude Code や gh がログイン情報を、Gradle がキャッシュを書き込めない。
#   ※ Claude Code が ~/.claude を使う前に済ませるため、インストールより先に実行する。
# -------------------------------------------------------------
for dir in "${CLAUDE_CONFIG_DIR:-}" "$HOME/.config/gh" "$HOME/.gradle"; do
  [[ -n "$dir" ]] || continue
  sudo mkdir -p "$dir"
  # Gradle のボリュームは大きく育つので、所有者が既に自分なら再帰的な chown を飛ばす。
  if [[ "$(stat -c %u:%g "$dir")" == "$(id -u):$(id -g)" ]]; then
    log "$dir の所有者は既に $(whoami) です。変更を飛ばします。"
    continue
  fi
  log "$dir の所有者を $(whoami) に変更します。"
  sudo chown -R "$(id -u):$(id -g)" "$dir"
done

# -------------------------------------------------------------
# Claude Code のインストール (公式ネイティブインストーラ)
#   ~/.local/bin/claude に入り、以後は自動アップデートされる。
#   ※ sudo 不要なので、sudo を無効化した後でも更新できる。
#   ※ ~/.local/bin の PATH は Dockerfile の ENV で通している。
# -------------------------------------------------------------
log "Claude Code をインストールします。"
curl -fsSL https://claude.ai/install.sh | bash
log "インストールが完了しました。バージョン: $(claude --version)"

# -------------------------------------------------------------
# GitHub CLI (gh)
#   ログイン済み (ボリュームに認証情報が残っている) なら、
#   git の push / pull でも gh の認証を使えるように設定する。
# -------------------------------------------------------------
if gh auth status >/dev/null 2>&1; then
  log "gh はログイン済みです。git の認証にも gh を使うように設定します。"
  gh auth setup-git
else
  log "gh は未ログインです。'gh auth login' を実行してください。"
fi

# -------------------------------------------------------------
# git worktree を相対パスにする
#   ホストとコンテナで .git を共有していて、リポジトリのパスが違う。
#   worktree のパスを相対パスにすると、どちらからも使える。
#   設定は .git/config に入るので、ホストの git (2.48 以上) にも効く。
#   wtp と Claude Code はどちらも git worktree add を使うので、この設定に従う。
#   ※ 最初の相対パスの worktree を作ると、git が extensions.relativeWorktrees を書き、
#     core.repositoryformatversion を 1 にする。git 2.48 以上が必要になる。
# -------------------------------------------------------------
log "git worktree を相対パスにする設定をします。"
# なくても困らない設定なので、失敗してもセットアップ全体は止めない。
# スクリプトのあるディレクトリはリポジトリの中なので、git がリポジトリを見つける。
if ! git -C "$(dirname -- "${BASH_SOURCE[0]}")" config worktree.useRelativePaths true; then
  log "git worktree を相対パスにする設定ができませんでした。README の手順で、手動で設定してください。"
fi
