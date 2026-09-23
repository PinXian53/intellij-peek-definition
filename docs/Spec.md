# IntelliJ Peek Definition Plugin — 規格書

| 項目 | 內容 |
| --- | --- |
| 狀態 | Draft v3 |
| Plugin ID | `com.pino.intellij-peek-definition` |
| Base package | `com.pino.peekdefinition`（Java package 不可含 `-`） |
| 實作語言 | Java 21 |
| 目標平台 | IntelliJ IDEA Community / Ultimate 2024.2+（since-build `242`，不設 until-build） |
| 建置工具 | Gradle + IntelliJ Platform Gradle Plugin 2.x |
| 支援語言 | MVP：Java |
| 前置依賴 | `com.intellij.modules.platform`、`com.intellij.java` |
| 行為參考 | [Visual Studio — Peek Definition (Alt+F12)](https://learn.microsoft.com/en-us/visualstudio/ide/how-to-view-and-edit-code-by-using-peek-definition-alt-plus-f12?view=visualstudio) |

---

## 1. 目的

提供類似 Visual Studio **Peek Definition** 的 IntelliJ IDEA 閱讀體驗：
游標放在 symbol 上執行 Peek 後，Definition 直接**嵌入目前 Editor 的下一行**，使用者不離開目前檔案即可閱讀實作。

> **核心理念：Definition 是「嵌入 Editor 的可互動 Code View」，而不是 Popup。**

### 1.1 與 IntelliJ 既有功能的差異

| 功能 | 行為 | 與本 Plugin 差異 |
| --- | --- | --- |
| Go to Declaration（Ctrl/⌘+B） | 跳到定義所在檔案 | 會離開目前位置 |
| Quick Definition（Ctrl+Shift+I / ⌥Space） | 浮動 Popup 內嵌 Editor | Popup 會遮住程式碼、失焦即關閉、無法逐層探索 |
| **Peek Definition（本 Plugin）** | 以 Block Inlay 嵌入在呼叫行下方 | 不遮擋、可持續存在、可逐層探索（Phase 2） |

### 1.2 設計原則

1. **Plugin 本身不修改原始程式碼**：開啟、切換、關閉 Peek 只影響畫面，不寫入任何 Document（使用者在 Phase 2 主動於 Peek 內編輯除外）。
2. **不重造輪子**：Resolve、Syntax highlighting、Theme、Font 全部交給 IntelliJ Platform。
3. **明確觸發**：只在使用者執行 Action 時才運作，不監聽 caret 自動觸發。
4. **失敗要安靜**：找不到 Definition 時只顯示提示，不建立空白 Inlay、不拋例外。

---

## 2. 使用情境

```java
var result = clockService.clockIn(userId);
```

游標放在 `clockIn`，執行 Peek Definition：

```text
var result = clockService.clockIn(userId);
┌─ ClockService.java ─ ClockService.clockIn(Long) ────────── [×] ┐
│ public ClockResult clockIn(Long userId) {                      │
│     var user = userService.getUser(userId);                    │
│     validate(user);                                            │
│     persist(user);                                             │
│     return success();                                          │
│ }                                                              │
└────────────────────────────────────────────────────────────────┘
return result;
```

Peek 內載入的是 `ClockService.java` **整個檔案**，已捲動到 `clockIn` 並標示其範圍；使用者可在 Peek 內上下捲動看周邊程式碼。

Phase 2：在 Peek 內對 `getUser` 再次執行 Peek，**同一個 Peek 區塊**切換內容，標題列出現 breadcrumb dots（與 VS 相同）：

```text
┌─ UserService.java ─ UserService.getUser(Long)   ● ●  [◀][▶][×] ┐
│ public User getUser(Long id) {                                 │
│     return repository.findById(id).orElseThrow(...);           │
│ }                                                              │
└────────────────────────────────────────────────────────────────┘
```

---

## 3. 名詞定義

| 名詞 | 說明 |
| --- | --- |
| Host Editor | 使用者原本正在編輯的 Editor |
| Peek Target | Resolve 後得到的 `PsiElement`（method、class、field…） |
| Peek Panel | 嵌入 Host Editor 的整個 UI 區塊（標題列 + Viewer） |
| Peek Viewer | Peek Panel 內的唯讀 `EditorEx`，負責顯示程式碼 |
| Peek Session | 一個 Host Editor 上的 Peek 狀態（目前 Target、history） |

---

## 4. 範圍

> 本節是範圍的**唯一依據**；其他章節若提到 Phase 2 功能，以本節為準。

| 功能 | MVP | Phase 2 | 不做 |
| --- | :-: | :-: | :-: |
| Java method / constructor 呼叫 → Peek | ✅ | | |
| Class / Interface / Enum / Field / Annotation 型別 | | ✅ | |
| IntelliJ 原生 resolve（含 overload） | ✅ | | |
| Block Inlay 內嵌唯讀 Editor | ✅ | | |
| Syntax highlighting / Theme / Font 跟隨 IDE | ✅ | | |
| Close（× 按鈕、Esc） | ✅ | | |
| Collapse / Expand | ✅ | | |
| Viewer 內 Ctrl/⌘+Click 開啟真正檔案 | ✅ | | |
| 內容隨原始碼修改同步更新 | ✅ | | |
| 外部 Library（attached / decompiled source） | ✅ | | |
| 在 Peek 內再次 Peek（取代內容，無 history） | ✅ | | |
| Promote to Editor（在一般 tab 開啟） | ✅ | | |
| Breadcrumb dots、Back / Forward | | ✅ | |
| 多結果清單（右側 result list）、Interface → Implementation | | ✅ | |
| Spring Bean implementation 解析 | | ✅ | |
| Peek Viewer 內可編輯（VS 行為） | | ✅ | |
| Ctrl/⌘+Click 改為開啟 Peek（選項） | | ✅ | |
| Host Editor ↔ Peek 焦點切換 Action | | ✅ | |
| 設定頁（高度、預設行為） | | ✅ | |
| Kotlin 等其他語言 | | ✅（架構先預留） | |
| 巢狀多層 Block Inlay | | | ✅ |
| Caret 移動自動 Peek | | | ✅ |
| Remote Development / Gateway 特殊支援 | | | ✅（MVP 需不 crash） |
| Debug / Git / AI 整合 | | | ✅ |
| 關閉 IDE 後保留 Peek 狀態 | | | ✅ |

---

## 5. 功能需求

每條需求附驗收條件（AC），作為測試依據。

### FR-1 觸發

- 提供 Action **Peek Definition**，Action ID：`com.pino.peekdefinition.PeekDefinition`（帶前綴，避免與其他 plugin 衝突）。
- 加入以下選單：
  - Editor 右鍵選單（`EditorPopupMenu` 的 GoTo 區段）
  - 主選單 Navigate（`GoToMenu`）
- **不綁定預設快捷鍵**（Keymap 可被使用者修改、各 OS 不同、易與其他 plugin 衝突），使用者可在 `Settings → Keymap → Peek Definition` 自行設定。
- 所有 Peek 操作都提供獨立 Action（見 FR-6 快捷鍵表），README 列出 VS 對應鍵供使用者自行綁定，並註明與 IntelliJ 預設 Keymap 的衝突（例如 `Alt+F12` = Terminal、`F8` = Step Over、`Shift+Esc` = Hide Tool Window）。
- Action 只在 Java 檔案且有 Project 時啟用（`update()` 內判斷，需快速、不做 resolve）。

AC：
- 右鍵選單與 Navigate 選單可看到 Peek Definition。
- 非 Java 檔案中 Action 為 disabled 或不顯示。

### FR-2 目標解析（Resolve）

- **必須**使用 IntelliJ 原生機制，不以 method name 字串搜尋：
  - 首選 `TargetElementUtil.findTargetElement(editor, flags)`，與 Go to Declaration 行為一致，可自然處理 overload、`new Foo()` constructor、static import 等情況。
- 取得 Target 後使用 `target.getNavigationElement()`，以便 library class 自動導向 attached source。
- 各情況處理：

| 情況 | 行為 |
| --- | --- |
| 解析到唯一 Target | 開啟 Peek |
| 解析不到 | 以 `HintManager.showErrorHint` 顯示「No definition found」，不建立 Inlay |
| 無法唯一解析（ambiguous） | MVP：同上顯示提示；Phase 2：開啟 Peek 並在右側顯示結果清單（見 §6.2） |
| Caret 已在 declaration 本身 | 顯示提示「Already at definition」，不開啟 Peek |
| Indexing 中（Dumb Mode） | 顯示「Peek Definition is not available during indexing」 |
| Lombok 等產生的 light element（沒有實體原始碼） | 視為解析不到；**不自行產生假 Definition** |

AC：
- `getUser(Long)`、`getUser(String)`、`getUser(Long, boolean)` 三個 overload 各自 Peek 到正確的 method。
- 對 `new UserService()` Peek 會顯示對應 constructor；若無明確 constructor 則顯示 class。
- Resolve 失敗時畫面上不會留下任何 Inlay，IDE 無 exception。

### FR-3 顯示內容

> **決策 D1（已確定）**：Peek Viewer 載入**目標檔案的完整 Document**，捲動至 Target 起始處並以背景色標示 Target 範圍。
>
> 依據：VS 文件 —「you can view and edit the definition and **move around inside the definition file** while keeping your place in the original code file.」

理由：
- 與 VS 行為一致。
- 直接使用真實 Document → 內容同步（FR-7）、點擊跳轉、semantic highlighting、Phase 2 編輯都「免費」得到。
- 不必處理「截斷 30 行 + Show more」的 UI。
- Method 範圍定義為包含 JavaDoc、annotation、signature、body（即 `PsiMethod` 的完整 text range），**不是** `method.getBody()`（interface / abstract method 沒有 body）。

Peek Panel 高度：
- 預設為「Target 行數」與「15 行」取較小者，最少 5 行；超出部分在 Viewer 內捲動。
- Phase 2 可在設定頁調整最大高度，或拖曳調整。

標題列顯示：`檔名 ─ 類別.方法(參數型別)`，以及 Collapse / Close 按鈕。

AC：
- 300 行的 method 不會把 Host Editor 撐開超過設定高度。
- Interface method（無 body）可以正常顯示其宣告。

### FR-4 內嵌顯示

- **不使用** `JBPopup` / `JBPopupFactory` 作為主要 UI（會遮擋程式碼、無法持續存在）。
  - 例外：錯誤提示（`HintManager`）與 Phase 2 的候選選擇清單可使用 popup。
- Peek Panel 以 **Block Inlay** 放在 caret 所在行的**下方**。
- 一個 Host Editor 同時只存在一個 Peek Panel；在其他位置再次 Peek 時，關閉舊的並在新位置開啟。
- 同一檔案在 split 出來的多個 Editor 中，各自獨立（Inlay 屬於 Editor，不屬於 Document）。

AC：
- 開啟 Peek 前後 Host Editor 的捲動位置不跳動（Peek 所在行保持在畫面中）。
- Host Editor 的 Document 內容與 modification stamp 不變。

### FR-5 Syntax Highlighting、Theme、Font

- 不可用 `JLabel` / `SimpleTextAttributes` 把程式碼當純文字繪製，也**不自行實作** Java highlighting。
- Peek Viewer 為真正的 `EditorEx`，因此：
  - 語法高亮：使用 `EditorHighlighterFactory` 建立 highlighter（keyword、string、number、comment、annotation、JavaDoc 等 lexer 層級高亮）。
  - Theme：自動跟隨 `EditorColorsManager` 的 global scheme，不寫死任何顏色；切換 Theme 時即時更新。
  - Font：自動跟隨目前 scheme 的 font family / size / line spacing，不寫死 `Monaco`、`Consolas`、`Menlo`。
  - Peek Panel 背景可略為區隔（使用 scheme 中既有顏色 key，例如 `EditorColors.GUTTER_BACKGROUND`），不自訂色碼。

> ⚠️ **Semantic highlighting 風險**：method call、field、parameter、local variable 的不同顏色屬於 semantic highlighting，由 Daemon（`DaemonCodeAnalyzer`）產生並存於 Document 的 markup model。Daemon 只分析「在 FileEditor 中開啟」的檔案，因此目標檔案若未被開啟，Viewer 可能只有 lexer 層級高亮。此點列為 POC 驗證項（§10）。
>
> MVP 驗收標準：**lexer 層級高亮必須正確**；semantic highlighting 盡力而為。

AC：
- Light、Darcula、任一自訂 Theme 下顯示正常，切換 Theme 後 Peek 即時更新。
- 修改 Editor font size 後 Peek 字型同步。

### FR-6 互動

| 操作 | 行為 |
| --- | --- |
| Viewer 內捲動（滑鼠滾輪） | 捲動 Viewer；到頂 / 到底後不搶 Host Editor 的捲動 |
| 水平捲動 | Viewer 自身提供水平捲軸，長行不撐寬 Host Editor |
| Viewer 內 Ctrl/⌘+Click | 以一般方式開啟該 symbol 的檔案（行為同 Go to Declaration） |
| Viewer 內執行 Peek Definition | MVP：以新 Target 取代目前 Peek 內容；Phase 2：推入 history |
| Viewer 內移動 caret、選取與複製 | 允許；Host Editor 也可同時操作（兩者皆保持可用） |
| 從 Viewer 拖曳文字到 Host Editor | 允許（複製，不從 Viewer 刪除） |
| Viewer 內輸入文字 | MVP 不允許（唯讀）；Phase 2 見 §6.4 |
| 標題列 ×  | 關閉 Peek |
| 標題列 ▼ / ▶ | Collapse 只保留標題列 / Expand 還原 |
| 雙擊標題列 / Promote Action | 在一般 Editor tab 開啟目標檔案並定位到 Viewer 目前 caret，同時關閉 Peek |

**Action 與 VS 對照**（全部不預設快捷鍵）：

| Action | VS 預設鍵 | 階段 |
| --- | --- | --- |
| Peek Definition | `Alt+F12` | MVP |
| Close Peek | `Esc` | MVP（Esc 行為見下） |
| Promote Peek to Editor | `Ctrl+Alt+Home` | MVP |
| Peek Back / Forward | `Ctrl+Alt+-` / `Ctrl+Alt+=` | Phase 2 |
| Next / Previous Result | `F8` / `Shift+F8` | Phase 2 |
| Toggle Focus Host ↔ Peek | `Shift+Esc` | Phase 2 |

**Esc 行為**（需以 `editorActionHandler`（`EditorEscape`）實作並保留原本 handler）：

1. 焦點在 Peek Viewer：若有選取則先取消選取；否則關閉 Peek，焦點回到 Host Editor 原 caret 位置。
2. 焦點在 Host Editor：先交給原本的 Esc 行為（關閉 lookup、取消選取、移除多游標）；若原本沒有事可做且存在 Peek，則關閉 Peek。

AC：
- Peek 開啟時，Host Editor 的程式碼補全 popup 仍可用 Esc 正常關閉，且不會同時關閉 Peek。
- 在 Viewer 中按 Esc 後焦點回到 Host Editor。

### FR-7 內容同步

- 因 Viewer 使用真實 Document（D1），**目標檔案被修改時 Viewer 自動更新**，不需自行監聽 `DocumentListener` / `PsiTreeChangeListener` 重新 render。
- Target 範圍以 `RangeMarker` 追蹤，修改後標示範圍自動跟著移動。
- Host Editor 中 Peek 所在行被刪除時（Inlay 失效），關閉 Peek。
- Target 被刪除（`SmartPsiElementPointer` 取回為 null）時，標題列顯示「Definition no longer exists」，不 crash。

AC：
- 在另一個 tab 修改 `getUser` 的實作，Peek 內容即時反映。
- 刪除 Host Editor 中呼叫行後，不留下孤兒 Inlay。

---

## 6. Phase 2 功能

### 6.1 Nested Peek：單一容器 + History

> **決策 D2**：不使用多層巢狀 Block Inlay（Inlay 內再嵌 Inlay 在 layout、捲動、焦點上風險過高）。

採用**同一個 Peek Panel 切換內容**（Visual Studio 的巢狀 Peek 也是此模式）：

```text
PeekSession
 ├── history: [clockIn, getUser, findById]
 └── current index: 2
```

- 在 Viewer 內 Peek → 截斷 current 之後的 history，推入新 Target。
- 標題列顯示 **breadcrumb dots**（與 VS 相同），每個 dot 代表一層；tooltip 顯示該層的檔案路徑與 symbol（如 `UserService.getUser(Long)`），點擊可跳回該層。
- ◀ Back / ▶ Forward 按鈕；可註冊對應 Action 讓使用者自訂快捷鍵。
- History 上限預設 20 筆。
- History 中的 Target 以 `SmartPsiElementPointer` 保存。

### 6.2 多結果清單 / Interface → Implementation

VS：「a result list appears to the right of the code definition view. You can choose any result in the list to display its definition.」

- 多結果時，Peek Panel 右側顯示結果清單（檔名 + symbol），點選即切換 Viewer 內容；`Next / Previous Result` Action 可逐一切換。
- 結果來源：
  - Resolve 無法唯一決定的候選（poly-variant reference）。
  - 當 Target 為 interface / abstract method 時，標題列提供「Implementations (n)」，展開後把實作放入清單。實作以 `DefinitionsScopedSearch`（Go to Implementation 使用的機制）於背景執行緒搜尋。
- 可提供選項「預設直接 Peek 實作」（類似 Go to Implementation）。

### 6.3 Spring Bean 解析

- 例：`@Autowired UserService` 注入的實際 Bean（`@Service`、`@Component`、`@Repository`、`@Bean` 方法）。
- 僅 IntelliJ Ultimate 有 Spring plugin 模型可用，應以 optional dependency 實作，Community 版不影響其他功能。

### 6.4 其他

- Class / Field / Enum / Annotation 型別的 Target。
- 設定頁：Peek 最大高度、是否預設 Peek implementation。
- Ctrl/⌘+Click 改為開啟 Peek 的選項（VS：Open definition in peek view）。
- `Toggle Focus Host ↔ Peek` Action。

### 6.5 在 Peek 內編輯

VS：「When you start to edit inside a Peek Definition window, the file that you're modifying automatically opens as a separate tab ... You can continue to make, undo, and save changes in the Peek Definition window.」

- Viewer 改為可編輯的 Editor（同一份真實 Document，因此修改自然反映到所有開啟該檔案的 tab）。
- 開始編輯時，若目標檔案尚未開啟，於背景開啟一般 tab（不搶焦點），確保 Undo / Save / 未儲存標記行為與一般 Editor 一致。
- Library / 反編譯 / 唯讀檔案維持唯讀。

---

## 7. 特殊來源處理

| 來源 | 行為 |
| --- | --- |
| 專案原始碼 | 正常顯示 |
| Library 且有 attached source | `getNavigationElement()` 導向 source，正常顯示 |
| Library 只有 `.class` | 顯示 IntelliJ 反編譯結果（與 Go to Declaration 開啟的內容一致）；若反編譯被停用則顯示「Source not available」 |
| Lombok 產生的 method（如 `getName()`） | 若 Lombok plugin 提供可導航的 source element（例如導到 `@Getter` 欄位）則顯示該處；否則顯示 not available |
| MapStruct / OpenAPI / JPA metamodel 等 generated source | 若 generated 目錄已被標為 source root 並可 resolve，視同一般原始碼；否則顯示 not available。MVP 不做特殊處理 |

---

## 8. 非功能需求

### 8.1 效能與執行緒

- **只在 Action 觸發時執行**，不監聽 caret 移動。
- Resolve 不在 EDT 上阻塞：使用 `ReadAction.nonBlocking(...)`，完成後回到 EDT 建立 UI；超過數百毫秒時顯示可取消的進度。
- Action `update()` 不得執行 resolve 或其他耗時運算。
- Dumb Mode 下不執行 resolve（見 FR-2）。
- MVP **不做 cache**：Viewer 直接使用真實 Document，重新 Peek 的成本僅為建立一個 viewer editor。

### 8.2 PSI 參考保存

- 任何跨越一次 Action 的 PSI 參考，一律使用 `SmartPsiElementPointer`，不可直接長期保存 `PsiElement`。
- 使用前檢查取回結果是否為 null / `isValid()`。

### 8.3 Lifecycle / Dispose

- 每個 Peek Panel 對應一個 `Disposable`，parent 為 Host Editor 對應的 disposable（Editor 釋放時自動清理）。
- 以 `EditorFactory.createViewer` 建立的 Viewer **必須**透過 `EditorFactory.releaseEditor` 釋放，否則 IDE 會回報 editor leak。
- 需處理以下事件並確實清理 Inlay、Viewer、listener：
  - 使用者關閉 Peek
  - Host Editor 關閉（`EditorFactoryListener.editorReleased`）
  - Project 關閉
  - Plugin 動態卸載（dynamic plugin unload）
- 不可在 static 欄位或 Application service 持有 Project / Editor / PSI 參考。

AC：
- 開關 Peek 100 次後關閉 Project，IDE log 無 leak 相關錯誤（測試環境會檢查未釋放的 editor）。

### 8.4 相容性

- MVP 在 Remote Development（JetBrains Client）環境下不要求正常顯示，但不可拋出例外；可偵測後顯示「不支援」提示。
- 支援 soft wrap 開啟 / 關閉，以及 Host Editor 中有 folding region 的情況。

---

## 9. 技術設計

### 9.1 主要 API 對照

| 需求 | IntelliJ Platform API |
| --- | --- |
| 找出 caret 下的 Target | `TargetElementUtil.findTargetElement(editor, flags)` |
| 取得 source 位置 | `PsiElement.getNavigationElement()`、`getContainingFile()`、`getTextRange()` |
| 背景 resolve | `ReadAction.nonBlocking(...).inSmartMode(project).finishOnUiThread(...)` |
| 建立唯讀 Viewer | `EditorFactory.createViewer(document, project, EditorKind.PREVIEW)` |
| 語法高亮 | `EditorHighlighterFactory.createEditorHighlighter(project, virtualFile)` → `EditorEx.setHighlighter` |
| 在 Editor 中嵌入 Swing 元件 | 候選 A：`EditorEmbeddedComponentManager`（Jupyter / Notebook 使用）<br>候選 B：`InlayModel.addBlockElement(...)` + 自訂 `EditorCustomElementRenderer`（只能 paint，無法直接放 Swing 元件） |
| 標示 Target 範圍 | `MarkupModel.addRangeHighlighter`、`RangeMarker` |
| PSI 參考保存 | `SmartPointerManager.createSmartPsiElementPointer` |
| 錯誤提示 | `HintManager.showErrorHint` |
| Esc 處理 | `editorActionHandler` extension（action = `EditorEscape`） |
| Implementation 搜尋（Phase 2） | `DefinitionsScopedSearch` |

> ⚠️ **關鍵技術風險**：`InlayModel.addBlockElement` 的 renderer 只能 paint，不能直接放入可互動的 `EditorEx`。要把真正的 Editor 嵌進 Inlay，需使用 `EditorEmbeddedComponentManager` 或平台較新版本提供的 component inlay API。這些 API 部分位於 `impl` 套件或標示為 experimental，**必須在 POC 以選定的 since-build 驗證**，並記錄使用的 API 與版本限制。

### 9.2 Package 結構

```text
src/main/java/com/pino/peekdefinition
├── action
│   ├── PeekDefinitionAction          # 入口：取 editor/caret，交給 resolver
│   └── PeekEscapeHandler             # EditorEscape handler
├── resolve
│   ├── PeekTargetResolver            # 介面：語言無關（預留 Kotlin）
│   └── JavaPeekTargetResolver        # TargetElementUtil + navigation element
├── model
│   ├── PeekTarget                    # SmartPsiElementPointer + 顯示名稱 + range
│   └── PeekSession                   # 每個 Host Editor 一份（history 於 Phase 2）
├── ui
│   ├── PeekPanel                     # 標題列 + Viewer 的 Swing 容器
│   ├── PeekViewerFactory             # 建立 / 釋放 viewer EditorEx
│   └── PeekInlayManager              # 建立、移除、定位 Block Inlay
└── lifecycle
    └── PeekEditorFactoryListener     # Editor 釋放時清理 PeekSession
```

`PeekSession` 以 `Editor.putUserData(KEY, session)` 掛在 Host Editor 上。

### 9.3 核心流程

```text
User 執行 Peek Definition
        │
        ▼
PeekDefinitionAction ── Dumb Mode? ──▶ 顯示提示，結束
        │
        ▼  (背景 read action)
PeekTargetResolver.resolve(editor, offset)
        │
        ├─ 無結果 / 在 declaration 上 ──▶ HintManager 提示，結束
        ▼
PeekTarget（SmartPointer + range）
        │
        ▼  (EDT)
PeekSession：關閉舊 Peek（若有）
        │
        ▼
PeekViewerFactory：createViewer(真實 Document) + highlighter
        │
        ▼
PeekInlayManager：於 caret 行下方建立 Block Inlay，放入 PeekPanel
        │
        ▼
Viewer 捲動至 Target、標示範圍、Host Editor 保持位置
```

---

## 10. Proof of Concept（正式開發前必做）

只驗證一條路徑：

```text
userService.getUser(id)  →  PsiMethod  →  Block Inlay 內的真實 EditorEx
```

### 驗證清單

| # | 項目 | 通過標準 |
| --- | --- | --- |
| P1 | 在 Inlay 中嵌入可互動的 `EditorEx` | 可捲動、可選取、可取得焦點 |
| P2 | 選定嵌入 API（候選 A / B / component inlay）並確認 since-build | 記錄 API 名稱與最低版本 |
| P3 | Lexer 層級語法高亮 | 與一般 Java Editor 一致 |
| P4 | Semantic highlighting | 記錄：目標檔案開啟 / 未開啟時各自的表現 |
| P5 | Viewer 內 Ctrl/⌘+Click | 可跳轉到正確位置 |
| P6 | Host Editor 不跳頁 | 開關 Peek 前後 visible area 一致 |
| P7 | 原始 Document 未被修改 | modification stamp 不變 |
| P8 | Theme 切換 | Light ↔ Darcula 即時更新 |
| P9 | 移除 Inlay 與釋放 Viewer | 關閉 Project 無 leak 錯誤 |
| P10 | 滑鼠滾輪 | Viewer 與 Host Editor 捲動不互相干擾 |

POC 通過後才開始 MVP；若 P1/P2 不通過，需重新評估 D1 與整體 UI 方案。

---

## 11. 待決問題

| # | 問題 | 狀態 |
| --- | --- | --- |
| Q1 | 最低支援版本 | ✅ 2024.2+（242）；若 P2 選定的嵌入 API 需要更新版本則上調 |
| Q2 | Plugin 實作語言 | ✅ Java |
| Q3 | D1：顯示完整檔案 vs 僅 Target 範圍 | ✅ 完整檔案（依 VS 行為） |
| Q4 | Peek Viewer 是否可編輯 | ✅ MVP 唯讀，Phase 2 依 VS 行為支援（§6.5） |
| Q5 | Plugin ID / base package | ✅ `com.pino.intellij-peek-definition` / `com.pino.peekdefinition` |

---

## 12. 測試策略

- **Resolve 單元測試**：使用 `LightJavaCodeInsightFixtureTestCase`，以 `<caret>` 標記測試 overload、constructor、static import、interface method、library class、找不到定義等情況。
- **Lifecycle 測試**：開關 Peek、關閉 Editor、關閉 Project 後，確認 Inlay 與 Viewer 已釋放。
- **手動測試清單**：FR-1 ~ FR-7 的 AC、各 Theme、soft wrap、split editor、Dumb Mode。
- 建置使用 IntelliJ Platform Gradle Plugin 2.x，並以 `verifyPlugin` 檢查目標版本範圍內的 API 相容性（特別是 §9.1 提到的 impl / experimental API）。
