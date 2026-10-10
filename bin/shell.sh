#!/usr/bin/env bash
# =============================================================
# shell.sh
#   ホストのターミナルから、起動中の devcontainer に入るスクリプト。
#   VS Code で開いたコンテナに、別のターミナルから入りたいときに使う。
#
# 使い方:
#   bin/shell.sh                 # コンテナ内の bash に入る。
#   bin/shell.sh tmux new -A     # 指定したコマンドをコンテナ内で実行する。
#
# 環境変数:
#   DEVCONTAINER_ID      コンテナ名またはIDを直接指定する (自動検出できないとき用)。
#   DEVCONTAINER_USER    入るユーザー。デフォルトは vscode
#   DEVCONTAINER_WORKDIR コンテナ内の作業ディレクトリ。デフォルトは /workspaces/<フォルダ名>
#
# ※ コンテナの作成・起動は VS Code (Reopen in Container) で行う。
#   このスクリプトは、起動済みのコンテナに入るだけ。
# ※ git worktree (wtp) の中から実行しても、メインのリポジトリのコンテナに入る。
# =============================================================

set -euo pipefail

err() { echo -e "\033[1;31m[shell]\033[0m $*" >&2; }

command -v docker >/dev/null 2>&1 || { err "docker コマンドが見つかりません。"; exit 1; }

user="${DEVCONTAINER_USER:-vscode}"

# -------------------------------------------------------------
# パスを「WSL / Linux 側から見たパス」の形にそろえる
#   devcontainer.local_folder ラベルの値は、VS Code の開き方によって表記が変わる。
#     \\wsl.localhost\Ubuntu\home\me\proj  →  /home/me/proj
#     \\wsl$\Ubuntu\home\me\proj           →  /home/me/proj
#     C:\Users\me\proj                     →  /mnt/c/Users/me/proj
#   ※ 末尾一致のような曖昧な比較はしない (パスが似た別プロジェクトを拾わないため)。
# -------------------------------------------------------------
normalize_path() {
  local p="${1//\\//}"
  p="${p%/}"
  local wsl_re='^//wsl(\.localhost|[$])/[^/]+(/.*)?$'
  local drive_re='^([A-Za-z]):(/.*)?$'
  if [[ "$p" =~ $wsl_re ]]; then
    p="${BASH_REMATCH[2]:-/}"
  elif [[ "$p" =~ $drive_re ]]; then
    p="/mnt/$(echo "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')${BASH_REMATCH[2]}"
  fi
  echo "$p"
}

# -------------------------------------------------------------
# メインのリポジトリのルートを求める
#   worktree の中で実行された場合、worktree 自身ではなく
#   メインのリポジトリ (= devcontainer を開いたフォルダ) を指すようにする。
#   git が使えない・想定外の構成のときは、このスクリプトの場所から求める。
# -------------------------------------------------------------
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_logical="$(cd "$script_dir/.." && pwd)"
if common_dir="$(git -C "$script_dir" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" \
   && [[ "$(basename "$common_dir")" == ".git" ]]; then
  root_logical="$(dirname "$common_dir")"
fi
# シンボリックリンクを解決した形と、解決しない形の両方で照合する。
root_physical="$(cd "$root_logical" && pwd -P)"

# -------------------------------------------------------------
# 入るコンテナを決める
#   devcontainer が付けるラベル devcontainer.local_folder (= ホスト側のフォルダ) で探す。
# -------------------------------------------------------------
container="${DEVCONTAINER_ID:-}"
if [[ -z "$container" ]]; then
  found=()
  labels=()
  while IFS=$'\t' read -r id label; do
    [[ -n "$id" && -n "$label" ]] || continue
    labels+=("${id}  ${label}")
    normalized="$(normalize_path "$label")"
    if [[ "$normalized" == "$root_logical" || "$normalized" == "$root_physical" ]]; then
      found+=("$id")
    fi
  done < <(docker ps --filter "label=devcontainer.local_folder" \
             --format '{{.ID}}{{"\t"}}{{.Label "devcontainer.local_folder"}}')

  case "${#found[@]}" in
    0)
      err "起動中の devcontainer が見つかりません: ${root_logical}"
      if [[ "${#labels[@]}" -gt 0 ]]; then
        err "起動中の devcontainer (ID  local_folder):"
        printf '  %s\n' "${labels[@]}" >&2
      else
        err "VS Code でこのフォルダを開いて 'Reopen in Container' してください。"
      fi
      err "DEVCONTAINER_ID=<コンテナ名またはID> で直接指定もできます。"
      exit 1
      ;;
    1)
      container="${found[0]}"
      ;;
    *)
      err "該当するコンテナが複数あります。DEVCONTAINER_ID で指定してください:"
      printf '  %s\n' "${found[@]}" >&2
      exit 1
      ;;
  esac
fi

# -------------------------------------------------------------
# コンテナ内の作業ディレクトリを決める
#   devcontainer のデフォルトは /workspaces/<開いたフォルダ名>
#   フォルダ名は、コンテナのラベルから取る (worktree や DEVCONTAINER_ID 指定でも正しくなる)。
# -------------------------------------------------------------
workdir="${DEVCONTAINER_WORKDIR:-}"
if [[ -z "$workdir" ]]; then
  local_folder="$(docker inspect --format '{{ index .Config.Labels "devcontainer.local_folder" }}' "$container" 2>/dev/null || true)"
  if [[ -n "$local_folder" ]]; then
    folder_name="$(basename "$(normalize_path "$local_folder")")"
  else
    folder_name="$(basename "$root_logical")"
  fi
  workdir="/workspaces/${folder_name}"
fi

# 引数がなければ bash (ログインシェル) を起動する。あればそのコマンドを実行する。
if [[ $# -eq 0 ]]; then
  set -- bash -l
fi

# 標準入力と標準出力が端末のときだけ -t を付ける (パイプ経由でも使えるように)。
#   ※ 空の配列を展開すると古い bash (3.2 など) で set -u に引っかかるので、
#      必ず中身がある配列 (-i 入り) にしてから使う。
exec_args=(-i)
if [[ -t 0 && -t 1 ]]; then
  exec_args+=(-t)
fi
exec_args+=(--user "$user" --workdir "$workdir")

# CLAUDE_CONFIG_DIR などコンテナの環境変数は docker exec でも引き継がれる。
exec docker exec "${exec_args[@]}" "$container" "$@"
