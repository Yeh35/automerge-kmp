# Automerge-KMP 개발 가이드: Rust Core 기반 Cross-Platform Wrapper

이 문서는 `automerge-rs` (Rust Core)를 Kotlin Multiplatform (Android, iOS, JVM, Web) 환경에서 사용할 수 있도록 `automerge-kmp` 래퍼 라이브러리를 설계하고 구축하는 전 과정을 다룹니다.

데이터 소유권을 온전히 사용자에게 넘기는 로컬 퍼스트(Local-first) 아키텍처와 완벽한 오프라인 동기화를 구현하기 위해, 파편화된 브릿지 라이브러리 대신 단일 Rust 코어를 모든 플랫폼에서 직접 컴파일하여 연결하는 방식을 채택합니다.



---

## 1. 아키텍처 개요 (Architecture Overview)

하나의 Rust 코드베이스를 두 가지 브릿지 기술을 통해 KMP로 연결합니다.

1. **Mobile & Desktop (Android, iOS, JVM):** * **기술:** `Mozilla UniFFI`
   * Rust 코드를 C-ABI 및 JNI로 변환하고, Kotlin 인터페이스를 자동 생성합니다.
2. **Web (Browser):**
   * **기술:** `wasm-bindgen`
   * 브라우저 샌드박스 환경을 위해 Rust를 WebAssembly로 컴파일하고 JavaScript/TypeScript 바인딩을 생성하여 Kotlin/JS(또는 Kotlin/Wasm)와 연결합니다.

---

## 2. 개발 환경 설정 (Prerequisites)

각 플랫폼 빌드를 위한 툴체인이 모두 필요합니다.

* **Rust:** `rustup` 설치 (최신 안정화 버전)
  * `rustup target add aarch64-apple-ios x86_64-apple-ios` (iOS 타겟)
  * `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android` (Android 타겟)
  * `rustup target add wasm32-unknown-unknown` (Web 타겟)
* **Web:** `wasm-pack` (WebAssembly 빌드용)
* **Android/JVM:** JDK 17+, Android SDK & NDK
* **iOS:** Xcode 및 Command Line Tools
* **Kotlin:** IntelliJ IDEA 또는 Android Studio

---

## 핵심 주의 사항 및 트러블슈팅

### 1. 메모리 관리 (Memory Management)

* **문제:** Rust에서 할당된 메모리는 Kotlin의 가비지 컬렉터(GC)가 알지 못합니다.
* **해결책:** `AutomergeDocument`에 `AutoCloseable`을 구현하여 `.use { ... }` 블록 내에서 사용하도록 강제하거나, Kotlin의 `Cleaner` API (Android/JVM) 및 `FinalizationRegistry` (Web)를 활용하여 참조가 끊어질 때 Rust 포인터의 `free` 함수가 호출되도록 안전망을 구축해야 합니다.

### 2. 스레드 동기화 (Concurrency)

* **문제:** UI 스레드에서 문서에 쓰기(`put`)를 시도하는 동시에, 백그라운드 코루틴에서 네트워크 동기화(`sync`)가 발생하면 Rust 코어에서 패닉(Panic)이 발생할 수 있습니다.
* **해결책:** Kotlin `Mutex`를 사용하여 단일 `AutomergeDocument` 인스턴스에 대한 접근을 직렬화(Serialize)해야 합니다.

### 3. WebAssembly 로딩 비동기 처리

* **문제:** Web 타겟에서는 Wasm 파일 로딩이 비동기적으로 이루어집니다.
* **해결책:** KMP의 초기화 루틴에 `suspend fun initAutomergeWeb()`과 같은 함수를 제공하여, Wasm 인스턴스가 브라우저에 완전히 적재된 후(Instantiated)에만 문서 작업이 수행되도록 처리해야 합니다.
