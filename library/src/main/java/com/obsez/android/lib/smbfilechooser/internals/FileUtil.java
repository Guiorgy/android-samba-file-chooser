package com.obsez.android.lib.smbfilechooser.internals;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.ContextThemeWrapper;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jcifs.smb.SmbFile;

/**
 * Created by coco on 6/7/15. Edited by Guiorgy on 10/09/18.
 * Copyright 2015-2019 Hedzr Yeh
 * Modified 2018-2019 Guiorgy
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@SuppressWarnings({"unused", "WeakerAccess"})
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

    private static final class Constants {
        final static int BYTES_IN_KILOBYTES = 1024;
        final static String BYTES = " B";
        final static String KILOBYTES = " KB";
        final static String MEGABYTES = " MB";
        final static String GIGABYTES = " GB";
    }

    @NonNull
    public static String getReadableFileSize(long size) {
        float fileSize = 0;
        String suffix = Constants.KILOBYTES;

        if (size < Constants.BYTES_IN_KILOBYTES) {
            fileSize = size;
            suffix = Constants.BYTES;
        } else {
            fileSize = (float) size / Constants.BYTES_IN_KILOBYTES;
            if (fileSize >= Constants.BYTES_IN_KILOBYTES) {
                fileSize = fileSize / Constants.BYTES_IN_KILOBYTES;
                if (fileSize >= Constants.BYTES_IN_KILOBYTES) {
                    fileSize = fileSize / Constants.BYTES_IN_KILOBYTES;
                    suffix = Constants.GIGABYTES;
                } else {
                    suffix = Constants.MEGABYTES;
                }
            }
        }
        return String.valueOf(new DecimalFormat("###.#").format(fileSize) + suffix);
    }

    public static String getStoragePath(final Context context, final boolean isRemovable) {
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = storageManager.getClass().getMethod("getVolumeList");
            //noinspection JavaReflectionMemberAccess
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovableMtd = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(storageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovableMtd.invoke(storageVolumeElement);
                if (isRemovable == removable) {
                    return path;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static long readSDCard(Context context, Boolean isRemovable) {
        return readSDCard(context, isRemovable, false);
    }

    public static long readSDCard(Context context, Boolean isRemovable, Boolean freeOrTotal) {
        if (getStoragePath(context, isRemovable) != null) {
            StatFs sf = new StatFs(getStoragePath(context, isRemovable));
            long blockSize;
            long blockCount;
            long availCount;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // 文件存储时每一个存储块的大小为4KB
                //  (google translate: The size of each memory block is 4KB when the file is stored.)
                blockSize = sf.getBlockSizeLong();
                // 存储区域的存储块的总个数
                //  (google translate: The total number of storage blocks in the storage area)
                blockCount = sf.getBlockCountLong();
                // 存储区域中可用的存储块的个数（剩余的存储大小）
                //  (google translate: Number of memory blocks available in the storage area (remaining storage size))
                availCount = sf.getFreeBlocksLong();
            } else {
                blockSize = sf.getBlockSize();
                blockCount = sf.getBlockCount();
                availCount = sf.getFreeBlocks();
            }
            return (freeOrTotal ? availCount : blockCount) * blockSize;
        }
        return -1;
    }

    @SuppressWarnings("unused")
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
        private @Nullable
        Activity activity;
        private @NonNull
        Context context;

        public LightContextWrapper(@NonNull final Context context) {
            this.context = context;
            if (context instanceof Activity) {
                this.activity = (Activity) context;
            }
        }

        @NonNull
        public Context getBaseContext() {
            return context;
        }

        @Nullable
        public Activity getActivity() {
            return activity;
        }

        @NonNull
        public String getPackageName() {
            return context.getPackageName();
        }

        @NonNull
        public Resources getResources() {
            return context.getResources();
        }

        @NonNull
        public Context getThemeWrappedContext(@StyleRes final int themeResId) {
            return new ContextThemeWrapper(this.context, themeResId);
        }

        public void themeWrapContext(@StyleRes final int themeResId) {
            this.context = new ContextThemeWrapper(this.context, themeResId);
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
