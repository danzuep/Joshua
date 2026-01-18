# Implementation Plan: Project Specification

**Branch**: `001-project-spec` | **Date**: 2026-01-18 | **Spec**: [specs/001-project-spec/spec.md](specs/001-project-spec/spec.md)
**Input**: Feature specification from `/specs/001-project-spec/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Implement the Joshua Bible reader app for Android with offline reading, multi-version support, search, bookmarks, and study features. Core architecture uses MVVM with dependency injection, local SQLite storage, and minimal dependencies for small app size.

## Technical Context

**Language/Version**: Kotlin (latest stable) with Gradle
**Primary Dependencies**: AndroidX libraries, SQLite, minimal third-party
**Storage**: Local SQLite database for verses/translations, SharedPreferences for settings
**Testing**: JUnit, Espresso, Robolectric
**Target Platform**: Android API 19+
**Project Type**: Mobile app
**Performance Goals**: <2s startup, <1s search, <50MB size
**Constraints**: Offline-first, no cloud sync, minimal permissions
**Scale/Scope**: Single user, local data, 10+ translations
**Build System**: Gradle with Kotlin DSL
**Dependency Injection**: Dagger or Hilt
**UI**: Android Views (minimal dependencies)  

<!--
### Development Workflow
- Feature development follows TDD: write tests first, then implement.
- Code reviews required for all changes, focusing on constitution compliance.
- Automated CI/CD with tests and linting gates.
- Release process documented in docs/RELEASING.md.
-->

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- High Code Quality: Feature design must include plans for comprehensive tests and adherence to Kotlin style guidelines.
- Modularity: Clear separation of concerns with defined interfaces for dependency injection.
- Simple and Predictable UX: User scenarios must be simple and predictable; include help text plans.
- Minimal Dependencies: No new dependencies unless justified; prefer standard Android libraries.
- Backward Compatibility: Ensure changes follow semantic versioning; plan data migrations if needed.

## Project Structure

### Source Code (Android app)

```text
app/src/main/
├── AndroidManifest.xml
├── kotlin/me/xizzhu/
│   ├── data/           # Data layer: repositories, DAOs, models
│   │   ├── entities/   # Database entities (Translation, Book, Chapter, Verse)
│   │   ├── dao/        # Data access objects
│   │   └── repository/ # Repository implementations
│   ├── domain/         # Domain layer: use cases, business logic
│   │   ├── model/      # Domain models
│   │   ├── usecase/    # Use cases (ReadBible, SearchVerses, etc.)
│   │   └── interactor/ # Business logic interactors
│   ├── presentation/   # Presentation layer: ViewModels, UI models
│   │   ├── viewmodel/  # MVVM ViewModels
│   │   ├── ui/         # UI state models
│   │   └── mapper/     # Data to UI mappers
│   └── di/             # Dependency injection modules
├── res/                # Android resources
└── assets/             # Static assets (if needed)

app/src/test/           # Unit tests
├── kotlin/me/xizzhu/
└── resources/

app/src/androidTest/    # Instrumentation tests
└── kotlin/me/xizzhu/
```

**Structure Decision**: Android app with clean architecture (data/domain/presentation layers), MVVM pattern, following constitution modularity principles.

## Module Responsibilities and Public Interfaces

### Data Module (`data/`)
**Responsibilities**: 
- Database schema and migrations
- CRUD operations for entities
- Data persistence and retrieval
- Translation download and parsing

**Public Interfaces**:
```kotlin
interface BibleRepository {
    suspend fun getBooks(translation: String): List<Book>
    suspend fun getVerses(book: Book, chapter: Int): List<Verse>
    suspend fun searchVerses(query: String, translation: String): List<Verse>
}

interface BookmarkRepository {
    suspend fun getBookmarks(): List<Bookmark>
    suspend fun addBookmark(verseId: VerseId): Bookmark
    suspend fun removeBookmark(bookmarkId: String)
}
```

### Domain Module (`domain/`)
**Responsibilities**:
- Business logic and rules
- Use case orchestration
- Domain model transformations
- Validation logic

**Public Interfaces**:
```kotlin
class ReadBibleUseCase(
    private val bibleRepository: BibleRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(request: ReadBibleRequest): ReadBibleResponse
}

