---
name: emulator-check
description: 起動済みのエミュレータにアプリを入れて、dat を読み込む画面などを実機に近い形で確かめる手順です。ユーザーが「エミュレータで確かめて」「実機で動かして」と言ったときや、画面の動き、文字コード、プロセスが作り直されたあとの復元をエミュレータで確かめたいときに使います。エミュレータの起動や設定の変更はしません。
---

# エミュレータでアプリを確かめる

起動済みのエミュレータに debug ビルドを入れ、dat を置いて、画面を操作して確かめます。JVM の単体テストでは確かめられないもの (Activity の仕組み、ファイルの選択画面、文字コード、プロセスが作り直されたあとの復元) が対象です。

## 守ること

- エミュレータの起動、停止、設定の変更はしません。つながらないときは、手順 1 のとおり止めて伝えます。
- 元に戻せない操作 (アプリのアンインストール、端末のデータの削除やリセットなど) は、依頼がなければしません。要ると思ったときは、止めて伝えます (`.claude/agents/worker.md` の決まりと同じです)。
- 一時的なファイル (dat、スクリーンショット、`uiautomator dump` の出力) は、スクラッチパッドに置きます。リポジトリの中には置きません。

## 1. つながるかを確かめる

```bash
command -v adb
adb devices
```

- `adb` がないとき、または `adb devices` の一覧に `device` の行が 1 つもないときは、設定を変えずに止め、つながらなかったと伝えます。`ADB_SERVER_SOCKET` などの環境や、`.devcontainer/` の設定は触りません。つなぎ方は `README.md` を案内します。
- 一覧に `offline` や `unauthorized` の行があるときも、同じように止めて伝えます。
- 端末が複数あるときは、`-s <シリアル>` を以降のすべての `adb` に付けます。

確かめた環境として、`adb version` と、エミュレータの Android の版 (`adb shell getprop ro.build.version.release`) を控え、報告に書きます。つながらなかったときは、つながらなかったと報告し、版は書かなくてかまいません。

## 2. ビルドして入れる

パッケージ名は、`app/build.gradle.kts` の `applicationId` で確かめます (以降 `<パッケージ>` と書きます)。

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `Success` が出ることを確かめます。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (署名の不一致) で失敗したときは、**アンインストールせずに止めて伝えます**。アンインストールすると、端末のアプリのデータが消えるためです。原因でよくあるのは、debug の署名鍵 (`~/.android/debug.keystore`) が、端末に入っているアプリを入れたときと違うことです。コンテナを作り直すと、鍵が変わることがあります。アンインストールするかは、ユーザーが決めます。
- 入った版は、`adb shell dumpsys package <パッケージ> | grep versionName` で確かめられます。

## 3. dat を作る

dat は `windows-31j` (CP932) の文字コードで、1 行が 1 件のレスです。形は次のとおりです。

```text
名前<>メール<>日付と ID<>本文<>タイトル
```

- 5 番目のタイトルは、1 行目にだけ書きます。2 行目以降は、5 番目を空にします (`名前<>メール<>日付と ID<>本文<>`)。
- 本文の改行は `<br>` で書きます。
- 拡張文字の確認用に、本文に `①` (CP932 で `87 40`) と `髙` (CP932 で `fb fc`) を入れます。

手順は次のとおりです。

1. スクラッチパッドに、UTF-8 の dat を Write ツールで作ります。
2. CP932 に変換します。

   ```bash
   iconv -f UTF-8 -t CP932 <スクラッチパッド>/sample-utf8.dat > <スクラッチパッド>/sample.dat
   ```

3. 拡張文字のバイト列を確かめます。変換前のファイルの `①` と `髙` が、変換後に `87 40` と `fb fc` になっていれば、正しく変換できています。

   ```bash
   printf '①髙' | iconv -f UTF-8 -t CP932 | od -An -t x1
   for b in '87 40' 'fb fc'; do
     od -An -v -t x1 <スクラッチパッド>/sample.dat | tr '\n' ' ' | tr -s ' ' | grep -q " $b " && echo "$b あり" || echo "$b なし"
   done
   ```

   上のコードブロックの 1 つ目のコマンド (`iconv` と `od`) は `87 40 fb fc` を出します。`od` は 16 バイトごとに折り返すので、ファイル全体を 1 行につなげてから探します。2 つ目のコマンド (`for` の繰り返し) は、2 つのバイト列のどちらもが `あり` になれば正しく変換できています。

   `iconv` が `cannot convert` で失敗したときは、変換できない文字が入っています。その文字を dat から外します。

## 4. 端末に置く

```bash
adb push <スクラッチパッド>/sample.dat /sdcard/Download/sample.dat
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/sample.dat
```

- 2 行目のスキャンのブロードキャストは、必須ではありません。Android 17 のエミュレータでは、送らなくても、ファイルの選択画面にファイルが出ました。まず 1 行目だけを実行し、選択画面にファイルが出なかったときに 2 行目を送ります。
- 新しい Android では、このブロードキャストが非推奨のことがあり、効かないときがあります。送っても出ないときは、選択画面を開き直してから、ユーザーに伝えます。
- 選択画面にファイルが出たかどうか (ブロードキャストを送ったかどうかも) を、エミュレータの版と一緒に報告に書きます。

