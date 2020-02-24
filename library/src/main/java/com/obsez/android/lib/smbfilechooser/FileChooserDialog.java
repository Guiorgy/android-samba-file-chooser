package com.obsez.android.lib.smbfilechooser;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.obsez.android.lib.smbfilechooser.internals.ExtFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.FileUtil;
import com.obsez.android.lib.smbfilechooser.internals.RegexFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;
import com.obsez.android.lib.smbfilechooser.permissions.PermissionsUtil;
import com.obsez.android.lib.smbfilechooser.tool.DirAdapter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Pattern;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
import static com.obsez.android.lib.smbfilechooser.internals.FileUtil.LightContextWrapper;
import static com.obsez.android.lib.smbfilechooser.internals.FileUtil.NewFolderFilter;
import static com.obsez.android.lib.smbfilechooser.internals.UiUtil.getListYScroll;

/**
 * Created by coco on 6/7/15. Edited by Guiorgy on 10/09/18.
 * <p>
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
 *
 * @deprecated FileChooserDialog uses java.io.File api. Begining from Android R, it has been restrictet, thus FileChooserDialog is only supported until Android Q!
 */
@Deprecated
@SuppressWarnings({"SpellCheckingInspection", "unused", "UnusedReturnValue", "deprecation"})
public class FileChooserDialog extends LightContextWrapper implements DialogInterface.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, AdapterView.OnItemSelectedListener, DialogInterface.OnKeyListener {
    @FunctionalInterface
    public interface OnChosenListener {
        void onChoosePath(@NonNull final String dir, @NonNull final File dirFile);
    }

    @FunctionalInterface
    public interface OnSelectedListener {
        void onSelectFiles(@NonNull final List<File> files);
    }

    private FileChooserDialog(@NonNull final Context context, final boolean ignoreApi) {
        super(context);
        this.ignoreApi = ignoreApi;
    }

    private final boolean ignoreApi;

    /***
     * @deprecated FileChooserDialog uses java.io.File api. Begining from Android R, it has been restrictet, thus FileChooserDialog is only supported until Android Q!
     */
    @Deprecated
    @NonNull
    public static FileChooserDialog newDialog(@NonNull final Context context) {
        return newDialog(context, false);
    }

