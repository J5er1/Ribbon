package app.readribbon.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.readribbon.app.AppModel
import java.util.concurrent.TimeUnit

// The only way anything can reach a person while Ribbon is closed (S19).
//
// Before this, the room's websocket lived exactly as long as the app was on
// screen — `repeatOnLifecycle(RESUMED)` opens it and the `finally` closes it
// — and `refreshFromRemote` was called from nowhere else. So the only moments
// the app could learn anything were moments somebody was already looking at
// it, which is precisely when a notification is not needed. Every notification
// in S19 would have been, at best, a heads-up shown to a person holding the
// phone open on the room.
//
// **Why a periodic worker and not the two obvious alternatives.**
//
// Not a foreground service. Keeping the socket alive in the background needs
// one, and a foreground service means a permanent "Ribbon is connected"
// notification sitting in the shade — a piece of chrome about the app's own
// plumbing, always there, saying nothing to anybody. That is the opposite of
// §1's room, and it would be the most visible thing the product does.
//
// Not push, at the time — and push exists now (services/Push.kt, ledger
// A56): the backend sends each of the six the moment its row is written,
// and while it is delivering for this phone the pull below still merges but
// posts nothing, so nothing is said twice. This worker is what a build
// without Firebase, or a phone the server cannot reach, still relies on.
//
// **What this route can carry, and what it honestly cannot.** Worth stating
// plainly rather than discovering later:
//
//   - carried, because each has a row in the database: "Ruth left you a note
//     at Mark 4:9", the collapsed "Ruth left you a note", "The cards are
//     open", "You finished Mark together". Late by up to about fifteen
//     minutes, which for a note that was left to be found later is not a
//     defect — §4.4's whole beat is being found *later*.
//   - not carried: "Ruth is reading Mark". Presence is ephemeral and lives
//     only on the socket; its own S19 subtitle is "So you can read at the
//     same time", and a fifteen-minute-old version of that is a lie. It
//     posts only while Ribbon is running, and the switch means what it says
//     only then.
//   - not carried: "thinking of you". The tap is a client-to-client
//     broadcast with no row behind it anywhere, so there is nothing for a
//     pull to find. A tap sent to a closed app is lost, exactly as it was
//     before this file existed. Fixing that needs a table on the shared
//     backend, which is not an Android-only change.
//
// Fifteen minutes is the platform's floor for periodic work and not a number
// anybody chose; Android will stretch it further under Doze, which is correct
// behaviour for an app whose entire argument is that it is unhurried.

class RoomWatch(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Nothing to say to a phone that will not pass it on. Checked before
        // the network call rather than after, because the pull is the
        // expensive half and a person who refused the permission should not
        // be paying for it in battery.
        if (!Notifications.allowed(applicationContext)) return Result.success()

        return runCatching {
            // A whole model rather than a bare `RemoteSync`, because the
            // decisions this has to make — which room, whose switches, quiet
            // hours, the watermark that keeps a new phone quiet — all live on
            // it, and a second copy of that arithmetic here is a second copy
            // that can drift. It is expensive to build and this runs at most
            // four times an hour.
            //
            // `refreshFromRemote` posts what it finds itself: it is the one
            // place that can tell an arrival from a row that was already
            // there, so it is the one place that announces.
            //
            // Built for the pull and shut down after it. A model is a
            // `ViewModel`, and one built outside a `ViewModelStore` never has
            // `onCleared` called — so an early version of this worker left a
            // network callback, a live websocket with a heartbeat and an
            // uncancelled scope behind it every fifteen minutes, forever.
            // `forBackgroundPull` also skips the update check and the socket,
            // which a pull that exists to post a notification has no use for.
            val model = AppModel.load(applicationContext, forBackgroundPull = true)
            try {
                model.refreshFromRemote()
            } finally {
                model.shutDown()
            }
            Result.success()
        }.getOrElse {
            // A failed pull is a quiet nothing. Retrying inside a job that
            // runs again in fifteen minutes anyway would only spend battery
            // on a phone that is probably offline — and an app that says
            // nothing when it cannot reach the server is exactly what
            // Connectivity.kt argues for.
            Result.success()
        }
    }

    companion object {
        private const val NAME = "room-watch"

        /**
         * Start watching, once. `KEEP` rather than `UPDATE` so a relaunch
         * never restarts the interval — an app opened twenty times a day
         * would otherwise never reach the end of one period and never pull
         * at all, which is the classic way this kind of worker silently does
         * nothing.
         *
         * Called when there is an account to watch for, and not from
         * `Application.onCreate`. Two reasons, and the second is the one that
         * matters: a person who has never signed in has nothing to pull, so
         * starting there would wake the phone four times an hour to do
         * nothing; and `WorkManager.getInstance` throws outright when its
         * `androidx.startup` initializer has not run, which under Robolectric
         * it does not — so a call from `onCreate` takes the whole process
         * down before the first frame. The look book found that, which is
         * exactly what it is for (deviation A24).
         */
        fun start(context: Context) {
            val request = PeriodicWorkRequestBuilder<RoomWatch>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            // A background nicety that cannot be scheduled is silence, never
            // a crash — the same contract every other best-effort call in
            // this app keeps.
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }

        /**
         * Stop. Called when the account goes away — signed out, or deleted.
         * A worker that keeps pulling for somebody who is no longer here
         * would be both pointless and, on the deletion path, wrong.
         */
        fun stop(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        }
    }
}