## 5. 画面を操作して確かめる

画面の部品は、`uiautomator dump` で位置を調べて、`input tap` で押します。

```bash
adb shell uiautomator dump /sdcard/window_dump.xml
adb exec-out cat /sdcard/window_dump.xml > <スクラッチパッド>/window_dump.xml
adb shell input tap <x> <y>
adb exec-out screencap -p > <スクラッチパッド>/screen.png
```

- 押す場所は、`window_dump.xml` の該当する `node` の `bounds="[左,上][右,下]"` から、中心の座標を計算します。画面の表示が変わるたびに、dump を取り直します。
- 座標は、dump から次のように探せます (ボタンの文字が `dat を開く` の例)。`bounds` の真ん中を `input tap` に渡します。

  ```bash
  grep -o 'text="dat を開く"[^>]*bounds="\[[0-9,]*\]\[[0-9,]*\]"' <スクラッチパッド>/window_dump.xml
  # 例: bounds="[40,300][280,380]" なら、中心は x=(40+280)/2=160、y=(300+380)/2=340
  adb shell input tap 160 340
  ```

- 1 画面に収まらない長いスレッドは、`input swipe` でスクロールします。画面の大きさは `adb shell wm size` で確かめ、画面の幅の真ん中で、下から上へ動かします (指を上へ動かすと、内容が下へ進みます)。最後は、スクロールしても dump の内容が変わらなくなったことで分かります。

  ```bash
  adb shell wm size   # 例: Physical size: 320x640
  # x=160 で、y=500 から y=150 へ、300 ミリ秒かけて動かす
  adb shell input swipe 160 500 160 150 300
  ```
- スクリーンショットは Read ツールで見られます。ファイル名には、何の画面かが分かる名前を付けます。
- 文字は、スクリーンショットの見た目と、dump の `text` の両方で確かめます。dump の文字は、`①` や `髙` が化けていないかの確認にも使えます。
- ファイルの選択画面は、アプリの Activity ではなくシステムの画面です。dump の対象は同じですが、部品の名前は Android の版で違います。

### システムバーの分の余白を確かめるとき

最後の項目がナビゲーションバーに重ならないかを確かめるときは、先に、ナビゲーションバーの inset があるかを見ます。

```bash
adb shell dumpsys window | grep -n 'type=navigationBars'
```

- ジェスチャーのナビゲーションでは、`navigationBars` の inset が出ないか、0 のことがあり、重なりを確かめられないことがあります (方式は `adb shell settings get secure navigation_mode` で分かります。2 がジェスチャーです)。そのときは、確かめられなかったと報告します。エミュレータの設定 (ナビゲーションの方式) は変えません。
- 下端を確かめるには、1 画面に収まらない長い dat が要ります。手順 3 の形で、レスを十分な数まで増やして作り、上の `input swipe` で最後までスクロールします。

## 6. プロセスが作り直されたあとに戻るかを確かめる

画面の状態 (`rememberSaveable` や ViewModel の状態) が、プロセスが殺されたあとに戻るかを確かめます。

1. アプリで確かめたい画面 (例: dat を読んだスレッドの画面) を開きます。
2. ホームボタンで裏に回します。

   ```bash
   adb shell input keyevent KEYCODE_HOME
   ```

3. 5 秒待ちます。すぐに `am kill` すると、プロセスが残ることがあります。
4. プロセスを殺し、消えたことを確かめます。

   ```bash
   adb shell am kill <パッケージ>
   adb shell pidof <パッケージ>
   ```

   `pidof` が何も出さなければ、消えています。出たときは、3 から繰り返します。
5. ランチャーのアイコンからではなく、最近使ったアプリから戻します。ランチャーから起動すると、新しい起動になり、保存した状態を使わないためです。

   ```bash
   adb shell input keyevent KEYCODE_APP_SWITCH
   ```

   最近使ったアプリの一覧が出たら、`uiautomator dump` で、アプリのカード (`snapshot` を含む node) の `bounds` を探し、その真ん中を `input tap` で押すと、アプリに戻ります。
6. 3 秒待ってから、戻した画面を `uiautomator dump` の `text` で確かめます。

戻したあとのスクリーンショットは、黒一色になることがあり、Android 17 のエミュレータ (手順 7 の確かめた環境) で試したときは 2 回とも、5 秒空けても黒一色でした。戻したあとは、スクリーンショットではなく `uiautomator dump` の `text` で内容を確かめ、見た目は確かめていないと報告します。

## 7. 報告する

- 確かめた版: `adb version` と、Android の版。つながらなかったときは、つながらなかったこと。このスキルの手順を確かめた版は、adb 37.0.1 と Android 17 のエミュレータ (画面は 320x640、密度 160、ジェスチャーのナビゲーション) です。
- 入れた APK と、確かめたパッケージ。
- 確かめた操作と、見たもの (スクリーンショットの場所、dump の `text`)。
- 確かめていないこと (戻したあとの見た目、システムバーの分の余白、スキャンのブロードキャストの効き目など)。
- 端末に置いたファイル。次のものは、依頼がなければ消さずに残し、すべて報告します。
  - `/sdcard/Download/` に置いた dat。
  - `/sdcard/window_dump.xml`。
  - 入れた debug のアプリ。