    /***
     * @deprecated FileChooserDialog uses java.io.File api. Begining from Android R, it has been restrictet, thus FileChooserDialog is only supported until Android Q!
     */
    @Deprecated
    @NonNull
    public static FileChooserDialog newDialog(@NonNull final Context context, final boolean ignoreApi) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            android.util.Log.w("FileChooserDialog", "FileChooserDialog uses java.io.File api. Begining from Android R, it has been restrictet, thus FileChooserDialog is only supported until Android Q!");
        return new FileChooserDialog(context, ignoreApi);
    }

    @NonNull
    public FileChooserDialog setFilter(@NonNull final FileFilter ff) {
        setFilter(false, false, (String[]) null);
        this._fileFilter = ff;
        return this;
    }

    @NonNull
    public FileChooserDialog setFilter(final boolean dirOnly, final boolean allowHidden, @NonNull final FileFilter ff) {
        setFilter(dirOnly, allowHidden, (String[]) null);
        this._fileFilter = ff;
        return this;
    }

    @NonNull
    public FileChooserDialog setFilter(final boolean allowHidden, @Nullable final String... suffixes) {
        return setFilter(false, allowHidden, suffixes);
    }

    @NonNull
    public FileChooserDialog setFilter(final boolean dirOnly, final boolean allowHidden, @Nullable final String... suffixes) {
        this._dirOnly = dirOnly;
        if (suffixes == null || suffixes.length == 0) {
            this._fileFilter = dirOnly ?
                file -> file.isDirectory() && (!file.isHidden() || allowHidden) : file -> !file.isHidden() || allowHidden;
        } else {
            this._fileFilter = new ExtFileFilter(_dirOnly, allowHidden, suffixes);
        }
        return this;
    }

    @NonNull
    public FileChooserDialog setFilterRegex(final boolean dirOnly, final boolean allowHidden, @NonNull final String pattern, final int flags) {
        this._dirOnly = dirOnly;
        this._fileFilter = new RegexFileFilter(_dirOnly, allowHidden, pattern, flags);
        return this;
    }

    @NonNull
    public FileChooserDialog setFilterRegex(final boolean dirOnly, final boolean allowHidden, @NonNull final String pattern) {
        this._dirOnly = dirOnly;
        this._fileFilter = new RegexFileFilter(_dirOnly, allowHidden, pattern, Pattern.CASE_INSENSITIVE);
        return this;
    }

    @NonNull
    public FileChooserDialog setStartFile(@Nullable final String startFile) {
        if (startFile != null) {
            _currentDir = new File(startFile);
        } else {
            _currentDir = new File(FileUtil.getStoragePath(getBaseContext(), false));
        }

        if (!_currentDir.isDirectory()) {
            _currentDir = _currentDir.getParentFile();
        }

        if (_currentDir == null) {
            _currentDir = new File(FileUtil.getStoragePath(getBaseContext(), false));
        }

        return this;
    }

    @NonNull
    public FileChooserDialog cancelOnTouchOutside(final boolean cancelOnTouchOutside) {
        this._cancelOnTouchOutside = cancelOnTouchOutside;
        return this;
    }

    @NonNull
    public FileChooserDialog setOnChosenListener(@NonNull final OnChosenListener listener) {
        this._onChosenListener = listener;
        return this;
    }

    @NonNull
    public FileChooserDialog setOnSelectedListener(@NonNull final OnSelectedListener listener) {
        this._onSelectedListener = listener;
        return this;
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public FileChooserDialog setOnDismissListener(@NonNull final DialogInterface.OnDismissListener listener) {
        this._onDismissListener = listener;
        return this;
    }

    /**
     * called every time {@link KeyEvent#KEYCODE_BACK} is caught,
     * and current directory is not the root of Primary/SdCard storage.
     */
    @NonNull
    public FileChooserDialog setOnBackPressedListener(@NonNull final OnBackPressedListener listener) {
        this._onBackPressed = listener;
        return this;
    }

    /**
     * called if {@link KeyEvent#KEYCODE_BACK} is caught,
     * and current directory is the root of Primary/SdCard storage.
     */
    @NonNull
    public FileChooserDialog setOnLastBackPressedListener(@NonNull final OnBackPressedListener listener) {
        this._onLastBackPressed = listener;
        return this;
    }

    @NonNull
    public FileChooserDialog setResources(@Nullable @StringRes final Integer titleRes, @Nullable @StringRes final Integer okRes, @Nullable @StringRes final Integer cancelRes) {
        this._titleRes = titleRes;
        this._okRes = okRes;
        this._negativeRes = cancelRes;
        return this;
    }

    @NonNull
    public FileChooserDialog setResources(@Nullable final String title, @Nullable final String ok, @Nullable final String cancel) {
        if (title != null) {
            this._title = title;
        }
        if (ok != null) {
            this._ok = ok;
        }
        if (cancel != null) {
            this._negative = cancel;
        }
        return this;
    }

    @NonNull
    public FileChooserDialog enableOptions(final boolean enableOptions) {
        this._enableOptions = enableOptions;
        return this;
    }

    @NonNull
    public FileChooserDialog setOptionResources(@Nullable @StringRes final Integer createDirRes, @Nullable @StringRes final Integer deleteRes, @Nullable @StringRes final Integer newFolderCancelRes, @Nullable @StringRes final Integer newFolderOkRes) {
        this._createDirRes = createDirRes;
        this._deleteRes = deleteRes;
        this._newFolderCancelRes = newFolderCancelRes;
        this._newFolderOkRes = newFolderOkRes;
        return this;
    }

    @NonNull
    public FileChooserDialog setOptionResources(@Nullable final String createDir, @Nullable final String delete, @Nullable final String newFolderCancel, @Nullable final String newFolderOk) {
        if (createDir != null) {
            this._createDir = createDir;
        }
        if (delete != null) {
            this._delete = delete;
        }
        if (newFolderCancel != null) {
            this._newFolderCancel = newFolderCancel;
        }
        if (newFolderOk != null) {
            this._newFolderOk = newFolderOk;
        }
        return this;
    }

    @NonNull
    public FileChooserDialog setOptionIcons(@Nullable @DrawableRes final Integer optionsIconRes, @Nullable @DrawableRes final Integer createDirIconRes, @Nullable @DrawableRes final Integer deleteRes) {
        this._optionsIconRes = optionsIconRes;
        this._createDirIconRes = createDirIconRes;
        this._deleteIconRes = deleteRes;
        return this;
    }

    public FileChooserDialog setOptionIcons(@Nullable Drawable optionsIcon, @Nullable Drawable createDirIcon, @Nullable Drawable deleteIcon) {
        this._optionsIcon = optionsIcon;
        this._createDirIcon = createDirIcon;
        this._deleteIcon = deleteIcon;
        return this;
    }

    @NonNull
    public FileChooserDialog setIcon(@Nullable @DrawableRes final Integer iconId) {
        this._iconRes = iconId;
        return this;
    }

    @NonNull
    public FileChooserDialog setIcon(@Nullable Drawable icon) {
        this._icon = icon;
        return this;
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public FileChooserDialog setLayoutView(@Nullable @LayoutRes final Integer layoutResId) {
        this._layoutRes = layoutResId;
        return this;
    }

    @NonNull
    public FileChooserDialog setDateFormat() {
        return this.setDateFormat("yyyy/MM/dd HH:mm:ss");
    }

    @NonNull
    public FileChooserDialog setDateFormat(@NonNull final String format) {
        this._dateFormat = format;
        return this;
    }

    @NonNull
    public FileChooserDialog setNegativeButtonListener(@NonNull final DialogInterface.OnClickListener listener) {
        this._negativeListener = listener;
        return this;
    }

    /**
     * it's NOT recommended to use the `setOnCancelListener`, replace with `setNegativeButtonListener` pls.
     *
     * @deprecated will be removed at v1.2
     */
    @NonNull
    public FileChooserDialog setOnCancelListener(@NonNull final DialogInterface.OnCancelListener listener) {
        this._onCancelListener = listener;
        return this;
    }

    @NonNull
    public FileChooserDialog setFileIcons(final boolean tryResolveFileTypeAndIcon, @Nullable final Drawable fileIcon, @Nullable final Drawable folderIcon) {
        this._adapterSetter = adapter -> {
            if (fileIcon != null)
                adapter.setDefaultFileIcon(fileIcon);
            if (folderIcon != null)
                adapter.setDefaultFolderIcon(folderIcon);
            adapter.setResolveFileType(tryResolveFileTypeAndIcon);
        };
        return this;
    }

    @NonNull
    public FileChooserDialog setFileIconsRes(final boolean tryResolveFileTypeAndIcon, @Nullable final Integer fileIcon, @Nullable final Integer folderIcon) {
        this._adapterSetter = adapter -> {
            if (fileIcon != null) {
                adapter.setDefaultFileIcon(ContextCompat.getDrawable(FileChooserDialog.this.getBaseContext(), fileIcon));
            }
            if (folderIcon != null) {
                adapter.setDefaultFolderIcon(
                    ContextCompat.getDrawable(FileChooserDialog.this.getBaseContext(), folderIcon));
            }
            adapter.setResolveFileType(tryResolveFileTypeAndIcon);
        };
        return this;
    }

    /**
     * @param setter you can customize the folder navi-adapter set `setter`
     * @return this
     */
    @NonNull
    public FileChooserDialog setAdapterSetter(@NonNull final AdapterSetter setter) {
        this._adapterSetter = setter;
        return this;
    }

    /**
     * @param cb give a hook at navigating up to a directory
     * @return this
     */
    @NonNull
    public FileChooserDialog setNavigateUpTo(@NonNull final CanNavigateUp cb) {
        this._folderNavUpCB = cb;
        return this;
    }

    /**
     * @param cb give a hook at navigating to a child directory
     * @return this
     */
    @NonNull
    public FileChooserDialog setNavigateTo(@NonNull final CanNavigateTo cb) {
        this._folderNavToCB = cb;
        return this;
    }

    @NonNull
    public FileChooserDialog disableTitle(final boolean disable) {
        this._disableTitle = disable;
        return this;
    }

    @NonNull
    public FileChooserDialog displayPath(final boolean display) {
        this._displayPath = display;
        return this;
    }

    @NonNull
    public FileChooserDialog customizePathView(CustomizePathView callback) {
        _pathViewCallback = callback;
        return this;
    }

    @NonNull
    public FileChooserDialog enableMultiple(final boolean enableMultiple, final boolean allowSelectMultipleFolders) {
        this._enableMultiple = enableMultiple;
        this._allowSelectDir = allowSelectMultipleFolders;
        return this;
    }

    @NonNull
    public FileChooserDialog setNewFolderFilter(@NonNull final NewFolderFilter filter) {
        this._newFolderFilter = filter;
        return this;
    }

    @NonNull
    public FileChooserDialog enableDpad(final boolean enableDpad) {
        this._enableDpad = enableDpad;
        return this;
    }

    @NonNull
    public FileChooserDialog setTheme(@StyleRes final int themeResId) {
        this._themeResId = themeResId;
        return this;
    }

    @NonNull
    public FileChooserDialog build() {
        if (_currentDir == null) {
            _currentDir = new File(FileUtil.getStoragePath(getBaseContext(), false));
        }

        if (this._themeResId == null) {
            TypedValue typedValue = new TypedValue();
            if (!getBaseContext().getTheme().resolveAttribute(R.attr.fileChooserStyle, typedValue, true))
                themeWrapContext(R.style.FileChooserStyle);
            else themeWrapContext(typedValue.resourceId);
        } else {
            themeWrapContext(this._themeResId);
        }

        TypedArray ta = getBaseContext().obtainStyledAttributes(R.styleable.FileChooser);
        final int dialogStyle = ta.getResourceId(R.styleable.FileChooser_fileChooserDialogStyle, R.style.FileChooserDialogStyle);
        final AlertDialog.Builder builder = new AlertDialog.Builder(getBaseContext(), dialogStyle);
        final  Context dialogContext = getThemeWrappedContext(dialogStyle);
        final int style = ta.getResourceId(R.styleable.FileChooser_fileChooserListItemStyle, R.style.FileChooserListItemStyle);
        ta.recycle();
        final Context context = getThemeWrappedContext(style);
        ta = context.obtainStyledAttributes(R.styleable.FileChooser);
        final int listview_item_selector = ta.getResourceId(R.styleable.FileChooser_fileListItemFocusedDrawable,
            R.drawable.listview_item_selector);
        ta.recycle();

        this._adapter = new DirAdapter(context, this._dateFormat);
        if (this._adapterSetter != null) this._adapterSetter.apply(this._adapter);

        refreshDirs();
        builder.setAdapter(this._adapter, this);

        if (!this._disableTitle) {
            if (this._titleRes == null) builder.setTitle(this._title);
            else builder.setTitle(this._titleRes);
        }

        if (_iconRes != null) {
            builder.setIcon(_iconRes);
        } else if (_icon != null) {
            builder.setIcon(_icon);
        }

        if (this._layoutRes != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setView(this._layoutRes);
            }
        }

        if (this._dirOnly || this._enableMultiple) {
            final DialogInterface.OnClickListener listener = (dialog, which) -> {
                if (FileChooserDialog.this._enableMultiple) {
                    if (FileChooserDialog.this._adapter.isAnySelected()) {
                        if (FileChooserDialog.this._adapter.isOneSelected()) {
                            if (FileChooserDialog.this._onChosenListener != null) {
                                final File selected = _adapter.getSelected().get(0);
                                FileChooserDialog.this._onChosenListener.onChoosePath(selected.getAbsolutePath(), selected);
                            }
                        } else {
                            if (FileChooserDialog.this._onSelectedListener != null) {
                                FileChooserDialog.this._onSelectedListener.onSelectFiles(_adapter.getSelected());
                            }
                        }
                    }
                } else if (FileChooserDialog.this._onChosenListener != null) {
                    FileChooserDialog.this._onChosenListener.onChoosePath(FileChooserDialog.this._currentDir.getAbsolutePath(), FileChooserDialog.this._currentDir);
                }

                FileChooserDialog.this._alertDialog.dismiss();
            };

            if (this._okRes == null) builder.setPositiveButton(this._ok, listener);
            else builder.setPositiveButton(this._okRes, listener);
        }

        final DialogInterface.OnClickListener listener = this._negativeListener != null ? this._negativeListener : (dialog, which) -> dialog.cancel();

        if (this._negativeRes == null) builder.setNegativeButton(this._negative, listener);
        else builder.setNegativeButton(this._negativeRes, listener);

        if (this._onCancelListener != null) {
            builder.setOnCancelListener(_onCancelListener);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (this._onDismissListener != null) {
                builder.setOnDismissListener(this._onDismissListener);
            }
        }

        builder.setOnKeyListener(this);

        this._alertDialog = builder.create();

        this._alertDialog.setCanceledOnTouchOutside(this._cancelOnTouchOutside);
        this._alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                _list.requestFocus();
                _btnNeutral = _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                _btnNegative = _alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                _btnPositive = _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);

                // ensure that the buttons have the right order
                ViewGroup buttonBar = (ViewGroup) _btnPositive.getParent();
                ViewGroup.LayoutParams btnParams = buttonBar.getLayoutParams();
                btnParams.width = MATCH_PARENT;
                buttonBar.setLayoutParams(btnParams);
                buttonBar.removeAllViews();
                btnParams = _btnNeutral.getLayoutParams();
                if (buttonBar instanceof LinearLayout) {
                    ((LinearLayout.LayoutParams) btnParams).weight = 1;
                    ((LinearLayout.LayoutParams) btnParams).width = 0;
                }
                if (_enableOptions) {
                    buttonBar.addView(_btnNeutral, 0, btnParams);
                } else {
                    buttonBar.addView(new Space(getBaseContext()), 0, btnParams);
                }
                buttonBar.addView(_btnNegative, 1, btnParams);
                buttonBar.addView(_btnPositive, 2, btnParams);

                if (_enableMultiple && !_dirOnly) {
                    _btnPositive.setVisibility(INVISIBLE);
                }

                if (FileChooserDialog.this._enableOptions) {
                    final int buttonColor = _btnNeutral.getCurrentTextColor();
                    final PorterDuffColorFilter filter = new PorterDuffColorFilter(buttonColor, PorterDuff.Mode.SRC_IN);

                    _btnNeutral.setText("");
                    _btnNeutral.setVisibility(VISIBLE);
                    _btnNeutral.setTextColor(buttonColor);
                    Drawable dots;
                    if (FileChooserDialog.this._optionsIconRes != null) {
                        dots = ContextCompat.getDrawable(getBaseContext(), FileChooserDialog.this._optionsIconRes);
                    } else if (FileChooserDialog.this._optionsIcon != null) {
                        dots = FileChooserDialog.this._optionsIcon;
                    } else
                        dots = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_menu_24dp);
                    if (dots != null) {
                        dots.setColorFilter(filter);
                        _btnNeutral.setCompoundDrawablesWithIntrinsicBounds(dots, null, null, null);
                    }

                    final class Integer {
                        int Int = 0;
                    }
                    final Integer scroll = new Integer();

                    FileChooserDialog.this._list.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        int oldHeight = oldBottom - oldTop;
                        if (v.getHeight() != oldHeight) {
                            int offset = oldHeight - v.getHeight();
                            int newScroll = getListYScroll(_list);
                            if (scroll.Int != newScroll) offset += scroll.Int - newScroll;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                FileChooserDialog.this._list.scrollListBy(offset);
                            } else {
                                FileChooserDialog.this._list.scrollBy(0, offset);
                            }
                        }
                    });

                    final Runnable showOptions = new Runnable() {
                        @Override
                        public void run() {
                            if (FileChooserDialog.this._options.getHeight() == 0) {
                                ViewTreeObserver viewTreeObserver = FileChooserDialog.this._options.getViewTreeObserver();
                                viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (FileChooserDialog.this._options.getHeight() <= 0) {
                                            return false;
                                        }
                                        viewTreeObserver.removeOnPreDrawListener(this);
                                        scroll.Int = getListYScroll(FileChooserDialog.this._list);
                                        if (FileChooserDialog.this._options.getParent() instanceof FrameLayout) {
                                            final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) FileChooserDialog.this._list.getLayoutParams();
                                            params.bottomMargin = FileChooserDialog.this._options.getHeight();
                                            FileChooserDialog.this._list.setLayoutParams(params);
                                        }
                                        FileChooserDialog.this._options.setVisibility(VISIBLE);
                                        FileChooserDialog.this._options.requestFocus();
                                        return true;
                                    }
                                });
                            } else {
                                scroll.Int = getListYScroll(FileChooserDialog.this._list);
                                if (FileChooserDialog.this._options.getParent() instanceof FrameLayout) {
                                    final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) FileChooserDialog.this._list.getLayoutParams();
                                    params.bottomMargin = FileChooserDialog.this._options.getHeight();
                                    FileChooserDialog.this._list.setLayoutParams(params);
                                }
                                FileChooserDialog.this._options.setVisibility(VISIBLE);
                                FileChooserDialog.this._options.requestFocus();
                            }
                        }
                    };
                    final Runnable hideOptions = () -> {
                        scroll.Int = getListYScroll(FileChooserDialog.this._list);
                        FileChooserDialog.this._options.setVisibility(GONE);
                        FileChooserDialog.this._options.clearFocus();
                        if (FileChooserDialog.this._options.getParent() instanceof FrameLayout) {
                            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) FileChooserDialog.this._list.getLayoutParams();
                            params.bottomMargin = 0;
                            FileChooserDialog.this._list.setLayoutParams(params);
                        }
                    };

                    _btnNeutral.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View v) {
                            if (FileChooserDialog.this._newFolderView != null && FileChooserDialog.this._newFolderView.getVisibility() == VISIBLE)
                                return;

                            if (FileChooserDialog.this._options == null) {
                                // region Draw options view. (this only happens the first time one clicks on options)
                                // Root view (FrameLayout) of the ListView in the AlertDialog.
                                int rootId = getResources().getIdentifier("contentPanel", "id", getPackageName());
                                ViewGroup tmpRoot = ((AlertDialog) dialog).findViewById(rootId);
                                // In case the root id was changed or not found.
                                if (tmpRoot == null) {
                                    rootId = getResources().getIdentifier("contentPanel", "id", "android");
                                    tmpRoot = ((AlertDialog) dialog).findViewById(rootId);
                                    if (tmpRoot == null) return;
                                }
                                final ViewGroup root = tmpRoot;

                                // Create options view.
                                final FrameLayout options = new FrameLayout(getBaseContext());
                                ViewGroup.MarginLayoutParams params;
                                if (root instanceof LinearLayout) {
                                    params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                    LinearLayout.LayoutParams param = ((LinearLayout.LayoutParams) FileChooserDialog.this._list.getLayoutParams());
                                    param.weight = 1;
                                    FileChooserDialog.this._list.setLayoutParams(param);
                                } else {
                                    params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, BOTTOM);
                                }
                                root.addView(options, params);
                                options.setFocusable(false);
                                if (root instanceof FrameLayout) {
                                    FileChooserDialog.this._list.bringToFront();
                                }
                                options.setOnClickListener(null);

                                // Create a button for the option to create a new directory/folder.
                                final Button createDir = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                if (FileChooserDialog.this._createDirRes == null)
                                    createDir.setText(FileChooserDialog.this._createDir);
                                else createDir.setText(FileChooserDialog.this._createDirRes);
                                createDir.setTextColor(buttonColor);
                                Drawable plus;
                                if (FileChooserDialog.this._createDirIconRes != null) {
                                    plus = ContextCompat.getDrawable(getBaseContext(), FileChooserDialog.this._createDirIconRes);
                                } else if (FileChooserDialog.this._createDirIcon != null) {
                                    plus = FileChooserDialog.this._createDirIcon;
                                } else
                                    plus = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_add_24dp);
                                if (plus != null) {
                                    plus.setColorFilter(filter);
                                    createDir.setCompoundDrawablesWithIntrinsicBounds(plus, null, null, null);
                                }

                                params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, START | CENTER_VERTICAL);
                                params.leftMargin = UiUtil.dip2px(10);
                                options.addView(createDir, params);

                                // Create a button for the option to delete a file.
                                final Button delete = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                if (FileChooserDialog.this._deleteRes == null)
                                    delete.setText(FileChooserDialog.this._delete);
                                else delete.setText(FileChooserDialog.this._deleteRes);
                                delete.setTextColor(buttonColor);
                                Drawable bin;
                                if (FileChooserDialog.this._deleteIconRes != null) {
                                    bin = ContextCompat.getDrawable(getBaseContext(), FileChooserDialog.this._deleteIconRes);
                                } else if (FileChooserDialog.this._deleteIcon != null) {
                                    bin = FileChooserDialog.this._deleteIcon;
                                } else
                                    bin = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_delete_24dp);
                                if (bin != null) {
                                    bin.setColorFilter(filter);
                                    delete.setCompoundDrawablesWithIntrinsicBounds(bin, null, null, null);
                                }

                                params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, END | CENTER_VERTICAL);
                                params.rightMargin = UiUtil.dip2px(10);
                                options.addView(delete, params);

                                FileChooserDialog.this._options = options;
                                showOptions.run();

                                // Event Listeners.
                                createDir.setOnClickListener(new View.OnClickListener() {
                                    private EditText input = null;

                                    @Override
                                    public void onClick(final View v1) {
                                        hideOptions.run();
                                        File newFolder = new File(FileChooserDialog.this._currentDir, "New folder");
                                        for (int i = 1; newFolder.exists(); i++)
                                            newFolder = new File(FileChooserDialog.this._currentDir, "New folder (" + i + ')');
                                        if (this.input != null)
                                            this.input.setText(newFolder.getName());

                                        if (FileChooserDialog.this._newFolderView == null) {
                                            // region Draw a view with input to create new folder. (this only happens the first time one clicks on New folder)
                                            TypedArray ta = getBaseContext().obtainStyledAttributes(R.styleable.FileChooser);
                                            int style = ta.getResourceId(R.styleable.FileChooser_fileChooserNewFolderStyle, R.style.FileChooserNewFolderStyle);
                                            final Context context = getThemeWrappedContext(style);
                                            ta.recycle();
                                            ta = context.obtainStyledAttributes(R.styleable.FileChooser);

                                            try {
                                                //noinspection ConstantConditions
                                                ((AlertDialog) dialog).getWindow().clearFlags(FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM);
                                                //noinspection ConstantConditions
                                                ((AlertDialog) dialog).getWindow().setSoftInputMode(SOFT_INPUT_STATE_VISIBLE
                                                    | ta.getInt(R.styleable.FileChooser_fileChooserNewFolderSoftInputMode, 0x30));
                                            } catch (NullPointerException e) {
                                                e.printStackTrace();
                                            }

                                            // A semitransparent background overlay.
                                            final FrameLayout overlay = new FrameLayout(getBaseContext());
                                            overlay.setBackgroundColor(ta.getColor(R.styleable.FileChooser_fileChooserNewFolderOverlayColor, 0x60ffffff));
                                            overlay.setScrollContainer(true);
                                            ViewGroup.MarginLayoutParams params;
                                            if (root instanceof FrameLayout) {
                                                params = new FrameLayout.LayoutParams(
                                                    MATCH_PARENT, MATCH_PARENT, CENTER);
                                            } else {
                                                params = new LinearLayout.LayoutParams(
                                                    MATCH_PARENT, MATCH_PARENT);
                                            }
                                            root.addView(overlay, params);

                                            overlay.setOnClickListener(null);
                                            overlay.setVisibility(GONE);
                                            FileChooserDialog.this._newFolderView = overlay;
                                            overlay.setFocusable(false);

                                            // A LynearLayout and a pair of Spaces to center views.
                                            LinearLayout linearLayout = new LinearLayout(getBaseContext());
                                            params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, CENTER);
                                            overlay.addView(linearLayout, params);

                                            float widthWeight = ta.getFloat(R.styleable.FileChooser_fileChooserNewFolderWidthWeight, 0.6f);
                                            if (widthWeight <= 0) widthWeight = 0.6f;
                                            if (widthWeight > 1f) widthWeight = 1f;

                                            // The Space on the left.
                                            Space leftSpace = new Space(getBaseContext());
                                            params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, (1f - widthWeight) / 2);
                                            linearLayout.addView(leftSpace, params);
                                            leftSpace.setFocusable(false);

                                            // A solid holder view for the EditText and Buttons.
                                            final LinearLayout holder = new LinearLayout(getBaseContext());
                                            holder.setOrientation(LinearLayout.VERTICAL);
                                            holder.setBackgroundColor(ta.getColor(R.styleable.FileChooser_fileChooserNewFolderBackgroundColor, 0xffffffff));
                                            final int elevation = ta.getInt(R.styleable.FileChooser_fileChooserNewFolderElevation, 25);
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                holder.setElevation(elevation);
                                            } else {
                                                ViewCompat.setElevation(holder, elevation);
                                            }
                                            params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, widthWeight);
                                            linearLayout.addView(holder, params);
                                            holder.setFocusable(false);

                                            // The Space on the right.
                                            Space rightSpace = new Space(getBaseContext());
                                            params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, (1f - widthWeight) / 2);
                                            linearLayout.addView(rightSpace, params);
                                            rightSpace.setFocusable(false);

                                            final EditText input = new EditText(getBaseContext());
                                            final int color = ta.getColor(R.styleable.FileChooser_fileChooserNewFolderTextColor, buttonColor);
                                            input.setTextColor(color);
                                            input.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                                            input.setText(newFolder.getName());
                                            input.setSelectAllOnFocus(true);
                                            input.setSingleLine(true);
                                            // There should be no suggestions, but... android... :)
                                            input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                                            input.setFilters(new InputFilter[]{FileChooserDialog.this._newFolderFilter != null ? FileChooserDialog.this._newFolderFilter : new NewFolderFilter()});
                                            input.setGravity(CENTER_HORIZONTAL);
                                            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                                            params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                            params.setMargins(3, 2, 3, 0);
                                            holder.addView(input, params);

                                            this.input = input;

                                            // A FrameLayout to hold buttons
                                            final FrameLayout buttons = new FrameLayout(getBaseContext());
                                            params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                            holder.addView(buttons, params);

                                            // The Cancel button.
                                            final Button cancel = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                            if (FileChooserDialog.this._newFolderCancelRes == null)
                                                cancel.setText(FileChooserDialog.this._newFolderCancel);
                                            else
                                                cancel.setText(FileChooserDialog.this._newFolderCancelRes);
                                            cancel.setTextColor(buttonColor);
                                            params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, START);
                                            buttons.addView(cancel, params);

                                            // The OK button.
                                            final Button ok = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                            if (FileChooserDialog.this._newFolderOkRes == null)
                                                ok.setText(FileChooserDialog.this._newFolderOk);
                                            else ok.setText(FileChooserDialog.this._newFolderOkRes);
                                            ok.setTextColor(buttonColor);
                                            params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, END);
                                            buttons.addView(ok, params);

                                            final int id = cancel.hashCode();
                                            cancel.setId(id);
                                            ok.setNextFocusLeftId(id);
                                            input.setNextFocusLeftId(id);

                                            // Event Listeners.
                                            input.setOnEditorActionListener((v23, actionId, event) -> {
                                                if (actionId == EditorInfo.IME_ACTION_DONE) {
                                                    UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                    FileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                    overlay.setVisibility(GONE);
                                                    overlay.clearFocus();
                                                    if (FileChooserDialog.this._enableDpad) {
                                                        FileChooserDialog.this._btnNeutral.setFocusable(true);
                                                        FileChooserDialog.this._btnNeutral.requestFocus();
                                                        FileChooserDialog.this._list.setFocusable(true);
                                                    }
                                                    return true;
                                                }
                                                return false;
                                            });
                                            cancel.setOnClickListener(v22 -> {
                                                UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                overlay.setVisibility(GONE);
                                                overlay.clearFocus();
                                                if (FileChooserDialog.this._enableDpad) {
                                                    Button b = FileChooserDialog.this._btnNeutral;
                                                    b.setFocusable(true);
                                                    b.requestFocus();
                                                    FileChooserDialog.this._list.setFocusable(true);
                                                }
                                            });
                                            ok.setOnClickListener(v2 -> {
                                                FileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                overlay.setVisibility(GONE);
                                                overlay.clearFocus();
                                                if (FileChooserDialog.this._enableDpad) {
                                                    Button b = FileChooserDialog.this._btnNeutral;
                                                    b.setFocusable(true);
                                                    b.requestFocus();
                                                    FileChooserDialog.this._list.setFocusable(true);
                                                }
                                            });
                                            ta.recycle();
                                            // endregion
                                        }

                                        if (FileChooserDialog.this._newFolderView.getVisibility() != VISIBLE) {
                                            FileChooserDialog.this._newFolderView.setVisibility(VISIBLE);
                                            if (FileChooserDialog.this._enableDpad) {
                                                FileChooserDialog.this._newFolderView.requestFocus();
                                                FileChooserDialog.this._btnNeutral.setFocusable(false);
                                                FileChooserDialog.this._list.setFocusable(false);
                                            }
                                            if (FileChooserDialog.this._pathView != null &&
                                                FileChooserDialog.this._pathView.getVisibility() == View.VISIBLE) {
                                                FileChooserDialog.this._newFolderView.setPadding(0, UiUtil.dip2px(32),
                                                    0, UiUtil.dip2px(12));
                                            } else {
                                                FileChooserDialog.this._newFolderView.setPadding(0, UiUtil.dip2px(12),
                                                    0, UiUtil.dip2px(12));
                                            }
                                        } else {
                                            FileChooserDialog.this._newFolderView.setVisibility(GONE);
                                            if (FileChooserDialog.this._enableDpad) {
                                                FileChooserDialog.this._newFolderView.clearFocus();
                                                FileChooserDialog.this._btnNeutral.setFocusable(true);
                                                FileChooserDialog.this._list.setFocusable(true);
                                            }
                                        }
                                    }
                                });
                                delete.setOnClickListener(v1 -> {
                                    //Toast.makeText(getBaseContext(), "delete clicked", Toast.LENGTH_SHORT).show();
                                    hideOptions.run();

                                    if (FileChooserDialog.this._chooseMode == CHOOSE_MODE_SELECT_MULTIPLE) {
                                        Queue<File> parents = new ArrayDeque<>();
                                        File current = FileChooserDialog.this._currentDir.getParentFile();
                                        final File root1 = Environment.getExternalStorageDirectory();
                                        while (current != null && !current.equals(root1)) {
                                            parents.add(current);
                                            current = current.getParentFile();
                                        }

                                        for (File file : FileChooserDialog.this._adapter.getSelected()) {
                                            try {
                                                deleteFile(file);

                                            } catch (IOException e) {
                                                // There's probably a better way to handle this, but...
                                                e.printStackTrace();
                                                Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                        }
                                        FileChooserDialog.this._adapter.clearSelected();

                                        // Check whether the current directory was deleted.
                                        if (!FileChooserDialog.this._currentDir.exists()) {
                                            File parent;

                                            while ((parent = parents.poll()) != null) {
                                                if (parent.exists()) break;
                                            }

                                            if (parent != null && parent.exists()) {
                                                FileChooserDialog.this._currentDir = parent;
                                            } else {
                                                FileChooserDialog.this._currentDir = Environment.getExternalStorageDirectory();
                                            }
                                        }

                                        FileChooserDialog.this._chooseMode = CHOOSE_MODE_NORMAL;
                                        FileChooserDialog.this._btnPositive.setVisibility(INVISIBLE);
                                        FileChooserDialog.this._adapter.clearSelected();
                                        refreshDirs();
                                        return;
                                    }

                                    FileChooserDialog.this._chooseMode = FileChooserDialog.this._chooseMode != CHOOSE_MODE_DELETE ? CHOOSE_MODE_DELETE : CHOOSE_MODE_NORMAL;
                                    if (FileChooserDialog.this._deleteMode == null) {
                                        FileChooserDialog.this._deleteMode = () -> {
                                            if (FileChooserDialog.this._chooseMode == CHOOSE_MODE_DELETE) {
                                                final int color1 = 0x80ff0000;
                                                final PorterDuffColorFilter red = new PorterDuffColorFilter(color1, PorterDuff.Mode.SRC_IN);
                                                _btnNeutral.getCompoundDrawables()[0].setColorFilter(red);
                                                _btnNeutral.setTextColor(color1);
                                                delete.getCompoundDrawables()[0].setColorFilter(red);
                                                delete.setTextColor(color1);
                                            } else {
                                                _btnNeutral.getCompoundDrawables()[0].clearColorFilter();
                                                _btnNeutral.setTextColor(buttonColor);
                                                delete.getCompoundDrawables()[0].clearColorFilter();
                                                delete.setTextColor(buttonColor);
                                            }
                                        };
                                    }
                                    FileChooserDialog.this._deleteMode.run();
                                });
                                // endregion
                            } else if (FileChooserDialog.this._options.getVisibility() == VISIBLE) {
                                hideOptions.run();
                            } else {
                                showOptions.run();
                            }
                        }
                    });
                }
            }
        });

        this._list = this._alertDialog.getListView();
        this._list.setOnItemClickListener(this);
        if (this._enableMultiple) {
            this._list.setOnItemLongClickListener(this);
        }

        if (_enableDpad) {
            this._list.setSelector(listview_item_selector);
            this._list.setDrawSelectorOnTop(true);
            this._list.setItemsCanFocus(true);
            this._list.setOnItemSelectedListener(this);
            this._list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
        return this;
    }

    private void showDialog() {
        Window window = _alertDialog.getWindow();
        if (window != null) {
            TypedArray ta = getBaseContext().obtainStyledAttributes(R.styleable.FileChooser);
            window.setGravity(ta.getInt(R.styleable.FileChooser_fileChooserDialogGravity, Gravity.CENTER));
            ta.recycle();
        }
        _alertDialog.show();
    }

    public FileChooserDialog show() {
        if (!ignoreApi && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            Toast.makeText(getBaseContext(), "FileChooserDialog uses java.io.File api. Begining from Android R, it has been restrictet, thus FileChooserDialog is only supported until Android Q!", Toast.LENGTH_LONG).show();
            return this;
        }

        if (_alertDialog == null || _list == null) {
            build();
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            showDialog();
            return this;
        }

        if (_permissionListener == null) {
            _permissionListener = new PermissionsUtil.OnPermissionListener() {
                @Override
                public void onPermissionGranted(String[] permissions) {
                    boolean show = false;
                    for (String permission : permissions) {
                        if (permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                            show = true;
                            break;
                        }
                    }
                    if (!show) return;
                    if (_enableOptions) {
                        show = false;
                        for (String permission : permissions) {
                            if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                                show = true;
                                break;
                            }
                        }
                    }
                    if (!show) return;
                    if (_adapter.isEmpty()) refreshDirs();
                    showDialog();
                }

                @Override
                public void onPermissionDenied(String[] permissions) {
                    //
                }

                @Override
                public void onShouldShowRequestPermissionRationale(final String[] permissions) {
                    Toast.makeText(getBaseContext(), "You denied the Read/Write permissions!",
                        Toast.LENGTH_LONG).show();
                }
            };
        }

        final String[] permissions =
            _enableOptions ? new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}
                : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

        PermissionsUtil.checkPermissions(getBaseContext(), _permissionListener, permissions);

        return this;
    }

    private boolean displayRoot;

    private void displayPath(@Nullable String path) {
        if (_pathView == null) {
            int rootId = getResources().getIdentifier("contentPanel", "id", getPackageName());
            ViewGroup root = ((AlertDialog) _alertDialog).findViewById(rootId);
            // In case the root id was changed or not found.
            if (root == null) {
                rootId = getResources().getIdentifier("contentPanel", "id", "android");
                root = ((AlertDialog) _alertDialog).findViewById(rootId);
                if (root == null) return;
            }

            ViewGroup.MarginLayoutParams params;
            if (root instanceof LinearLayout) {
                params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            } else {
                params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, TOP);
            }

            TypedArray ta = getBaseContext().obtainStyledAttributes(R.styleable.FileChooser);
            int style = ta.getResourceId(R.styleable.FileChooser_fileChooserPathViewStyle, R.style.FileChooserPathViewStyle);
            final Context context = getThemeWrappedContext(style);
            ta.recycle();
            ta = context.obtainStyledAttributes(R.styleable.FileChooser);

            displayRoot = ta.getBoolean(R.styleable.FileChooser_fileChooserPathViewDisplayRoot, true);

            _pathView = new TextView(context);
            root.addView(_pathView, 0, params);
            _pathView.setFocusable(false);

            int elevation = ta.getInt(R.styleable.FileChooser_fileChooserPathViewElevation, 2);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                _pathView.setElevation(elevation);
            } else {
                ViewCompat.setElevation(_pathView, elevation);
            }

            if (_pathViewCallback != null) {
                _pathViewCallback.customize(_pathView);
            }
            ta.recycle();
        }

        if (path == null) {
            _pathView.setVisibility(GONE);
            ViewGroup.MarginLayoutParams param = ((ViewGroup.MarginLayoutParams) _list.getLayoutParams());
            if (_pathView.getParent() instanceof FrameLayout) {
                param.topMargin = 0;
            }
            _list.setLayoutParams(param);
        } else {
            if (removableRoot == null || primaryRoot == null) {
                removableRoot = FileUtil.getStoragePath(getBaseContext(), true);
                primaryRoot = FileUtil.getStoragePath(getBaseContext(), false);
            }
            if (path.contains(removableRoot))
                path = path.substring(displayRoot ? removableRoot.lastIndexOf('/') + 1 : removableRoot.length());
            if (path.contains(primaryRoot))
                path = path.substring(displayRoot ? primaryRoot.lastIndexOf('/') + 1 : primaryRoot.length());
            _pathView.setText(path);

            while (_pathView.getLineCount() > 1) {
                int i = path.indexOf("/");
                i = path.indexOf("/", i + 1);
                if (i == -1) break;
                path = "..." + path.substring(i);
                _pathView.setText(path);
            }

            _pathView.setVisibility(VISIBLE);

            ViewGroup.MarginLayoutParams param = ((ViewGroup.MarginLayoutParams) _list.getLayoutParams());
            if (_pathView.getHeight() == 0) {
                ViewTreeObserver viewTreeObserver = _pathView.getViewTreeObserver();
                viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (_pathView.getHeight() <= 0) {
                            return false;
                        }
                        viewTreeObserver.removeOnPreDrawListener(this);
                        if (_pathView.getParent() instanceof FrameLayout) {
                            param.topMargin = _pathView.getHeight();
                        }
                        _list.setLayoutParams(param);
                        _list.post(() -> _list.setSelection(0));
                        return true;
                    }
                });
            } else {
                if (_pathView.getParent() instanceof FrameLayout) {
                    param.topMargin = _pathView.getHeight();
                }
                _list.setLayoutParams(param);
            }
        }
    }

    private String removableRoot = null;
    private String primaryRoot = null;

    public final class RootFile extends File {
        private String name;

        RootFile(String path) {
            super(path);
            this.name = null;
        }

        RootFile(String path, String name) {
            super(path);
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name != null ? this.name : super.getName();
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public boolean isHidden() {
            return false;
        }

        @Override
        public long lastModified() {
            return 0L;
        }
    }

    private void listDirs() {
        _entries.clear();

        // Get files
        File[] files = _currentDir.listFiles(_fileFilter);

        // Add the ".." entry
        if (removableRoot == null || primaryRoot == null) {
            removableRoot = FileUtil.getStoragePath(getBaseContext(), true);
            primaryRoot = FileUtil.getStoragePath(getBaseContext(), false);
        }
        if (removableRoot != null && primaryRoot != null && !removableRoot.equals(primaryRoot)) {
            if (_currentDir.getAbsolutePath().equals(primaryRoot)) {
                _entries.add(new RootFile(removableRoot));
            } else if (_currentDir.getAbsolutePath().equals(removableRoot)) {
                _entries.add(new RootFile(primaryRoot));
            }
        }
        boolean displayPath = false;
        if (_entries.isEmpty() && _currentDir.getParentFile() != null && _currentDir.getParentFile().canRead()) {
            _entries.add(new RootFile(_currentDir.getParentFile().getAbsolutePath(), ".."));
            displayPath = true;
        }

        if (files == null) return;

        List<File> dirList = new LinkedList<>();
        List<File> fileList = new LinkedList<>();

        for (File f : files) {
            if (f.isDirectory()) {
                dirList.add(f);
            } else {
                fileList.add(f);
            }
        }

        sortByName(dirList);
        sortByName(fileList);
        _entries.addAll(dirList);
        _entries.addAll(fileList);

        if (_alertDialog != null && _alertDialog.isShowing() && _displayPath) {
            if (displayPath) {
                displayPath(_currentDir.getPath());
            } else {
                displayPath(null);
            }
        }
    }

    private void sortByName(@NonNull final List<File> list) {
        Collections.sort(list, (f1, f2) -> f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase()));
    }

    private void createNewDirectory(@NonNull final String name) {
        final File newDir = new File(this._currentDir, name);
        if (!newDir.exists() && newDir.mkdirs()) {
            refreshDirs();
            return;
        }
        Toast.makeText(getBaseContext(),
            "Couldn't create folder " + newDir.getName() + " at " + newDir.getAbsolutePath(),
            Toast.LENGTH_LONG).show();
    }

    // todo: ask for confirmation! (inside an AlertDialog.. Ironical, I know)
    private Runnable _deleteMode;

    private void deleteFile(@NonNull final File file) throws IOException {
        if (file.isDirectory()) {
            final File[] entries = file.listFiles();
            for (final File entry : entries) {
                deleteFile(entry);
            }
        }
        if (!file.delete())
            throw new IOException("Couldn't delete \"" + file.getName() + "\" at \"" + file.getParent());
    }

    private int scrollTo;

    @Override
    public void onItemClick(@Nullable final AdapterView<?> parent, @NonNull final View list, final int position, final long id) {
        if (position < 0 || position >= _entries.size()) return;

        scrollTo = 0;
        File file = _entries.get(position);
        if (file instanceof RootFile) {
            if (_folderNavUpCB == null) _folderNavUpCB = _defaultNavUpCB;
            if (_folderNavUpCB.canUpTo(file)) {
                _currentDir = file;
                _chooseMode = _chooseMode == CHOOSE_MODE_DELETE ? CHOOSE_MODE_NORMAL : _chooseMode;
                if (_deleteMode != null) _deleteMode.run();
                lastSelected = false;
                if (!_adapter.getIndexStack().empty()) {
                    scrollTo = _adapter.getIndexStack().pop();
                }
            }
        } else {
            switch (_chooseMode) {
                case CHOOSE_MODE_NORMAL:
                    if (file.isDirectory()) {
                        if (_folderNavToCB == null) _folderNavToCB = _defaultNavToCB;
                        if (_folderNavToCB.canNavigate(file)) {
                            _currentDir = file;
                            scrollTo = 0;
                            _adapter.getIndexStack().push(position);
                        }
                    } else if ((!_dirOnly) && _onChosenListener != null) {
                        _onChosenListener.onChoosePath(file.getAbsolutePath(), file);
                        _alertDialog.dismiss();
                        return;
                    }
                    lastSelected = false;
                    break;
                case CHOOSE_MODE_SELECT_MULTIPLE:
                    if (file.isDirectory()) {
                        if (_folderNavToCB == null) _folderNavToCB = _defaultNavToCB;
                        if (_folderNavToCB.canNavigate(file)) {
                            _currentDir = file;
                            scrollTo = 0;
                            _adapter.getIndexStack().push(position);
                        }
                    } else {
                        _adapter.selectItem(position);
                        if (!_adapter.isAnySelected()) {
                            _chooseMode = CHOOSE_MODE_NORMAL;
                            if (!_dirOnly)
                                _btnPositive.setVisibility(INVISIBLE);
                        }
                        return;
                    }
                    break;
                case CHOOSE_MODE_DELETE:
                    try {
                        deleteFile(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    _chooseMode = CHOOSE_MODE_NORMAL;
                    if (_deleteMode != null) _deleteMode.run();
                    scrollTo = -1;
                    break;
                default:
                    // ERROR! It shouldn't get here...
                    break;
            }
        }
        refreshDirs();
        if (scrollTo != -1) {
            _list.setSelection(scrollTo);
            _list.post(() -> _list.setSelection(scrollTo));
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View list, int position, long id) {
        File file = _entries.get(position);
        if (file instanceof RootFile) return true;
        if (!_allowSelectDir && file.isDirectory()) return true;
        _adapter.selectItem(position);
        if (!_adapter.isAnySelected()) {
            _chooseMode = CHOOSE_MODE_NORMAL;
            if (!_dirOnly)
                _btnPositive.setVisibility(INVISIBLE);
        } else {
            _chooseMode = CHOOSE_MODE_SELECT_MULTIPLE;
            if (!_dirOnly)
                _btnPositive.setVisibility(VISIBLE);
        }
        if (FileChooserDialog.this._deleteMode != null) FileChooserDialog.this._deleteMode.run();
        return true;
    }

    private boolean lastSelected = false;

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
        lastSelected = position == _entries.size() - 1;
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
        lastSelected = false;
    }

    @Override
    public boolean onKey(final DialogInterface dialog, final int keyCode, final KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (_newFolderView != null && _newFolderView.getVisibility() == VISIBLE) {
                _newFolderView.setVisibility(GONE);
                return true;
            }

            _onBackPressedListener.onBackPressed(_alertDialog);
            return true;
        }

        if (!_enableDpad) return true;

        if (!_list.hasFocus()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (_btnNeutral == null) return false;
                    if (_btnNeutral.hasFocus() || _btnNegative.hasFocus() || _btnPositive.hasFocus()) {
                        if (_options != null && _options.getVisibility() == VISIBLE) {
                            _options.requestFocus(_btnNeutral.hasFocus() ? FOCUS_RIGHT : FOCUS_LEFT);
                            return true;
                        } else if (_newFolderView != null && _newFolderView.getVisibility() == VISIBLE) {
                            _newFolderView.requestFocus(_btnNeutral.hasFocus() ? FOCUS_RIGHT : FOCUS_LEFT);
                            return true;
                        } else {
                            _list.requestFocus();
                            lastSelected = true;
                            return true;
                        }
                    }
                    if (_options != null && _options.hasFocus()) {
                        _list.requestFocus();
                        lastSelected = true;
                        return true;
                    }
                    break;
                default:
                    return false;
            }
        }

        if (_list.hasFocus()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    _onBackPressedListener.onBackPressed(_alertDialog);
                    lastSelected = false;
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    _list.performItemClick(_list, _list.getSelectedItemPosition(), _list.getSelectedItemId());
                    lastSelected = false;
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (lastSelected) {
                        lastSelected = false;
                        if (_options != null && _options.getVisibility() == VISIBLE) {
                            _options.requestFocus();
                        } else {
                            if (_btnNeutral.getVisibility() == VISIBLE)
                                _btnNeutral.requestFocus();
                            else _btnNegative.requestFocus();
                        }
                        return true;
                    }
                    break;
                default:
                    return false;
            }
        }
        return false;
    }

    @Override
    public void onClick(@NonNull final DialogInterface dialog, final int which) {
        //
    }

    private void refreshDirs() {
        listDirs();
        _adapter.setEntries(_entries);
    }

    public void dismiss() {
        if (_alertDialog == null) return;
        _alertDialog.dismiss();
    }

    public void cancel() {
        if (_alertDialog == null) return;
        _alertDialog.cancel();
    }

    private @Nullable
    @StyleRes
    Integer _themeResId = null;
    private List<File> _entries = new ArrayList<>();
    private DirAdapter _adapter;
    private File _currentDir;
    private AlertDialog _alertDialog;
    private ListView _list;
    private OnChosenListener _onChosenListener = null;
    private OnSelectedListener _onSelectedListener = null;
    private boolean _dirOnly;
    private FileFilter _fileFilter;
    private @Nullable
    @StringRes
    Integer _titleRes = null, _okRes = null, _negativeRes = null;
    private @NonNull
    String _title = "Select a file", _ok = "Choose", _negative = "Cancel";
    private @Nullable
    @DrawableRes
    Integer _iconRes = null;
    private @Nullable
    Drawable _icon = null;
    private @Nullable
    @LayoutRes
    Integer _layoutRes = null;
    private String _dateFormat;
    private DialogInterface.OnClickListener _negativeListener;
    private DialogInterface.OnCancelListener _onCancelListener;
    private boolean _disableTitle;
    private boolean _displayPath;
    private TextView _pathView;
    private CustomizePathView _pathViewCallback;
    private boolean _cancelOnTouchOutside;
    private DialogInterface.OnDismissListener _onDismissListener;
    private boolean _enableOptions;
    private View _options;
    private @Nullable
    @StringRes
    Integer _createDirRes = null, _deleteRes = null, _newFolderCancelRes = null, _newFolderOkRes = null;
    private @NonNull
    String _createDir = "New folder", _delete = "Delete", _newFolderCancel = "Cancel", _newFolderOk = "Ok";
    private @Nullable
    @DrawableRes
    Integer _optionsIconRes = null, _createDirIconRes = null, _deleteIconRes = null;
    private @Nullable
    Drawable _optionsIcon = null, _createDirIcon = null, _deleteIcon = null;
    private View _newFolderView;
    private boolean _enableMultiple;
    private boolean _allowSelectDir = false;
    private boolean _enableDpad;
    private PermissionsUtil.OnPermissionListener _permissionListener;
    private Button _btnNeutral;
    private Button _btnNegative;
    private Button _btnPositive;

    @FunctionalInterface
    public interface AdapterSetter {
        void apply(@NonNull final DirAdapter adapter);
    }

    private AdapterSetter _adapterSetter = null;

    @FunctionalInterface
    public interface CanNavigateUp {
        boolean canUpTo(@Nullable final File dir);
    }

    @FunctionalInterface
    public interface CanNavigateTo {
        boolean canNavigate(@NonNull final File dir);
    }

    private CanNavigateUp _folderNavUpCB;
    private CanNavigateTo _folderNavToCB;

    private final static CanNavigateUp _defaultNavUpCB = dir -> dir != null && dir.canRead();

    private final static CanNavigateTo _defaultNavToCB = dir -> true;

    /**
     * attempts to move to the parent directory
     *
     * @return true if successful. false otherwise
     */
    public boolean goBack() {
        if (_entries.size() > 0 &&
            (_entries.get(0).getName().equals(".."))) {
            _list.performItemClick(_list, 0, 0);
            return true;
        }
        return false;
    }

    @FunctionalInterface
    public interface OnBackPressedListener {
        void onBackPressed(@NonNull final AlertDialog dialog);
    }

    private OnBackPressedListener _onBackPressedListener = (dialog -> {
        if (FileChooserDialog.this._entries.size() > 0
            && (FileChooserDialog.this._entries.get(0).getName().equals(".."))) {
            if (FileChooserDialog.this._onBackPressed != null)
                FileChooserDialog.this._onBackPressed.onBackPressed(dialog);
            else FileChooserDialog.this._defaultBack.onBackPressed(dialog);
        } else {
            if (FileChooserDialog.this._onLastBackPressed != null)
                FileChooserDialog.this._onLastBackPressed.onBackPressed(dialog);
            else FileChooserDialog._defaultLastBack.onBackPressed(dialog);
        }
    });

    private OnBackPressedListener _onBackPressed;
    private OnBackPressedListener _onLastBackPressed;
    private final OnBackPressedListener _defaultBack = dialog -> FileChooserDialog.this._list.performItemClick(FileChooserDialog.this._list, 0, 0);
    private static final OnBackPressedListener _defaultLastBack = Dialog::cancel;

    private static final int CHOOSE_MODE_NORMAL = 0;
    private static final int CHOOSE_MODE_DELETE = 1;
    private static final int CHOOSE_MODE_SELECT_MULTIPLE = 2;

    private int _chooseMode = CHOOSE_MODE_NORMAL;

    private NewFolderFilter _newFolderFilter;

    @FunctionalInterface
    public interface CustomizePathView {
        void customize(TextView pathView);
    }
}
