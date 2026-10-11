# Ubuntu - DevContainer公式イメージ。
# * GitHub | devcontainer/cpp : https://github.com/devcontainers/images/tree/main/src/base-ubuntu
FROM mcr.microsoft.com/devcontainers/base:ubuntu

# RUN のシェルを bash + pipefail にする。
#   `curl ... | gpg ...` のようなパイプの途中で失敗しても、ビルドが確実に止まるようにする。
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# コンテナ全体のロケールを UTF-8 にする。
#   未設定 (POSIX) だと tmux が UTF-8 非対応と判断し、2 バイト文字を `_` に置き換えて表示する。
#   C.UTF-8 はベースイメージに入っているので、追加パッケージは不要である。
ENV LANG=C.UTF-8

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
        zip \
        unzip \
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

# 以降の Java / Android SDK / Gradle は、vscode ユーザーで導入する。
#   導入先がすべて vscode ユーザーのホーム配下なので、sudo が無くても後から更新できる。
#   ※ 最後に USER root へ戻し、ベースイメージの既定 (root) を変えないようにする。
USER vscode

# Java (SDKMAN で導入・バージョン管理)
# * SDKMAN : https://sdkman.io/
# * Android SDK の Linux 版 (adb、aapt2 など) は x86_64 のみ提供されているため、amd64 だけを対象にする。
#   バージョンは versions.properties の JDK_SDKMAN_ID (Temurin の識別子) で固定する。変えるときは、`sdk list java` で識別子を確認して書き換える。
# * SDKMAN のインストーラは curl | bash で、バージョンの固定とチェックサムの検証ができない。
#   JDK 本体の取得と検証は SDKMAN に任せる。
# * sdk コマンドは対話シェルでだけ有効になる。非対話シェル (Claude Code など) でも java を使えるよう、
#   current のシンボリックリンクを JAVA_HOME と PATH に通す。
# * ci=true で入れると、設定が非対話向け (確認なしで既定の JDK を切り替える等) になる。
#   JDK の導入後に、対話向けの値 (SDKMAN の通常インストールの既定値) へ戻す。
ENV SDKMAN_DIR="/home/vscode/.sdkman"
# versions.properties の読み込み
#   COPY はキャッシュのキーになるので、JDK の手順の直前に置く。
#   値は `KEY=` で始まる行から読む。値が空なら、ビルドを止める。
#   vscode ユーザーが後の手順で消せるよう、vscode ユーザーの持ち物にする。
COPY --chown=vscode:vscode versions.properties /tmp/versions.properties
RUN case "$(dpkg --print-architecture)" in \
        amd64) ;; \
        *) echo "ERROR: Android SDK の Linux 版は x86_64 のみ提供されているため、amd64 以外には対応していません。" >&2 && exit 1 ;; \
    esac \
    && JAVA_VERSION="$(grep -E '^JDK_SDKMAN_ID=' /tmp/versions.properties | head -n 1 | cut -d= -f2-)" \
    && { test -n "${JAVA_VERSION}" || { echo "ERROR: versions.properties の JDK_SDKMAN_ID が空か、見つかりません。" >&2; exit 1; }; } \
    && curl -fsSL "https://get.sdkman.io?ci=true&rcupdate=false" | bash \
    && source "${SDKMAN_DIR}/bin/sdkman-init.sh" \
    && sdk install java "${JAVA_VERSION}" \
    && sed -i \
        -e 's/^sdkman_auto_answer=.*/sdkman_auto_answer=false/' \
        -e 's/^sdkman_selfupdate_feature=.*/sdkman_selfupdate_feature=true/' \
        -e 's/^sdkman_colour_enable=.*/sdkman_colour_enable=true/' \
        "${SDKMAN_DIR}/etc/config" \
    && grep -qx 'sdkman_auto_answer=false' "${SDKMAN_DIR}/etc/config" \
    && grep -qx 'sdkman_selfupdate_feature=true' "${SDKMAN_DIR}/etc/config" \
    && grep -qx 'sdkman_colour_enable=true' "${SDKMAN_DIR}/etc/config" \
    && rm -rf "${SDKMAN_DIR}/archives"/* "${SDKMAN_DIR}/tmp"/* \
    && "${SDKMAN_DIR}/candidates/java/current/bin/java" -version
ENV JAVA_HOME="${SDKMAN_DIR}/candidates/java/current"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# SDKMAN のシェル連携 (sdk コマンド・タブ補完) を vscode ユーザーの bash / zsh に設定する。
RUN echo 'source "$HOME/.sdkman/bin/sdkman-init.sh"' >> /home/vscode/.bashrc \
    && echo 'source "$HOME/.sdkman/bin/sdkman-init.sh"' >> /home/vscode/.zshrc

# Android SDK (command-line tools、platform-tools、platform、build-tools)
# * Android Studio : https://developer.android.com/studio#command-line-tools-only
# * バージョンとチェックサムは、すべて versions.properties から読む (JDK の手順で COPY 済み)。値が空なら、ビルドを止める。
#   バージョンを変えるときは、versions.properties を書き換える。
# * command-line tools は、公式ページのチェックサムで検証する。
# * ライセンスへの同意は、ビルド時に一括で行う。
# * SDK は vscode ユーザーの持ち物にして、sdkmanager で後から追加できるようにする。
ENV ANDROID_HOME="/home/vscode/android-sdk"
RUN read_version() { \
        local value; \
        value="$(grep -E "^$1=" /tmp/versions.properties | head -n 1 | cut -d= -f2-)"; \
        test -n "${value}" || { echo "ERROR: versions.properties の $1 が空か、見つかりません。" >&2; return 1; }; \
        printf '%s' "${value}"; \
    } \
    && ANDROID_CMDLINE_TOOLS_VERSION="$(read_version ANDROID_CMDLINE_TOOLS_VERSION)" \
    && ANDROID_CMDLINE_TOOLS_SHA256="$(read_version ANDROID_CMDLINE_TOOLS_SHA256)" \
    && ANDROID_PLATFORM="$(read_version ANDROID_PLATFORM)" \
    && ANDROID_BUILD_TOOLS="$(read_version ANDROID_BUILD_TOOLS)" \
    && ZIP_FILE="commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
    && curl -fsSL -o "/tmp/${ZIP_FILE}" "https://dl.google.com/android/repository/${ZIP_FILE}" \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/${ZIP_FILE}" | sha256sum -c - \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" /tmp/cmdline-tools-unzip \
    && unzip -q "/tmp/${ZIP_FILE}" -d /tmp/cmdline-tools-unzip \
    && mv /tmp/cmdline-tools-unzip/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf "/tmp/${ZIP_FILE}" /tmp/cmdline-tools-unzip \
    && "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --licenses < <(yes) > /dev/null \
    && "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
    && rm -f /tmp/versions.properties
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Gradle のキャッシュの保存先
# * ~/.gradle : 依存関係やラッパーのキャッシュ。ボリュームをマウントしたとき vscode ユーザーの持ち物になるよう先に作成する。
RUN mkdir -p /home/vscode/.gradle

USER root
