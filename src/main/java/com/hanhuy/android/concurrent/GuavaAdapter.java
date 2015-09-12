package com.hanhuy.android.concurrent;

/**
 * @author pfnguyen
 */
public class GuavaAdapter {
    private GuavaAdapter() {}

    public static <F,T> com.google.common.base.Function<F,T> asGuavaFunction(
            final Future.Function<F,T> f) {
        return new com.google.common.base.Function<F,T>() {
            @Override
            public T apply(F input) {
                return f.apply(input);
            }
        };
    }
    public static <F,T> Future.Function<F,T> asFutureFunction(
            final com.google.common.base.Function<F,T> f) {
        return new Future.Function<F,T>() {
            @Override
            public T apply(F input) {
                return f.apply(input);
            }
        };
    }
}
