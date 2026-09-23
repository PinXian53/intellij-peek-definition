# IntelliJ Peek Definition Plugin

## 1. 目的

提供類似 Visual Studio **Peek Definition** 的 IntelliJ IDEA 編輯體驗。

使用者將游標放在 Java method、field、class 等 symbol 上，執行 Peek Definition 後：

* 不離開目前檔案
* 不開啟新的 Editor Tab
* 不使用一般 Popup 顯示
* 直接在目前 Editor 中嵌入 Definition
* 使用 IntelliJ 原生程式碼語法高亮
* 可以繼續對 Definition 內的 symbol 執行 Peek
* 可以返回上一層 Definition
* 不修改原始程式碼

核心目標：

> **讓開發者閱讀程式碼時，可以像 Visual Studio Peek Definition 一樣，在目前 Editor 中逐層探索 method implementation。**

---

# 2. 使用情境

原始程式碼：

```java
var result = clockService.clockIn(userId);
```

游標放在：

```text
clockIn()
```

執行 Peek Definition 後：

```text
var result = clockService.clockIn(userId);

┌──────────────────────────────────────────────┐
│ ClockService                                 │
│                                              │
│ public ClockResult clockIn(Long userId) {   │
│     var user = userService.getUser(userId); │
│     validate(user);                          │
│     persist(user);                           │
│     return success();                        │
│ }                                            │
└──────────────────────────────────────────────┘
```

Definition 內又有：

```java
userService.getUser(userId);
```

使用者可以再次執行 Peek：

```text
原始 Editor
    │
    └── Peek clockIn()
            │
            └── Peek getUser()
                    │
                    └── Peek repository.findById()
```

形成逐層閱讀程式碼的能力。

---

# 3. 功能需求

## 3.1 Peek Definition

### 必要功能

支援以下 Java PSI element：

* Method
* Constructor
* Class
* Interface
* Field
* Property（可視需要支援）
* Enum
* Annotation

第一階段以 **Method** 為主要目標。

### 行為

使用者：

```java
service.getUser(id);
```

將 caret 放在：

```text
getUser
```

執行 Peek Definition。

Plugin 應找到對應的：

```text
PsiMethod
```

並顯示其 implementation。

---

# 4. Editor 內嵌

## 4.1 不使用一般 Popup

不要以：

```text
JBPopup
JBPopupFactory
```

作為主要 UI。

原因：

* 與 Visual Studio Peek Definition 體驗不同
* Popup 容易遮住原始程式碼
* 無法自然融入 Editor
* 無法形成巢狀 Peek

---

## 4.2 使用 IntelliJ Editor Inlay

優先研究：

```text
Editor
 └── InlayModel
      └── BlockInlay
```

概念：

```text
原始程式碼

line 10: service.getUser(id);

          ↓ Peek

line 10: service.getUser(id);

         ┌──────────────────────────────┐
line 11: │ public User getUser(...) {   │
line 12: │     ...                       │
line 13: │ }                             │
         └──────────────────────────────┘

line 14: return result;
```

使用：

```java
Editor.getInlayModel()
```

建立 Block Inlay。

優先研究 API：

```java
InlayModel.addBlockElement(...)
```

---

# 5. Syntax Highlighting

這是 Plugin 的核心需求之一。

不能單純使用：

```java
JLabel
```

或：

```java
SimpleTextAttributes
```

把整個 method body 當成純文字。

否則會變成：

```text
public User getUser(Long id) {
    var user = repository.findById(id);
    return user;
}
```

全部使用相同顏色。

---

## 5.1 目標

Definition 必須盡可能使用 IntelliJ 原生 Java Editor rendering：

* Keyword
* Class
* Interface
* Method
* Variable
* String
* Number
* Comment
* Annotation
* Generic
* Operator
* Parameter
* JavaDoc

例如：

```java
public User getUser(Long id) {
    var user = repository.findById(id)
            .orElseThrow(() -> new NotFoundException(id));

    return user;
}
```

應與正常 IntelliJ Java Editor 的 syntax highlighting 一致。

---

