/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A hook to register a runnable, which will be executed after a successful spring transaction.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class AfterTransactionCommitExecutor {

    /**
     * Shared thread pool for executing after-commit runnables asynchronously.
     * Using a separate pool prevents slow/blocking runnables (e.g. RabbitMQ event publishing)
     * from delaying the HTTP response to the client.
     */
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(final Runnable r) {
            final Thread t = new Thread(r, "after-commit-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    @SuppressWarnings("squid:S1217")
    public static void afterCommit(final Runnable runnable) {
        log.debug("Submitting new runnable {} to run after transaction commit", runnable);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations().stream().filter(TransactionSynchronizationImpl.class::isInstance)
                    .map(TransactionSynchronizationImpl.class::cast).findAny().orElseGet(() -> {
                        final TransactionSynchronizationImpl newTS = new TransactionSynchronizationImpl();
                        TransactionSynchronizationManager.registerSynchronization(newTS);
                        return newTS;
                    }).afterCommit(runnable);
        } else {
            log.info("Transaction synchronization is NOT ACTIVE/ INACTIVE. Executing right now runnable {}", runnable);
            runnable.run();
        }
    }

    private static class TransactionSynchronizationImpl implements TransactionSynchronization {

        private final List<Runnable> afterCommitRunnables = new ArrayList<>();

        @Override
        // Exception squid:S1217 - Is aspectJ proxy
        @SuppressWarnings({ "squid:S1217" })
        public void afterCommit() {
            log.debug("Transaction successfully committed, submitting {} runnables to async executor", afterCommitRunnables.size());
            for (final Runnable afterCommitRunnable : afterCommitRunnables) {
                log.debug("Submitting runnable {} to async executor", afterCommitRunnable);
                EXECUTOR.execute(() -> {
                    try {
                        afterCommitRunnable.run();
                    } catch (final RuntimeException e) {
                        log.error("Failed to execute runnable {}", afterCommitRunnable, e);
                    }
                });
            }
        }

        @Override
        @SuppressWarnings({ "squid:S1217" })
        public void afterCompletion(final int status) {
            log.debug("Transaction completed after commit with status {}", status == TransactionSynchronization.STATUS_COMMITTED ? "COMMITTED" : "ROLLEDBACK");
        }

        private void afterCommit(final Runnable runnable) {
            afterCommitRunnables.add(runnable);
        }
    }
}