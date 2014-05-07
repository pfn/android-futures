package com.hanhuy.android.concurrent;

import android.os.AsyncTask;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author pfnguyen
 */
public abstract class Future<V> implements java.util.concurrent.Future<V> {
    /**
     * Runs in background thread
     */
    public abstract void onComplete(Callback<Try<V>> fn);

    /**
     * Runs on UI thread
     */
    public abstract void onSuccess(Callback<V> fn);

    /**
     * Runs on UI thread
     */
    public abstract void onFailure(Callback<Throwable> fn);

    /**
     * Runs in background thread
     */
    public abstract <T> Future<T> map(Function<V,T> fn);

    /**
     * Runs in background thread
     */
    public abstract <T> Future<T> flatMap(Function<V,Future<T>> fn);

    public abstract boolean isFailed();

    /**
     * Returns the successful value if this future is completed. Will throw
     * if it is not done, or has failed.
     * @throws IllegalStateException
     */
    public abstract V getValue() throws IllegalStateException;
    public abstract Throwable getError();

    public Future<V> recover(final Callable<V> recovery) {
        final Promise<V> promise = Promise.create();
        Callback<Try<V>> cb = new Callback<Try<V>>() {
           @Override
           public void onCallback(Try<V> value) {
               if (value instanceof Try.Failure) {
                   Throwable t = ((Try.Failure) value).error;
                   Future<V> recoverFuture = Future.create(recovery);
                   recoverFuture.onComplete(promise.makeOnComplete(t));
               } else {
                   promise.complete(value);
               }
           }
        };
        if (this instanceof Promise) {
            ((Promise<V>)this).orElse(cb);
        } else {
            onComplete(cb);
        }
        return promise;
    }

    public static <T> Future<T> create(final Callable<T> callable) {
        final Promise<T> promise = Promise.create();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    promise.success(callable.call());
                } catch (Throwable t) {
                    promise.failure(t);
                }
            }
        };
        AsyncTask.THREAD_POOL_EXECUTOR.execute(r);
        return promise;
    }

    public static Future<Void> join(Future<?>... futures) {
        return join(Arrays.asList(futures));
    }

    @SuppressWarnings("unchecked")
    /**
     * The result object of this Future is just some arbitrary object without
     * meaning. Just use onSuccess/onFailure to determine whether or not it
     * completed execution.
     *
     * Creates a new Future that waits for all of the list's futures to
     * complete.
     */
    public static Future<Void> join(List<Future<?>> list) {
        // so we can cast to a bogus type and get around the typechecks
        List l = list;
        List<Future<Object>> l2 = (List<Future<Object>>) l;

        return sequence(l2).map(new Function<List<Object>, Void>() {
            @Override
            public Void apply(List<Object> input) {
                return null;
            }
        });
    }
    /**
     * Takes a List[Future[T]] and converts it into a single Future[List[T]]
     */
    @SuppressWarnings("unchecked")
    public static <T> Future<List<T>> sequence(List<Future<T>> list) {
        final Promise<List<T>> promise = Promise.create();
        final int count = list.size();
        final int[] completed = {0};
        final Object[] items = new Object[count];
        final Object lock = new Object();

        for (int i = 0; i < count; i++) {
            final int j = i;
            Future<T> fut = list.get(i);
            fut.onComplete(new Callback<Try<T>>() {
                @Override
                public void onCallback(Try<T> value) {
                    if (value instanceof Try.Success) {
                        Try.Success<T> success = (Try.Success<T>) value;
                        synchronized(lock) {
                            items[j] = success.value;
                            completed[0]++;
                            if (count == completed[0]) {
                                promise.success((List<T>) Arrays.asList(items));
                            }
                        }
                    } else {
                        Try.Failure failure = (Try.Failure) value;
                        promise.failure(failure.getError());
                    }
                }
            });
        }
        return promise;
    }

    public static abstract class Try<V> {
        public abstract <T> Try<T> map(Function<V,T> fn);
        @SuppressWarnings("unchecked")
        public static <T> Try<T> create(T value) {
            return new Success(value);
        }

        @SuppressWarnings("unchecked")
        public static <T> Try<T> create(T value, Throwable error) {
            // no nullcheck because we want to support Future[Void]
            return error != null ? new Failure(error) : new Success(value);
        }

        @SuppressWarnings("unchecked")
        public static class Success<V> extends Try<V> {
            private final V value;
            private Success(V value) {
                this.value = value;
            }
            public <T> Success<T> map(Function<V,T> fn) {
                return new Success(fn.apply(value));
            }

            public V getValue() {
                return value;
            }

            @Override
            public String toString() {
                return "Success: " + value;
            }
        }
        public static class Failure<V> extends Try<V> {
            private Failure(Throwable error) {
                Preconditions.checkNotNull(error, "error may not be null");
                this.error = error;
            }
            private final Throwable error;
            @SuppressWarnings("unchecked")
            public <T> Failure<T> map(Function<V,T> fn) {
                return (Failure<T>) this;
            }

            public Throwable getError() {
                return error;
            }
            @Override
            public String toString() {
                return "Failure: " + error;
            }
        }
    }


    public interface Callback<V> {
        public void onCallback(V value);
    }
}