# 6. Rendering 實作方向

優先研究 IntelliJ Platform 是否可以：

```text
PsiFile / PsiMethod
        ↓
Document / Editor
        ↓
EditorEx / EditorFactory
        ↓
Block Inlay
```

也就是在 Block Inlay 裡建立一個受 IntelliJ Editor rendering 控制的 code view。

不要自己重新實作 Java syntax highlighting。

---

# 7. Definition 來源

## 7.1 PSI

主要使用：

```java
PsiElement
PsiMethod
PsiClass
PsiFile
```

method：

```java
PsiMethod method
```

取得：

```java
PsiCodeBlock body = method.getBody();
```

---

# 8. Method Resolution

這部分要特別注意。

以下程式碼：

```java
service.getUser(id);
```

不能只透過 method name：

```text
getUser
```

搜尋。

必須讓 IntelliJ PSI / Resolve 機制負責：

```text
method call
    ↓
PsiReference
    ↓
resolve()
    ↓
PsiMethod
```

避免 overload resolution 錯誤。

例如：

```java
getUser(Long id)
getUser(String id)
getUser(Long id, boolean cache)
```

應使用 IntelliJ 原生 resolve 結果。

---

# 9. Interface / Implementation

例如：

```java
UserService service;

service.getUser(id);
```

Definition 可能是：

```java
public interface UserService {
    User getUser(Long id);
}
```

但使用者真正想看的可能是：

```java
@Service
public class UserServiceImpl implements UserService {

    @Override
    public User getUser(Long id) {
        ...
    }
}
```

因此建議分成兩階段。

### MVP

顯示 IntelliJ resolve 到的 Definition。

### Phase 2

增加：

```text
Interface
    ↓
Implementation
```

選擇機制。

可以參考 IntelliJ：

```text
Go to Implementation
```

的既有 PSI / navigation 機制。

---

# 10. Nested Peek

Definition 裡面：

```java
public User getUser(Long id) {
    return repository.findById(id)
            .orElseThrow(...);
}
```

使用者可以對：

```java
findById()
```

再次 Peek。

形成：

```text
Root Editor
    │
    └── Peek #1
          │
          └── Peek #2
                │
                └── Peek #3
```

---

# 11. Navigation History

需要保存 Peek navigation history。

例如：

```text
ClockService.clockIn()
        ↓
UserService.getUser()
        ↓
UserRepository.findById()
```

上方可以顯示：

```text
ClockService.clockIn
 > UserService.getUser
   > UserRepository.findById
```

使用者可以：

* Back
* Forward
* 回到上一層 Peek

---

# 12. Close / Collapse

每一個 Peek block 應該可以：

```text
Expand
Collapse
Close
```

建議 UI：

```text
▼ ClockService.clockIn()
```

收合後：

```text
▶ ClockService.clockIn()
```

---

# 13. Shortcut

建議第一階段提供 Action：

```text
Peek Definition
```

不要一開始強制綁定固定快捷鍵。

原因：

* IntelliJ Keymap 可被使用者修改
* 不同 OS 預設快捷鍵不同
* 避免與 IntelliJ / IDE Plugin 衝突

建議：

```text
Action ID:
peekDefinition
```

使用者自行設定：

```text
Settings
→ Keymap
→ Peek Definition
```

---

# 14. Context Menu

可以加入：

```text
Right Click
    → Peek Definition
```

以及：

```text
Navigate
    → Peek Definition
```

---

# 15. Esc 行為

建議：

```text
Esc
```

關閉目前 Peek。

如果有 nested Peek：

```text
Esc
```

優先關閉最內層。

---

# 16. Method Body 長度

不能無限制顯示大型 method。

例如：

```java
public void execute() {
    // 300 lines
}
```

建議：

```text
Default maximum lines = 30
```

超過：

```text
...
Show more (270 lines)
```

使用者點擊後再展開。

---

# 17. Performance

這是非常重要的實作注意事項。

不要每次 caret 移動都：

```text
重新建立 Editor
重新 parse PSI
重新 render
```

否則大型 Java Project 會非常慢。

