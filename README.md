Implement a terminal text buffer — the core data structure that terminal emulators use to store and manipulate displayed text.

When a shell sends output, the terminal emulator updates this buffer, and the UI renders it.

A terminal buffer consists of a grid of character cells. Each cell can have:
- Character (or empty)
- Foreground color: default, or one of 16 standard terminal colors
- Background color: default, or one of 16 standard terminal colors
- Style flags: bold, italic, underline (at minimum)

The buffer maintains a cursor position — where the next character will be written.

The buffer has two logical parts:
- Screen — the last N lines that fit the screen dimensions (e.g., 80×24). This is the editable part and what users see.
- Scrollback — lines that scrolled off the top of the screen, preserved for history and unmodifiable. Users can scroll up to view them.

Terminal buffer requirements

Basic operations

Implement a TerminalBuffer class (or equivalent) supporting the following operations:

Setup
- Configurable initial width and height
- Configurable scrollback maximum size (number of lines)

Attributes
- Set current attributes: foreground, background and styles. These attributes should be used for further edits.

Cursor
- Get/set cursor position (column, row)
- Move cursor: up, down, left, right by N cells
- Cursor must not move outside screen bounds

Editing

Operations that should take the current cursor position and attributes into account:

- Write a text on a line, overriding the current content. Moves the cursor.
- Insert a text on a line, possibly wrapping the line. Moves the cursor.
- Fill a line with a character (or empty)

Operations that do not depend on cursor position or attributes:
- Insert an empty line at the bottom of the screen
- Clear the entire screen
- Clear the screen and scrollback

Content Access
- Get character at position (from screen and scrollback)
- Get attributes at position (from screen and scrollback)
- Get line as string (from screen and scrollback)
- Get entire screen content as string
- Get entire screen+scrollback content as string


Bonus

If you complete the core requirements and want an extra challenge:

- Wide characters: Some characters (e.g., CJK ideographs, emoji) occupy 2 cells in terminals. Handle writing and cursor movement for such characters.
- Resize: change the dimensions of the screen (content handling strategy is your design decision)

Technical Constraints

- Language: Java or Kotlin
- No external libraries except for testing (any test framework is allowed)
- Build tool: Gradle or Maven

Expected results

Explain the solution, trade-offs and decisions you made before submitting the task.
If you have any improvements in your mind but didn't have time to implement them, mention them as well.

Attach a link to a public Git repository (GitHub, GitLab, etc.).
The repository should contain:

Source code

Build file that compiles

Unit Tests:

Comprehensive test coverage

Edge cases and boundary conditions

Tests should document expected behavior

Git history:

Incremental commits showing the development process

Clear, descriptive commit messages

Separation of adding new features and refactorings

