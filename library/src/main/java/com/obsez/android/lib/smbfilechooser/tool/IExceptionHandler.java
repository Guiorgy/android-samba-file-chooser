package com.obsez.android.lib.smbfilechooser.tool;

import androidx.annotation.NonNull;

public interface IExceptionHandler {
    void handleException(@NonNull final Throwable exception);

    void handleException(@NonNull final Throwable exception, final int id);

    final class ExceptionId {
        public static final int UNDEFINED = -1;
        public static final int FAILED_TO_LOAD_FILES = 1;
        public static final int FAILED_TO_FIND_ROOT_DIR = 2;
        public static final int EXECUTOR_INTERUPTED = 3;
    }

    @FunctionalInterface
    interface ExceptionHandler {
        boolean handle(@NonNull final Throwable exception, final int id);
    }
}