data class ReadBibleRequest(val translation: String, val book: String, val chapter: Int)
data class ReadBibleResponse(val verses: List<Verse>, val bookmarks: List<Bookmark>)
```

### Presentation Module (`presentation/`)
**Responsibilities**:
- UI state management
- User interaction handling
- Data formatting for display
- Navigation logic

**Public Interfaces**:
```kotlin
class ReadingViewModel(
    private val readBibleUseCase: ReadBibleUseCase,
    private val bookmarkUseCase: BookmarkUseCase
) : ViewModel() {
    val uiState: StateFlow<ReadingUiState>
    
    fun onVerseClicked(verse: Verse)
    fun onBookmarkToggled(verse: Verse)
}
```

## DI Approach with Rationale

**Chosen Framework**: Hilt (Dagger-based) for Android
**Rationale**: 
- Compile-time dependency injection ensures no runtime failures
- Minimal runtime overhead compared to reflection-based DI
- Strong typing prevents injection errors
- Android-specific lifecycle management
- Aligns with constitution's modularity principle (clear interfaces)

**Module Structure**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideBibleRepository(
        bibleDao: BibleDao,
        translationDownloader: TranslationDownloader
    ): BibleRepository = BibleRepositoryImpl(bibleDao, translationDownloader)
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var readingViewModel: ReadingViewModel
}
```

## External Effects Pattern

**Pattern**: Repository pattern with suspend functions for async operations
**Rationale**: 
- Clear separation of data access from business logic
- Easy to mock for testing
- Supports coroutine-based async programming
- Handles network/database failures gracefully

**Example**:
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
}

interface TranslationDownloader {
    suspend fun downloadTranslation(shortName: String): Result<Translation>
}
```

## Persistence/Storage Choices and Default File Layout

**Primary Storage**: SQLite database via Room
**Rationale**: 
- ACID transactions for data integrity
- SQL queries for complex searches
- Built-in Android support
- Minimal dependencies (Room is AndroidX)

**Schema**:
```sql
CREATE TABLE translations (
    short_name TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    language TEXT NOT NULL
);

CREATE TABLE books (
    id INTEGER PRIMARY KEY,
    translation_short_name TEXT NOT NULL,
    name TEXT NOT NULL,
    abbreviation TEXT NOT NULL,
    FOREIGN KEY (translation_short_name) REFERENCES translations(short_name)
);

