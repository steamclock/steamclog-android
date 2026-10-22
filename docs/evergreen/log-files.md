---
kind: prescriptive
summary: "How SteamcLog writes, reads and flushes its log files on one background thread, and the rules code that touches those files must follow."
last-human-review:
last-human-reviewer:
---

# Log files

SteamcLog writes its log files on one background thread, the file thread. A log call never
reads or writes the log files on the thread that logged. Reads wait for every earlier write, and
the process flushes pending lines before it ends, when SteamcLog gets the chance (rule 3).
This doc covers SteamcLog's own log files only. Disk use inside the Sentry SDK is outside it: when
SteamcLog sends an error, user report or fatal log, Sentry reads device files on the calling
thread (#183).

## Where it lives

All paths are under `steamclog/src/main/java/com/steamclock/steamclog/`. Find a symbol with
`grep -n`; this doc names symbols, not line numbers, so it stays correct when the files change.

| What | Symbol | File |
|---|---|---|
| The file thread and its queue | `ExternalLogFileDestination.fileExecutor` | `Destinations.kt` |
| Queue a write | `ExternalLogFileDestination.log` | `Destinations.kt` |
| Blocking read | `ExternalLogFileDestination.onFileThread`, `getLogFiles`, `getLogFileContents` | `Destinations.kt` |
| Timed read inside a log call | `ExternalLogFileDestination.getLogFilesOrNull`, `SteamcLog.getLogFilesForAttachment` | `Destinations.kt`, `Steamclog.kt` |
| Flush | `ExternalLogFileDestination.flush` | `Destinations.kt` |
| Flush before `fatal` ends the process | `SteamcLog.closeApp` | `Steamclog.kt` |
| Flush before a crash ends the process | `SteamcLog.installFlushOnCrash`, `SteamcLog.flushingUncaughtExceptionHandler` | `Steamclog.kt` |
| Attaching files to a user report | `SentryDestination.log` | `Destinations.kt` |

## Why a file thread

Timber calls every planted tree on the thread that logged, which is usually the main thread.
Timber itself never touches the disk; its `DebugTree` writes to Logcat. SteamcLog's log file tree
(`ExternalLogFileDestination`) does write to disk. Before #151 it opened, wrote and closed the file
on the calling thread for every line, so an app with StrictMode's disk checks on got a violation
for every logged line.

## How it works

`ExternalLogFileDestination.fileExecutor` is a single-thread executor: one background thread
named `steamclog-file-writer` and a queue. It runs the queued tasks one at a time, in the order
they were added.

| Operation | How it uses the queue | Caller waits? |
|---|---|---|
| Write a line (`log`) | Builds the line on the calling thread, then queues the write with `execute()` | No |
| Delete expired files (`removeOldLogFiles()`) | Queues with `execute()` | No |
| Read (`getLogFiles`, `getLogFileContents`) | Queues with `submit()`, then blocks on the returned `Future.get()` | Yes, until every earlier write is done |
| Read inside a log call (`getLogFilesOrNull`) | Queues with `submit()`, then waits on `Future.get()` with a timeout | Yes, at most the timeout; returns null instead of throwing (rule 5) |
| Flush (`flush(timeoutMs)`) | Queues an empty task and waits for it, up to the timeout | Yes, at most `timeoutMs` |

The line is built on the calling thread because its tag, thread name and timestamp describe the
caller. Only the disk work moves.

Because the queue runs in order, a read always sees every line logged before it was called, and a
flush returns once everything queued before it is on disk.

## Rules

1. **All log file access inside SteamcLog goes through the file thread.** Writing, rotating,
   deleting, listing and reading the files, and the `cachedCurrentLogFile` state, belong to the file
   thread only. This is why no locks are needed, and why two log calls can never rotate the
   same file at once (#155). The exception is the `File`s that `getAllLogFiles()` and
   `getLogFilesForAttachment()` return: the caller reads them on its own thread.
   SentryDestination attaches them to a user report, and Sentry reads them later. A line written during that read, or a rotation that empties the file, can
   show up in the attachment. This is accepted for now; #179 tracks attaching copies instead.
2. **Code running on the file thread never waits on the file thread.** `onFileThread()` queues a
   task and blocks until it runs. Called from a task that is already on the file thread, it waits
   for itself forever. Inside a file-thread task, call the direct versions instead, as
   `getLogFileContents` does with `sortedLogFiles` and the three-argument `removeOldLogFiles`.
3. **Flush before the process ends.** The file thread is a daemon thread, so it never keeps a JVM
   process alive, and lines still in the queue are lost when the process dies. Every path that
   SteamcLog sees end the process flushes first, with a timeout:
   - `SteamcLog.fatal` flushes for up to 1 second, then flushes Sentry for up to 2 seconds. The
     total stays below Android's 5-second ANR limit.
   - `initWith` wraps the default uncaught-exception handler, once, and only when the config has
     a `fileWritePath`. The wrapper flushes for up to 1 second, then calls the handler it
     replaced (Sentry's, when Sentry is set up), which then calls Android's handler. It calls the
     replaced handler in a `finally`, so a failed flush cannot stop crash reporting.

   Some endings run no code in the app, so nothing can flush: Android killing the app to free
   memory, a force-stop, the kill after an ANR, a native crash, and other code calling
   `Process.killProcess` or `System.exit`. Lines still queued then are lost, usually the last few
   milliseconds' worth. Before #151 each line was written before the log call returned, so it
   survived these. This is the accepted cost of writing off the calling thread.
4. **Blocking reads stay off the main thread where possible.** `getFullLogContents()` and
   `getAllLogFiles()` block until pending writes finish. Their KDoc says so.
5. **Reads inside a log call have a timeout and never throw.** A log call never throws (#144)
   and may run on the main thread. When a user report attaches log files, the Sentry tree lists
   them with `SteamcLog.getLogFilesForAttachment`, which waits at most 1 second and returns
   null (no attachments) on a timeout, an interrupted caller, or an error. Use the same kind of
   read for any other file access added to a log call.
6. **The file thread runs at background priority.** A new thread copies the priority of the
   thread that creates it, usually the main thread, so the thread lowers itself to
   `THREAD_PRIORITY_BACKGROUND` before running any task.

## Why an executor and not a HandlerThread

A `HandlerThread` would also run tasks in order on one thread. The executor was chosen because:

- `submit()` and `Future.get()`, with or without a timeout, give results and waiting for free. A
  `HandlerThread` needs a hand-written `FutureTask` or `CountDownLatch` for each.
- It runs in plain JVM unit tests. Android's `Handler` classes are stubs there: with this repo's
  `isReturnDefaultValues = true`, `post()` does nothing, and without it the stubs throw. Testing
  a `HandlerThread` version would need Robolectric or device tests.

A `HandlerThread` fits work that needs delays or message passing. The file thread needs neither.

## Tests

`LogFileWriteTest` covers read ordering, several threads logging at once (with and without a
rotation, #155), flush and its timeout, the timed read inside a log call, interrupted callers,
the crash handler, and the thread name in each line. The rotation test lets the emptied file's
old contents disappear and checks only that every new line is written once.

`LogFileStrictModeTest` (instrumented, `steamclog/src/androidTest`) logs from the main thread
with StrictMode's disk read and write checks set to kill the process, then checks the lines reach
the file. Run it with `./gradlew :steamclog:connectedDebugAndroidTest` on a device or emulator.
It has three tests:

| Test | Preset | Sentry | Covers |
|---|---|---|---|
| `logging_onMainThread_doesNoDiskIo_andTheLinesReachTheFile` | `Debug` | Not sent | Console and file trees |
| `releasePreset_withSentry_breadcrumbsOnMainThread_doNoDiskIo` | `Release` | Set up | Info logs as Sentry breadcrumbs |
| `releasePreset_errorAndUserReportWithAttachments_onMainThread_doNoSteamcLogDiskIo` | `Release` | Not set up | Error and user report paths, including listing attachments |

The third test leaves Sentry unset because Sentry's own event capture reads device files on the
calling thread (#183). During review of #178 the first test passed on API 23, 28, 35 and 36 and
failed on the code before #151; all three passed on API 23 and 35.
