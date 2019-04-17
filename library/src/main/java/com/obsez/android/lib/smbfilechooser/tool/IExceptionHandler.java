package com.obsez.android.lib.smbfilechooser.tool;

import androidx.annotation.NonNull;

public interface IExceptionHandler {
    @SuppressWarnings("WeakerAccess")
    final class ExceptionId {
        public static final int UNDEFINED = -1;
        public static final int FAILED_TO_LOAD_FILES = 1;
        public static final int FAILED_TO_FIND_ROOT_DIR = 2;
        public static final int EXECUTOR_INTERRUPTED = 3;
        public static final int FAILED_TO_INITIALIZE = 4;
        public static final int ADAPTER_GET_VIEW = 5;
    }

    @FunctionalInterface
    interface ExceptionHandler {
        /**
         * @param exception the exception to be handled
         * @param id        an id to give further hint to the thrown exception
         *                  see {@link ExceptionId}
         * @return true to attempt to terminate.
         * false to attempt to ignore exception and continue
         */
        boolean handle(@NonNull final Throwable exception, final int id);
    }

    /**
     * @param exception the exception to be handled
     */
    void handleException(@NonNull final Throwable exception);

    /**
     * @param exception the exception to be handled
     * @param id        an id to give further hint to the thrown exception
     *                  see {@link ExceptionId}
     */
    void handleException(@NonNull final Throwable exception, final int id);
}