---

## 17.1 建議觸發時機

不要：

```text
Caret moved
    ↓
立即 Peek
```

而是：

```text
使用者明確執行 Peek Action
    ↓
Resolve PSI
    ↓
建立 Inlay
```

---

## 17.2 Cache

可以考慮 cache：

```text
PsiMethod
+
Document
+
Rendering state
```

但必須注意 PSI invalidation。

不要長期保存：

```java
PsiElement
```

而沒有檢查：

```java
PsiElement.isValid()
```

---

# 18. Code Modification

使用者修改原始程式碼後：

```java
service.getUser(id);
```

或 Definition 本身被修改：

```java
public User getUser(...) {
    ...
}
```

Peek 內容必須更新。

需要研究：

```text
DocumentListener
PsiTreeChangeListener
```

或 IntelliJ Inlay / Editor lifecycle。

---

# 19. Dispose / Lifecycle

Plugin 關閉、Editor 關閉、Project 關閉時：

```text
Inlay
Editor
Disposable
```

都必須正確 dispose。

避免：

* Memory leak
* stale editor
* stale PSI
* listener 沒有解除
* Project close 後仍持有 reference

---

# 20. Theme

不能自己寫死：

```text
black
white
blue
```

應使用 IntelliJ：

```text
EditorColorsScheme
EditorColorsManager
TextAttributesKey
```

確保：

* Light Theme
* Dark Theme
* Custom Theme

都可以正常顯示。

---

# 21. Font

Definition code 應跟目前 Editor：

```text
font family
font size
line spacing
```

保持一致。

不要自己指定：

```text
Monaco
Consolas
Menlo
```

應優先取得目前 Editor 的 font configuration。

---

# 22. Scroll

Peek 區塊可能比目前 Editor 寬。

需要考慮：

```text
Horizontal scroll
Vertical scroll
```

尤其：

```java
someVeryLongMethodName(...)
```

或：

```java
VeryLongGenericType<A, B, C, D>
```

---

# 23. Nested Inlay 的風險

Nested Peek 是本 Plugin 最值得注意的 UI 問題。

例如：

```text
Root Editor

service.a()
┌───────────────────────────────┐
│ service.b()                   │
│ ┌───────────────────────────┐ │
│ │ service.c()               │ │
│ │ ┌───────────────────────┐ │ │
│ │ │ service.d()           │ │ │
│ │ └───────────────────────┘ │ │
│ └───────────────────────────┘ │
└───────────────────────────────┘
```

不建議真的無限制巢狀 Block Inlay。

建議：

```text
Maximum Peek Depth = 3
```

或採用：

```text
同一個 Peek Container
    ↓
切換目前 Peek Definition
```

避免 Editor 結構變得太複雜。

---

# 24. Spring Boot 支援

第一階段不要特別處理 Spring。

例如：

```java
userService.getUser(id);
```

先依照 IntelliJ Java PSI resolve。

Phase 2 再考慮：

```text
@Service
@Component
@Repository
@Controller
@Bean
@Autowired
```

以及：

```text
Interface → Spring implementation
```

---

# 25. Lombok

需要注意：

```java
@Getter
@Setter
@Builder
@RequiredArgsConstructor
```

這類 Lombok generated code。

例如：

```java
user.getName();
```

可能實際沒有：

```java
getName()
```

Java source method。

Plugin 不應自己猜測。

優先使用 IntelliJ PSI / Lombok plugin 提供的 PSI。

如果 resolve 不到：

```text
顯示：
Definition not available
```

不要自行產生假的 Definition。

---

# 26. Generated Code

類似問題：

* Lombok
* MapStruct
* OpenAPI Generator
* JPA metamodel
* generated source
* Kotlin generated code

第一階段：

> 能取得 PSI Definition 就顯示，否則顯示無法取得 Definition。

不要在 MVP 階段自行處理所有 generated source。

---

# 27. Binary / External Library

例如：

```java
objectMapper.writeValueAsString(object);
```

Definition 可能來自：

```text
External Library
```

如果 IntelliJ 有：

