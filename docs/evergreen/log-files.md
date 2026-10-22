---
kind: prescriptive
summary: "How SteamcLog writes, reads and flushes its log files on one background thread, and the rules code that touches those files must follow."
last-human-review:
last-human-reviewer:
---

# Log files

SteamcLog writes its log files on one background thread, the file thread. A log call never
touches the disk on the thread that logged. Reads wait for every earlier write, and the process
flushes pending lines before it ends.

## Why a file thread

Timber calls every planted tree on the thread that logged, which is usually the main thread.
Timber itself never touches the disk; its `DebugTree` writes to Logcat. SteamcLog's log file tree
(`ExternalLogFileDestination`) does write to disk. Before #151 it opened, wrote and closed the file
on the calling thread for every line, so StrictMode flagged every logged line.

## How it works

`ExternalLogFileDestination.fileExecutor` is a single-thread executor: one background thread
named `steamclog-file-writer` and a queue. It runs the queued tasks one at a time, in the order
they were added.

| Operation | How it uses the queue | Caller waits? |
|---|---|---|
| Write a line (`log`) | Builds the line on the calling thread, then queues the write with `execute()` | No |
| Delete expired files (`removeOldLogFiles()`) | Queues with `execute()` | No |
| Read (`getLogFiles`, `getLogFileContents`) | Queues with `submit()`, then blocks on the returned `Future.get()` | Yes, until every earlier write is done |
| Flush (`flush(timeoutMs)`) | Queues an empty task and waits for it, up to the timeout | Yes, at most `timeoutMs` |

The line is built on the calling thread because its tag, thread name and timestamp describe the
caller. Only the disk work moves.

Because the queue runs in order, a read always sees every line logged before it was called, and a
flush returns once everything queued before it is on disk.

## Rules

1. **All log file access inside SteamcLog goes through the file thread.** Writing, rotating,
   deleting, listing and reading the files, and the `cachedCurrentLogFile` state, belong to the file
   thread only. This is why no locks are needed, and why two log calls can never rotate the
   same file at once (#155). `getAllLogFiles()` is the exception: it returns `File`s that the
   caller reads on its own thread. SentryDestination attaches them to a user report, and Sentry
   reads them later. A line written during that read, or a rotation that empties the file, can
   show up in the attachment. This is accepted for now; #179 tracks attaching copies instead.
2. **Code running on the file thread never waits on the file thread.** `onFileThread()` queues a
   task and blocks until it runs. Called from a task that is already on the file thread, it waits
   for itself forever. Inside a file-thread task, call the direct versions instead, as
   `getLogFileContents` does with `sortedLogFiles` and the three-argument `removeOldLogFiles`.
3. **Flush before the process ends.** The file thread is a daemon thread, so it never keeps a JVM
   process alive, and lines still in the queue are lost when the process dies. Every path that
   ends the process flushes first, with a timeout:
   - `SteamcLog.fatal` flushes for up to 1 second, then flushes Sentry for up to 2 seconds. The
     total stays below Android's 5-second ANR limit.
   - `initWith` wraps the default uncaught-exception handler. The wrapper flushes for up to
     1 second, then calls the handler it replaced (Sentry's, when Sentry is set up), which then
     calls Android's handler.
4. **Blocking reads stay off the main thread where possible.** `getFullLogContents()` and
   `getAllLogFiles()` block until pending writes finish. Their KDoc says so.

## Why an executor and not a HandlerThread

A `HandlerThread` would also run tasks in order on one thread. The executor was chosen because:

- `submit()` and `Future.get()`, with or without a timeout, give results and waiting for free. A
  `HandlerThread` needs a hand-written `FutureTask` or `CountDownLatch` for each.
- It runs in plain JVM unit tests. Android's `Handler` classes do nothing there, so testing a
  `HandlerThread` version would need Robolectric or device tests.

A `HandlerThread` fits work that needs delays or message passing. The file thread needs neither.

## Tests

`LogFileWriteTest` covers read ordering, several threads logging at once, flush and its timeout,
the crash handler, and the thread name in each line. No test rotates a file. Rotation empties the
oldest file by design, so such a test could not check that every line was kept.
