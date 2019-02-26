package com.obsez.android.lib.smbfilechooser.internals;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.io.File;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import jcifs.smb.SmbFile;

/**
 * Created by coco on 6/7/15. Edited by Guiorgy on 10/09/18.
 * Copyright 2015-2018 Hedzr Yeh
 * Modified 2018 Guiorgy
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class FileUtil {

    @NonNull
    public static String getExtension(@Nullable final File file) {
        if (file == null) {
            return "";
        }

        int dot = file.getName().lastIndexOf(".");
        if (dot >= 0) {
            return file.getName().substring(dot);
        } else {
            // No extension.
            return "";
        }
    }

    @NonNull
    public static String getExtension(@Nullable final SmbFile file) {
        if (file == null) {
            return "";
        }

        int dot = file.getName().lastIndexOf(".");
        if (dot >= 0) {
            return file.getName().substring(dot);
        } else {
            // No extension.
            return "";
        }
    }

    @NonNull
    public static String getExtensionWithoutDot(@NonNull final File file) {
        String ext = getExtension(file);
        if (ext.length() == 0) {
            return ext;
        }
        return ext.substring(1);
    }

    @NonNull
    public static String getExtensionWithoutDot(@NonNull final SmbFile file) {
        String ext = getExtension(file);
        if (ext.length() == 0) {
            return ext;
        }
        return ext.substring(1);
    }

    @NonNull
    public static String getReadableFileSize(final long size) {
        final int BYTES_IN_KILOBYTES = 1024;
        final DecimalFormat dec = new DecimalFormat("###.#");
        final String KILOBYTES = " KB";
        final String MEGABYTES = " MB";
        final String GIGABYTES = " GB";
        double fileSize = 0;
        String suffix = KILOBYTES;

        if (size > BYTES_IN_KILOBYTES) {
            fileSize = (double) size / BYTES_IN_KILOBYTES;
            if (fileSize > BYTES_IN_KILOBYTES) {
                fileSize = fileSize / BYTES_IN_KILOBYTES;
                if (fileSize > BYTES_IN_KILOBYTES) {
                    fileSize = fileSize / BYTES_IN_KILOBYTES;
                    suffix = GIGABYTES;
                } else {
                    suffix = MEGABYTES;
                }
            }
        }
        return String.valueOf(dec.format(fileSize) + suffix);
    }


    public static class NewFolderFilter implements InputFilter {
        private final int maxLength;
        private final Pattern pattern;

        public NewFolderFilter() {
            this(255, "^[^/<>|\\\\:&;#\n\r\t?*~\0-\37]*$");
        }

        public NewFolderFilter(int maxLength) {
            this(maxLength, "^[^/<>|\\\\:&;#\n\r\t?*~\0-\37]*$");
        }

        public NewFolderFilter(String pattern) {
            this(255, pattern);
        }

        public NewFolderFilter(int maxLength, String pattern) {
            this.maxLength = maxLength;
            this.pattern = Pattern.compile(pattern);
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            Matcher matcher = pattern.matcher(source);
            if (!matcher.matches()) {
                return source instanceof SpannableStringBuilder ? dest.subSequence(dstart, dend) : "";
            }

            int keep = maxLength - (dest.length() - (dend - dstart));
            if (keep <= 0) {
                return "";
            } else if (keep >= end - start) {
                return null; // keep original
            } else {
                keep += start;
                if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                    --keep;
                    if (keep == start) {
                        return "";
                    }
                }
                return source.subSequence(start, keep).toString();
            }
        }
    }

    public static abstract class LightContextWrapper {
        final private Context context;

        public LightContextWrapper(@NonNull final Context context) {
            this.context = context;
        }

        @NonNull
        public Context getBaseContext() {
            return context;
        }

        @NonNull
        public Resources getResources() {
            return context.getResources();
        }

        public void runOnUiThread(Runnable runnable) {
            if (!Thread.currentThread().equals(Looper.getMainLooper().getThread())) {
                Handler mainHandler = new Handler(context.getMainLooper());
                mainHandler.post(runnable);
            } else {
                runnable.run();
            }
        }
    }
}
