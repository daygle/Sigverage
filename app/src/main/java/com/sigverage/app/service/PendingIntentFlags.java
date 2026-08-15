package com.sigverage.app.service;

import android.app.PendingIntent;

/**
 * Flag combinations for {@link PendingIntent} creation, written in Java on
 * purpose.
 *
 * <p>CodeQL's {@code java/android/implicit-pendingintents} query treats a
 * PendingIntent as possibly mutable unless it can trace the
 * {@code PendingIntent.FLAG_IMMUTABLE} field access into the flags argument.
 * The Kotlin 2.x (K2) compiler constant-folds {@code FLAG_IMMUTABLE} (a
 * {@code static final int} in android.jar) into a plain integer literal, so
 * flag combinations written in Kotlin — including {@code A or B} — are
 * invisible to the query and it reports a false positive
 * (see github/codeql#20153). Expressing the combination in Java preserves the
 * bitwise-expression form the query understands.
 */
public final class PendingIntentFlags {
    private PendingIntentFlags() {
    }

    /**
     * {@code FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE}: replace the extras of an
     * existing PendingIntent, and never allow it to be mutated by whoever
     * receives it.
     */
    public static int updateCurrentImmutable() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    /**
     * {@code FLAG_NO_CREATE | FLAG_IMMUTABLE}: fetch an existing PendingIntent
     * without creating one, and never allow it to be mutated by whoever
     * receives it.
     */
    public static int noCreateImmutable() {
        return PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE;
    }
}
