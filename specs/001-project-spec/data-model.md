# Data Model: Project Specification

## Entities

### Translation
- **Fields**:
  - shortName: String (primary key, e.g., "KJV")
  - name: String (e.g., "King James Version")
  - language: String (e.g., "English")
- **Relationships**: One-to-many with Book
- **Validation**: shortName must be unique, 2-10 characters

### Book
- **Fields**:
  - id: Int (auto-generated primary key)
  - translationShortName: String (foreign key to Translation)
  - name: String (e.g., "Genesis")
  - abbreviation: String (e.g., "Gen")
- **Relationships**: Many-to-one with Translation, one-to-many with Chapter
- **Validation**: name and abbreviation required

### Chapter
- **Fields**:
  - id: Int (auto-generated primary key)
  - bookId: Int (foreign key to Book)
  - number: Int (chapter number)
- **Relationships**: Many-to-one with Book, one-to-many with Verse
- **Validation**: number > 0

### Verse
- **Fields**:
  - id: String (composite key: "KJV-GEN-1-1")
  - bookId: Int (foreign key to Book)
  - chapter: Int
  - verse: Int
  - text: String
- **Relationships**: Many-to-one with Chapter/Book
- **Validation**: text not empty, verse > 0

### Bookmark
- **Fields**:
  - id: String (UUID)
  - verseId: String (foreign key to Verse)
  - addedDate: DateTime
  - note: String? (optional)
- **Relationships**: Many-to-one with Verse
- **Validation**: verseId must exist

### Highlight
- **Fields**:
  - id: String (UUID)
  - verseId: String (foreign key to Verse)
  - color: String (hex code)
  - addedDate: DateTime
- **Relationships**: Many-to-one with Verse
- **Validation**: valid hex color

### Note
- **Fields**:
  - id: String (UUID)
  - verseId: String (foreign key to Verse)
  - content: String
  - createdDate: DateTime
  - modifiedDate: DateTime
- **Relationships**: Many-to-one with Verse
- **Validation**: content not empty

### CrossReference
- **Fields**:
  - id: String (UUID)
  - fromVerseId: String (foreign key to Verse)
  - toVerseId: String (foreign key to Verse)
  - description: String?
- **Relationships**: Many-to-one with Verse (from/to)
- **Validation**: from != to

## State Transitions

### Reading Session
1. **Initial**: No book/chapter selected
2. **Book Selected**: Book chosen, chapters listed
3. **Chapter Selected**: Verses loaded and displayed
4. **Verse Focused**: Single verse highlighted
5. **Search Active**: Search results displayed

### Bookmark Lifecycle
1. **Not Bookmarked** → User bookmarks → **Bookmarked**
2. **Bookmarked** → User removes → **Not Bookmarked**

### Translation Download
1. **Not Downloaded** → User initiates download → **Downloading**
2. **Downloading** → Success → **Downloaded**
3. **Downloading** → Failure → **Not Downloaded** (with error)