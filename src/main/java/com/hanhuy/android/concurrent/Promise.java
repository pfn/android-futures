package com.hanhuy.android.concurrent;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hanhuy.android.concurrent.Future.Callback;

/**
 * @author pfnguyen
 */
public class Promise<V> extends Future<V> {
    private boolean noCompletionPromises;
    private Promise<Void> completeAsyncPromise;
    private Promise<Void> completeUiPromise;
    private Callback<Try<V>> orElseCallback;
    private static final String TAG = "Promise";
    private List<Callback<Try<V>>> onCompletionQueue = Lists.newArrayList();
    private List<Callback<V>> onSuccessQueue = Lists.newCopyOnWriteArrayList();
    private List<Callback<Throwable>> onFailureQueue = Lists.newArrayList();
    private V value;
    private Throwable error;
    private boolean canceled;
    private boolean done;
    private List<Pair<Promise<Object>,Function<Object,Object>>> mapped =
            Lists.newArrayList();
    private List<Pair<Promise<Object>,Function<Object,Future<Object>>>> flatMapped =
            Lists.newArrayList();

    private final static Handler handler = new Handler(Looper.getMainLooper());

    private void v(String msg) {
        Log.v(TAG, this + ": " + msg);
    }
    private static <T> Promise<T> createInternal() {
        Promise<T> p = create();
        p.noCompletionPromises = true;
        return p;
    }

    public static <T> Promise<T> create() {
        return new Promise<T>();
    }
    public static <T> Promise<T> create(T value) {
        Promise<T> promise = Promise.create();
        promise.success(value);
        return promise;
    }
    public void success(V value) {
        if (!isDone())
            complete(Try.create(value));
    }

    public void failure(Throwable error) {
        Log.v(TAG, "Promise not kept: " + error, error);
        if (!isDone())
            complete(Try.<V>create(null, error));
    }

    private synchronized void completeAsync(final Try<V> result) {
        if (orElseCallback != null) {
            orElseCallback.onCallback(result);
            orElseCallback = null;
        }

        for (Callback<Try<V>> cb : onCompletionQueue)
            cb.onCallback(result);
        onCompletionQueue.clear();
        onCompletionQueue = ImmutableList.of();

        if (result instanceof Try.Success) {
            for (Pair<Promise<Object>,Function<Object,Object>> pair : mapped) {
                v("Executing success mapping: " + pair.first);
                pair.first.success(pair.second.apply(value));
            }
            for (final Pair<Promise<Object>,Function<Object,Future<Object>>> pair : flatMapped) {
                pair.second.apply(value).onComplete(new Callback<Try<Object>>() {
                    @Override
                    public void onCallback(Try<Object> result) {
                        pair.first.complete(result);
                    }
                });
            }
        } else if (result instanceof Try.Failure) {
            for (Pair<Promise<Object>,Function<Object,Object>> pair : mapped) {
                v("Executing failure mapping: " + pair.first);
                pair.first.failure(error);
            }
            for (Pair<Promise<Object>,Function<Object,Future<Object>>> pair : flatMapped) {
                pair.first.failure(error);
            }
        }

        mapped.clear();
        flatMapped.clear();
        mapped = ImmutableList.of();
        flatMapped = ImmutableList.of();
        if (completeAsyncPromise != null)
            completeAsyncPromise.complete(Try.<Void>create(null, null));
    }

