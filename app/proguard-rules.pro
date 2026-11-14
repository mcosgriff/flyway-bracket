# App-specific R8 rules. Libraries bring their own consumer rules.

# WorkManager saves each queued run with its worker's class name, and SyncWorkerFactory makes the worker by that
# name (#43). Its name must not change between releases, or runs queued before an update would find no worker.
-keepnames class io.cosgriff.flywaybracket.data.sync.SyncWorker
