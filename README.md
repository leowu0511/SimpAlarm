# SimpAlarm

SimpAlarm 是一個 Android 通知鬧鐘工具。你可以指定要監聽的 App 與對象名稱，當符合條件的通知出現時，手機會立刻播放鬧鐘聲，並提供通知欄關閉或跳出解除畫面。

這個專案的定位是「有點荒謬但真的有用」的小工具：適合那些你不想漏掉、又怕自己睡過頭或滑過通知的人的訊息。

## 目前功能

- 支援監聽多個 App：Instagram、WhatsApp、WhatsApp Business、Discord、LINE、Telegram。
- 其中 Instagram 已作為主要測試目標；其他 App 使用相同通知監聽流程，理論上可支援，但實際可用性會受各 App 通知格式影響。
- 可建立多個監聽對象，每個對象都有顯示名稱、通知 ID、開關狀態與頭像。
- 對象可使用照片頭像；未設定照片時會自動產生幾何頭像。
- 支援對象置頂與拖曳排序。
- 支援單次模式與持續監聽模式。
- 可用星號讓特定對象鎖定為持續監聽。
- 可選擇鬧鐘觸發後「跳出解除畫面」或「只響鈴」。
- 鬧鐘會使用系統預設鬧鐘聲循環播放，並搭配震動。
- 觸發時會建立前景通知，通知欄可直接關閉鬧鐘。
- 跳出解除畫面支援鎖定畫面顯示，並使用滑動關閉降低誤觸。
- 提供觸發紀錄頁，記錄對象、來源 App、時間與訊息摘要。
- 首次設定採用可恢復的任務流程，跳到系統設定後回到 App 會自動接續。

## 使用方式

1. 開啟 App，依照首次設定流程完成必要設定。
2. 在「應用」頁選擇要監聽的通知來源。
3. 在「對象」頁新增監聽對象。
4. 輸入顯示名稱與通知上實際會出現的名稱或 ID。
5. 開啟 Android 的通知監聽權限。
6. 允許鬧鐘通知與全螢幕鬧鐘權限。
7. 使用「測試鬧鐘」確認聲音、震動與關閉流程正常。

## 通知比對邏輯

SimpAlarm 會監聽已選擇 App 的通知，並使用通知 sender / title 來比對對象名稱。

目前採用片段符合：

```text
輸入：1234567
通知 sender：123456789
結果：會觸發，因為通知 sender 包含 1234567
```

App 不會比對訊息內容，避免別人在訊息中提到某個名字時誤觸發。

同一個監聽對象可以設定多個通知名稱，例如：

```text
暱稱,用戶ID
暱稱、用戶ID
暱稱 / 用戶ID
```

這些名稱會視為同一個對象。只要通知 sender / title 出現其中一個，就會觸發同一個對象。

請以 Android 通知上實際顯示的文字為準。如果某個用戶 ID 或暱稱沒有出現在通知資料裡，App 就無法用它判斷。

## 觸發模式

SimpAlarm 支援兩種全域模式：

- 單次模式：對象觸發一次後會自動關閉，下次需要手動打開。
- 持續監聽：對象保持開啟時，每次符合通知都會觸發。

每個對象也可以獨立設定：

- 開關：決定該對象是否啟用。
- 星號：即使全域是單次模式，該對象仍持續監聽。
- 圖釘：將對象固定在列表上方。

## 鬧鐘方式

SimpAlarm 支援兩種鬧鐘顯示方式：

- 跳出畫面：觸發時會嘗試開啟解除鬧鐘頁面，並同時顯示前景通知。
- 只響鈴：只播放聲音、震動與顯示通知，不主動跳出畫面。

不論哪種模式，通知欄都會提供關閉鬧鐘的按鈕。

## 需要的權限

- 通知監聽權限：讀取已選擇 App 的通知。
- 通知權限：Android 13 以上需要，讓鬧鐘通知可以顯示。
- 全螢幕通知權限：Android 14 以上可能需要，讓鬧鐘解除畫面可以跳出。
- 震動權限：觸發鬧鐘時震動。
- 前景服務權限：讓鬧鐘能在背景可靠播放。
- 電池最佳化例外：建議設定，降低背景監聽被系統限制的機率。

## 專案結構

```text
app/src/main/java/com/example/simpalarm/
  MainActivity.kt
  SimpNotificationListener.kt
  SimpAlarmService.kt
  AlarmDismissActivity.kt
  SimpTargetManager.kt
  SimpEventLog.kt
```

核心檔案說明：

- `MainActivity.kt`：Jetpack Compose UI，包含首頁、對象列表、App 選擇、觸發紀錄、設定、首次引導、拖曳排序與頭像。
- `SimpNotificationListener.kt`：通知監聽入口，判斷通知來源與對象是否符合。
- `SimpAlarmService.kt`：前景鬧鐘服務，負責播放聲音、震動、通知欄動作與啟動解除畫面。
- `AlarmDismissActivity.kt`：鬧鐘解除畫面，提供滑動關閉與開啟來源 App。
- `SimpTargetManager.kt`：管理監聽對象、App 選擇、觸發模式、排序與 SharedPreferences 儲存。
- `SimpEventLog.kt`：記錄最近事件與觸發紀錄。

## 技術

- Kotlin
- Jetpack Compose
- Android NotificationListenerService
- Android Foreground Service
- Gradle Kotlin DSL

## 建置

確認已安裝 Android Studio 與 JDK，然後執行：

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 會輸出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 給想 Git 協助開發的寶子們的上傳建議

建議納入版本控制：

```text
app/
gradle/
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew
gradlew.bat
README.md
LICENSE
.gitignore
```

不建議上傳：

```text
.gradle/
.idea/
.kotlin/
build/
app/build/
local.properties
*.apk
*.aab
```

## 注意事項

- 不同 App 的通知格式可能因語言、帳號、系統版本或通知類型而改變。
- 若沒有觸發，請先確認通知監聽權限是否已啟用。
- 某些手機品牌會限制背景服務，可能需要額外允許自啟動或關閉電池最佳化。
- Android 可能限制背景跳出 Activity；若跳出畫面不穩定，可以改用只響鈴模式並從通知欄關閉。

