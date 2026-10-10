# Ubuntu - DevContainer公式イメージ。
# * GitHub | devcontainer/cpp : https://github.com/devcontainers/images/tree/main/src/base-ubuntu
FROM mcr.microsoft.com/devcontainers/base:ubuntu

# RUN のシェルを bash + pipefail にする。
#   `curl ... | gpg ...` のようなパイプの途中で失敗しても、ビルドが確実に止まるようにする。
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# aptリポジトリの追加と、パッケージのインストール
#   apt-get update を1回で済ませるため、リポジトリを全部登録してからまとめてインストールする。
#   ※ 鍵の取得に使う curl / gpg はベースイメージに入っている。
#   ※ ビルドのたびに中身が変わらないよう apt-get upgrade はしない (ベースイメージの更新に任せる)。
#
# 最新のgit (git-core公式PPA)
# * Launchpad | git-core/ppa : https://launchpad.net/~git-core/+archive/ubuntu/ppa
#   PPA は新旧2つの鍵で署名されているので、両方を keyring に入れる。
#   (add-apt-repository は Python ごと入って重いので使わない。)
#
# GitHub CLI (gh) 公式aptリポジトリ
# * GitHub | cli/cli : https://github.com/cli/cli/blob/trunk/docs/install_linux.md
ARG GIT_CORE_PPA_KEYS="E1DD270288B4E6030699E45FA1715D88E1DF1F24 F911AB184317630C59970973E363C90F8F1B6217"
RUN export DEBIAN_FRONTEND=noninteractive \
    && . /etc/os-release \
    && mkdir -p -m 755 /etc/apt/keyrings \
    && for fp in ${GIT_CORE_PPA_KEYS}; do \
        curl -fsSL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x${fp}" | gpg --dearmor \
            || { echo "ERROR: 鍵 ${fp} を keyserver から取得できませんでした。" >&2; exit 1; }; \
    done > /etc/apt/keyrings/git-core-ppa.gpg \
    && for fp in ${GIT_CORE_PPA_KEYS}; do \
        gpg --show-keys --with-colons /etc/apt/keyrings/git-core-ppa.gpg | grep -q "^fpr:.*:${fp}:\$" \
            || { echo "ERROR: 取得した keyring にフィンガープリント ${fp} の鍵が入っていません。" >&2; exit 1; }; \
    done \
    && echo "deb [signed-by=/etc/apt/keyrings/git-core-ppa.gpg] https://ppa.launchpadcontent.net/git-core/ppa/ubuntu ${VERSION_CODENAME} main" \
        > /etc/apt/sources.list.d/git-core-ppa.list \
    && curl -fsSL -o /etc/apt/keyrings/githubcli-archive-keyring.gpg https://cli.github.com/packages/githubcli-archive-keyring.gpg \
    && chmod go+r /etc/apt/keyrings/git-core-ppa.gpg /etc/apt/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update \
    && apt-get -y install --no-install-recommends \
        wget \
        curl \
        ca-certificates \
        gnupg \
        tmux \
        git \
        gh \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# wtp (Git worktree管理ツール) GitHub Releasesのバイナリ (バージョン固定)
# * GitHub | satococoa/wtp : https://github.com/satococoa/wtp
# * Releases : https://github.com/satococoa/wtp/releases
ARG WTP_VERSION=2.10.3
RUN case "$(dpkg --print-architecture)" in \
        amd64) WTP_ARCH="x86_64" ;; \
        arm64) WTP_ARCH="arm64" ;; \
        *) echo "unsupported architecture" && exit 1 ;; \
    esac \
    && WTP_FILE="wtp_${WTP_VERSION}_Linux_${WTP_ARCH}.tar.gz" \
    && WTP_URL="https://github.com/satococoa/wtp/releases/download/v${WTP_VERSION}" \
    && curl -fsSL -o "/tmp/${WTP_FILE}" "${WTP_URL}/${WTP_FILE}" \
    && curl -fsSL -o /tmp/wtp_checksums.txt "${WTP_URL}/checksums.txt" \
    && (cd /tmp && grep " ${WTP_FILE}\$" wtp_checksums.txt | sha256sum -c -) \
    && mkdir -p /tmp/wtp \
    && tar xzf "/tmp/${WTP_FILE}" -C /tmp/wtp \
    && WTP_BIN="$(find /tmp/wtp -type f -name wtp | head -n 1)" \
    && test -n "${WTP_BIN}" \
    && install -m 755 "${WTP_BIN}" /usr/local/bin/wtp \
    && rm -rf "/tmp/${WTP_FILE}" /tmp/wtp_checksums.txt /tmp/wtp \
    && wtp --version

# wtpのシェル連携 (タブ補完・wtp cd) を vscode ユーザーの bash / zsh に設定する。
RUN echo 'eval "$(wtp shell-init bash)"' >> /home/vscode/.bashrc \
    && echo 'eval "$(wtp shell-init zsh)"' >> /home/vscode/.zshrc

# tmuxの設定 (マウス操作を有効化) を vscode ユーザーに配置する。
RUN echo 'set -g mouse on' > /home/vscode/.tmux.conf \
    && chown vscode:vscode /home/vscode/.tmux.conf

# Claude Code 用の準備 (本体は postCreateCommand でインストール)
# * ~/.claude : ログイン情報や設定の保存先。ボリュームをマウントしたとき vscode ユーザーの持ち物になるよう先に作成する。
# * ~/.local/bin : ネイティブインストーラーが claude コマンドを置く場所なので PATH に追加する。
RUN mkdir -p /home/vscode/.claude /home/vscode/.local/bin \
    && chown -R vscode:vscode /home/vscode/.claude /home/vscode/.local
ENV PATH="/home/vscode/.local/bin:${PATH}"

# GitHub CLI (gh) の認証情報の保存先
# * ~/.config/gh/hosts.yml にトークンが保存される。
# * ボリュームをマウントしたとき vscode ユーザーの持ち物になるよう先に作成する。
RUN mkdir -p /home/vscode/.config/gh \
    && chown -R vscode:vscode /home/vscode/.config