```text
decompiled source
```

或：

```text
attached source
```

可以顯示。

如果只有：

```text
.class
```

則依 IntelliJ 原生 navigation 行為處理。

---

# 28. Error Handling

Definition 找不到時：

```text
No definition found
```

不要：

* Crash
* 建立空白 Inlay
* 修改原始碼

如果 method 有多個 implementation：

```text
Multiple implementations found
```

可以提供：

```text
Select Implementation
```

---

# 29. MVP Scope

第一版建議只做：

### 支援

* Java
* Method call
* Constructor
* Method definition
* IntelliJ PSI resolve
* Editor Block Inlay
* Java syntax highlighting
* Expand / Collapse
* Close
* Esc
* 不修改原始碼
* Light / Dark theme
* 基本 lifecycle handling

### 暫不支援

* Spring implementation resolution
* Lombok 特殊處理
* 多語言
* 無限制 nested Peek
* Remote development 特殊處理
* Debug integration
* Git integration
* 自動 Peek
* AI 功能

---

# 30. Phase 2

完成 MVP 後再增加：

1. Interface → Implementation
2. Nested Peek
3. Breadcrumb
4. Back / Forward
5. Method line limit
6. Show more
7. Spring Bean resolution
8. Lombok support
9. Generated source support
10. 快捷鍵自訂
11. Peek state persistence

---

# 31. 建議 Plugin Architecture

```text
src/main/java
└── .../peekdefinition
    │
    ├── action
    │   └── PeekDefinitionAction
    │
    ├── resolver
    │   ├── DefinitionResolver
    │   └── MethodDefinitionResolver
    │
    ├── model
    │   ├── PeekDefinition
    │   └── PeekState
    │
    ├── editor
    │   ├── PeekInlayManager
    │   ├── PeekBlockInlay
    │   └── PeekEditorRenderer
    │
    ├── navigation
    │   └── PeekNavigationManager
    │
    └── lifecycle
        └── PeekEditorListener
```

---

# 32. 核心流程

```text
User places caret
        │
        ▼
PeekDefinitionAction
        │
        ▼
Find PsiElement
        │
        ▼
Resolve Reference
        │
        ▼
PsiMethod / PsiClass
        │
        ▼
DefinitionResolver
        │
        ▼
Create PeekDefinition
        │
        ▼
PeekInlayManager
        │
        ▼
Create Block Inlay
        │
        ▼
Render Definition
        │
        ▼
Display inside Editor
```

---

# 33. 最重要的技術驗證

正式開始 Plugin 開發之前，建議先做一個 Proof of Concept。

只驗證這件事：

```text
Java Method Call
        ↓
PsiMethod
        ↓
Block Inlay
        ↓
真正的 IntelliJ Java Editor Rendering
```

測試：

```java
userService.getUser(id);
```

能否得到：

```text
userService.getUser(id);

┌───────────────────────────────────┐
│ public User getUser(Long id) {   │
│     var user = repository...;    │
│     return user;                 │
│ }                                │
└───────────────────────────────────┘
```

並確認：

* Java syntax highlighting 正常
* method/class 可以點擊
* Editor 不跳頁
* 原始 Document 沒有被修改
* Theme 正常
* Inlay 可以移除

如果這個 POC 成功，再開始做 Navigation、Nested Peek、Spring 等功能。

---

# 34. 最終目標

Plugin 最終提供：

```text
                    IntelliJ IDEA

var result = clockService.clockIn(userId);

             ┌──────────────────────────────────────┐
             │ ClockService.clockIn                 │
             │                                      │
             │ public ClockResult clockIn(...) {   │
             │     var user =                       │
             │         userService.getUser(userId); │
             │                                      │
             │     persist(user);                   │
             │     return success();                │
             │ }                                    │
             │                                      │
             │  ▼ Peek userService.getUser()        │
             └──────────────────────────────────────┘
```

核心理念：

> **Definition 是「嵌入 Editor 的可互動 Code View」，而不是 Popup。**

這也是本 Plugin 與 IntelliJ `Quick Definition` 最大的差異。
