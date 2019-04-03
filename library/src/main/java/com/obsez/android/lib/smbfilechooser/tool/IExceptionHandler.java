package com.obsez.android.lib.smbfilechooser.tool;

import androidx.annotation.NonNull;

public interface IExceptionHandler {
    final class ExceptionId {
        public static final int UNDEFINED = -1;
        public static final int FAILED_TO_LOAD_FILES = 1;
        public static final int FAILED_TO_FIND_ROOT_DIR = 2;
        public static final int EXECUTOR_INTERRUPTED = 3;
        public static final int FAILED_TO_INITIALIZE = 4;
        @Deprecated
        public static final int TIMED_OUT = 5;
        public static final int ADAPTER_GETVIEW = 6;
    }

    @FunctionalInterface
    interface ExceptionHandler {
        /**
         * @param exception the exception to be handled
         * @param id        an id to give further hint to the thrown exception
         */
        boolean handle(@NonNull final Throwable exception, final int id);
    }

    void handleException(@NonNull final Throwable exception);

    void handleException(@NonNull final Throwable exception, final int id);
}
