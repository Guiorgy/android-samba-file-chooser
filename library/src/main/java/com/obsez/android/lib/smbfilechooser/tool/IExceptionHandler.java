package com.obsez.android.lib.smbfilechooser.tool;

import android.support.annotation.NonNull;

public interface IExceptionHandler{
    boolean handleException(@NonNull final Throwable exception);

    boolean handleException(@NonNull final Throwable exception, final int id);

    static final class ExceptionId{
        public static final int UNDEFINED = -1;
    }

    @FunctionalInterface
    interface ExceptionHandler{
        boolean handle(@NonNull final Throwable exception, final int id);
    }
}