    /**
     * Sends the result to the UI thread callbacks, if any.
     */
    protected synchronized void complete(final Try<V> result) {

        if (!noCompletionPromises) {
            completeAsyncPromise = Promise.createInternal();
            completeUiPromise = Promise.createInternal();
        }
        if (result instanceof Try.Success) {
            Try.Success<V> success = (Try.Success<V>) result;
            value = success.getValue();
        } else if (result instanceof Try.Failure)  {
            Try.Failure<V> failure = (Try.Failure<V>) result;
            error = failure.getError();
        } else {
            throw new IllegalStateException("should never happen");
        }
        done = true;
        Promise.this.notifyAll();

        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            completeAsync(result);

            handler.post(new Runnable() {
                public void run() {
                    completeOnUi(result);
                }
            });
        } else {
            completeOnUi(result);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    completeAsync(result);
                }
            });
        }
    }

    private static Function<Void,Void> asFunction(final Runnable r) {
        return new Function<Void, Void>() {
            @Override
            public Void apply(Void input) {
                r.run();
                return null;
            }
        };
    }

    private static <V> Callback<Try<V>> asCallback(final Runnable r) {
        return new Callback<Try<V>>() {
            @Override
            public void onCallback(Try<V> input) {
                r.run();
            }
        };
    }

    // this runs on UI thread
    private synchronized void completeOnUi(Try<V> result) {
        if (result instanceof Try.Success) {
            for (Callback<V> cb : onSuccessQueue) {
                cb.onCallback(value);
            }
        } else if (result instanceof Try.Failure) {
            for (Callback<Throwable> cb : onFailureQueue)
                cb.onCallback(error);
        }
        onFailureQueue.clear();
        onSuccessQueue.clear();
        onFailureQueue = ImmutableList.of();
        onSuccessQueue = ImmutableList.of();
        if (completeUiPromise != null)
            completeUiPromise.complete(Try.<Void>create(null, null));
    }

    synchronized void orElse(Callback<Try<V>> fn) {
        if (done) {
            fn.onCallback(Try.create(value, error));
        } else {
            orElseCallback = fn;
        }
    }

    @Override
    public synchronized void onComplete(Callback<Try<V>> fn) {
        if (done) {
            fn.onCallback(Try.create(value, error));
        } else {
            onCompletionQueue.add(fn);
        }
    }

    @Override
    public synchronized void onSuccess(Callback<V> fn) {
        if (done && error == null) {
            fn.onCallback(value);
        } else if (!done) {
            onSuccessQueue.add(fn);
        }
    }

    @Override
    public synchronized void onFailure(Callback<Throwable> fn) {
        if (done && error != null) {
            fn.onCallback(error);
        } else if (!done) {
            onFailureQueue.add(fn);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> Future<T> map(Function<V, T> fn) {
        Promise<T> promise = Promise.create();
        if (isDone()) {
            v("Executing instant map: " + promise);
            if (error != null)
                promise.failure(error);
            else
                promise.success(fn.apply(value));
        } else {
            // yeah, this needs to be unchecked...
            v("Queuing map: " + promise);
            mapped.add(new Pair(promise, fn));
        }
        return promise;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> Future<T> flatMap(Function<V, Future<T>> fn) {
        Promise<T> promise = Promise.create();
        if (isDone()) {
            if (error != null) {
                promise.failure(error);
            } else
                return fn.apply(value);
        } else
            flatMapped.add(new Pair(promise, fn));
        return promise;
    }

    @Override
    public synchronized boolean cancel(boolean b) {
        if (!done) {
            onCompletionQueue.clear();
            onSuccessQueue.clear();
            onFailureQueue.clear();
        }
        for (Pair<Promise<Object>,?> pair : mapped) {
            pair.first.cancel(true);
        }
        for (Pair<Promise<Object>,?> pair : flatMapped) {
            pair.first.cancel(true);
        }
        if (!done) {
            mapped.clear();
            flatMapped.clear();
        }

        if (!done)
            canceled = true;
        notifyAll();
        return !done;
    }

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isFailed() {
        return error != null;
    }

    @Override
    public synchronized V getValue() throws IllegalStateException {
        if (!isDone() || isFailed())
            throw new IllegalStateException("This future is not done");
        if (canceled)
            throw new IllegalStateException("This future has been canceled");
        return value;
    }

    @Override
    public synchronized Throwable getError() {
        if (!isDone() && !isFailed())
            throw new IllegalStateException("This future did not fail");
        return error;
    }

    /**
     * Generally, get() should not be used in favor of onSuccess and onFailure
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            while (!done && !canceled) {
                wait();
            }
        }
        if (error != null) throw new ExecutionException("Future failed", error);
        if (canceled) throw new ExecutionException("Future canceled",
                new RuntimeException());
        return value;
    }

    /**
     * Generally, get() should not be used in favor of onSuccess and onFailure
     */
    @Override
    public V get(long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (this) {
            if (!done && !canceled)
                wait(TimeUnit.MILLISECONDS.convert(l, timeUnit));
        }
        if (!done) throw new TimeoutException("Future timed out");
        if (error != null) throw new ExecutionException("Future failed", error);
        if (canceled) throw new ExecutionException("Future canceled",
                new RuntimeException());
        return value;
    }

    Callback<Try<V>> makeOnComplete(final Throwable error) {
        return new Callback<Try<V>>() {
            @Override
            public void onCallback(Try<V> v) {
                if (v instanceof Try.Failure) {
                    complete(Try.<V>create(null, error));
                } else
                    complete(v);
            }
        };
    }
}