CREATE TABLE verses (
    id TEXT PRIMARY KEY, -- "KJV-GEN-1-1"
    book_id INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verse INTEGER NOT NULL,
    text TEXT NOT NULL,
    FOREIGN KEY (book_id) REFERENCES books(id)
);
```

**Settings**: SharedPreferences for user preferences
**Files**: Internal storage for downloaded translation files

## Testing Strategy

### Unit Tests for Each Module

**Data Module**:
```kotlin
@Test
fun `BibleRepository returns verses for chapter`() = runTest {
    // Given
    val mockDao = mock<BibleDao>()
    whenever(mockDao.getVerses("GEN", 1)).thenReturn(listOf(mockVerse))
    
    // When
    val result = repository.getVerses(Book("GEN"), 1)
    
    // Then
    assertEquals(listOf(mockVerse), result)
}
```

**Domain Module**:
```kotlin
@Test
fun `ReadBibleUseCase combines verses and bookmarks`() = runTest {
    // Given
    val mockBibleRepo = mock<BibleRepository>()
    val mockBookmarkRepo = mock<BookmarkRepository>()
    whenever(mockBibleRepo.getVerses(any(), any())).thenReturn(verses)
    whenever(mockBookmarkRepo.getBookmarksForChapter(any())).thenReturn(bookmarks)
    
    // When
    val response = useCase(ReadBibleRequest("KJV", "GEN", 1))
    
    // Then
    assertEquals(expectedResponse, response)
}
```

**Presentation Module**:
```kotlin
@Test
fun `ReadingViewModel updates state on bookmark toggle`() = runTest {
    // Given
    val mockUseCase = mock<BookmarkUseCase>()
    val viewModel = ReadingViewModel(mockUseCase)
    
    // When
    viewModel.onBookmarkToggled(verse)
    
    // Then
    assertTrue(viewModel.uiState.value.isBookmarked)
}
```

**Mocking Approach**: Mockito for interfaces, mockito-kotlin for DSL

### Contract Tests

**Purpose**: Validate recorded vs replayed interactions
**Tools**: Pact or custom recording framework
**Example**:
```kotlin
@Test
fun `BibleRepository contract with DAO`() {
    // Record interactions during happy path
    val recorder = ContractRecorder()
    recorder.record {
        repository.getVerses(Book("GEN"), 1)
    }
    
    // Replay and verify
    recorder.replay {
        verify(mockDao).getVerses("GEN", 1)
    }
}
```

### Integration Test Scenario

**Scenario**: Complete reading flow with search and bookmark
**Test Data**: Pre-populated KJV translation, sample bookmarks
**Steps**:
1. Launch app → Verify main screen loads in <2s
2. Select "Genesis" → Verify chapter list displays
3. Select "Chapter 1" → Verify verses load and display
4. Search "In the beginning" → Verify results show Gen 1:1
5. Bookmark Gen 1:1 → Verify bookmark indicator appears
6. Navigate back to bookmarks → Verify bookmarked verse appears

**Expected Outcomes**: All steps complete successfully, no crashes, performance within limits

## CI Pipeline Outline

**Build Job**:
- Gradle assembleDebug/Release
- Lint checks
- Static analysis (Detekt/Ktlint)

**Unit Tests Job**:
- ./gradlew test
- Coverage report (>80%)
- Publish to Codecov

**Contract Tests Job**:
- Run contract tests
- Validate recordings match expectations

**Integration Demo Job**:
- Build APK
- Run Espresso tests on emulator
- Record demo video
- Upload artifacts

**Triggers**: PRs and main branch pushes

## Developer DX Notes

**Running Local Demo**:
```bash
# Build and install debug APK
./gradlew installDebug

# Run unit tests
./gradlew test

# Run integration tests
./gradlew connectedAndroidTest

# Update recordings (if using contract tests)
./gradlew updateContracts
```

**Updating Recordings**: Run `./gradlew recordContracts` after changing interfaces

## Acceptance Checklist

### Functional Requirements
- [ ] Offline reading works without network
- [ ] Full-text search returns results in <1s
- [ ] Bookmarks persist across app restarts
- [ ] Multiple translations can be downloaded and switched
- [ ] Font size and theme settings are saved
- [ ] Verses can be shared via system share sheet
- [ ] Cross-references navigate correctly
- [ ] Notes are saved and displayed

### Non-Functional Requirements
- [ ] App size <50MB
- [ ] Startup time <2s
- [ ] Search response <1s for 95% of queries
- [ ] No crashes during normal usage
- [ ] Works on API 19+
- [ ] Minimal battery/network usage
- [ ] Accessible (screen reader support)

## Representative Function/Type Signatures

```kotlin
// Domain models
data class Verse(
    val id: VerseId,
    val text: String,
    val book: Book,
    val chapter: Int,
    val verse: Int
)

data class VerseId(val translation: String, val book: String, val chapter: Int, val verse: Int)

// Use cases
interface ReadBibleUseCase {
    suspend operator fun invoke(request: ReadBibleRequest): Result<ReadBibleResponse>
}

// ViewModels
class ReadingViewModel : ViewModel() {
    val uiState: StateFlow<ReadingUiState>
    fun loadChapter(book: Book, chapter: Int)
    fun toggleBookmark(verse: Verse)
}
```

## Example Config Files

**app/build.gradle.kts**:
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("com.google.dagger:hilt-android:2.48")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

**gradle.properties**:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=false
```

## Small Example CLI Command Sequence

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests
./gradlew connectedDebugAndroidTest

# Generate coverage report
./gradlew createDebugCoverageReport
```

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
