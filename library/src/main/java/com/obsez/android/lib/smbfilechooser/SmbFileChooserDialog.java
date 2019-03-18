package com.obsez.android.lib.smbfilechooser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.obsez.android.lib.smbfilechooser.internals.ExtSmbFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.RegexSmbFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;
import com.obsez.android.lib.smbfilechooser.tool.IExceptionHandler;
import com.obsez.android.lib.smbfilechooser.tool.SmbDirAdapter;

import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.Configuration;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;
import kotlin.Triple;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.View.GONE;
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
 */
@SuppressWarnings({"SpellCheckingInspection", "unused", "WeakerAccess", "UnusedReturnValue"})
public class SmbFileChooserDialog extends LightContextWrapper implements DialogInterface.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, AdapterView.OnItemSelectedListener, DialogInterface.OnKeyListener {
    private Thread thread;
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("SmbFileChooserDialog EXECUTOR - network Thread");
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });

    public ExecutorService getNetworkThread() {
        return EXECUTOR;
    }

    private IExceptionHandler.ExceptionHandler _handler;
    private boolean _terminate;

    public SmbFileChooserDialog setExceptionHandler(@NonNull final IExceptionHandler.ExceptionHandler handler) {
        this._handler = handler;
        return this;
    }

    private IExceptionHandler _exceptionHandler = new IExceptionHandler() {
        @Override
        public void handleException(@NonNull final Throwable exception) {
            _terminate = _handler != null && _handler.handle(exception, ExceptionId.UNDEFINED);
            if (_alertDialog != null && _terminate) _alertDialog.dismiss();
        }

        @Override
        public void handleException(@NonNull final Throwable exception, final int id) {
            _terminate = _handler != null && _handler.handle(exception, id);
            if (_alertDialog != null && _terminate) _alertDialog.dismiss();
        }
    };

    @FunctionalInterface
    public interface OnChosenListener {
        void onChoosePath(@NonNull String path, @NonNull SmbFile file);
    }

    @FunctionalInterface
    public interface OnSelectedListener {
        void onSelectFiles(@NonNull final List<SmbFile> files);
    }

    private SmbFileChooserDialog(@NonNull final Context context, @NonNull final String serverIP) {
        this(context, null, serverIP, null);
    }

    private SmbFileChooserDialog(@NonNull final Context context, @Nullable final Properties properties, @NonNull final String serverIP) {
        this(context, properties, serverIP, null);
    }

    private SmbFileChooserDialog(@NonNull final Context context, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthenticator auth) {
        this(context, null, serverIP, auth);
    }

    private SmbFileChooserDialog(@NonNull final Context context, @Nullable final Properties properties, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthenticator auth) {
        super(context);

        if (serverIP.startsWith("smb://")) {
            this._serverIP = serverIP.substring(6);
        } else this._serverIP = serverIP;
        if (serverIP.endsWith("/")) {
            this._serverIP = this._serverIP.substring(0, this._serverIP.length() - 1);
        }

        if (properties == null) init(this._serverIP);
        else init(this._serverIP, properties);

        try {
            EXECUTOR.submit(() -> {
                if (_smbContext != null) {
                    _smbContext.withCredentials(auth);
                    this._rootDirPath = "smb://" + this._serverIP + '/';
                    try {
                        this._rootDir = new SmbFile(this._rootDirPath, _smbContext);
                    } catch (MalformedURLException e) {
                        _exceptionHandler.handleException(e, IExceptionHandler.ExceptionId.FAILED_TO_FIND_ROOT_DIR);
                        this._rootDir = null;
                    }
                }
            }).get();
        } catch (ExecutionException | InterruptedException e) {
            _exceptionHandler.handleException(e, IExceptionHandler.ExceptionId.EXECUTOR_INTERRUPTED);
        }
    }

    private void init(@NonNull final String serverIP) {
        Properties properties = new Properties();
        properties.setProperty("jcifs.smb.client.responseTimeout", "5000");
        properties.setProperty("jcifs.smb.client.soTimeout", "15000");
        properties.setProperty("jcifs.smb.client.connTimeout", "5000");
        properties.setProperty("jcifs.smb.client.sessionTimeout", "15000");
        properties.setProperty("jcifs.netbios.soTimeout", "15000");
        properties.setProperty("jcifs.netbios.retryCount", "1");
        properties.setProperty("jcifs.netbios.retryTimeout", "2000");
        properties.setProperty("jcifs.smb.client.minVersion", "SMB1");
        properties.setProperty("jcifs.smb.client.maxVersion", "SMB311");
        this.init(serverIP, properties);
    }

    private void init(@NonNull final String serverIP, @NonNull final Properties properties) {
        try {
            EXECUTOR.submit(() -> {
                try {
                    properties.setProperty("jcifs.smb.client.domain", serverIP);
                    properties.setProperty("jcifs.netbios.wins", serverIP);
                    Configuration configuration = new PropertyConfiguration(properties);
                    _smbContext = new BaseContext(configuration);
                } catch (CIFSException e) {
                    runOnUiThread(() -> _exceptionHandler.handleException(e, IExceptionHandler.ExceptionId.FAILED_TO_INITIALIZE));
                }
            }).get();
        } catch (ExecutionException | InterruptedException e) {
            _exceptionHandler.handleException(e, IExceptionHandler.ExceptionId.EXECUTOR_INTERRUPTED);
        }
    }

    @NonNull
    public static SmbFileChooserDialog newDialog(@NonNull final Context context, @NonNull final String serverIP) {
        return new SmbFileChooserDialog(context, serverIP);
    }

    @NonNull
    public static SmbFileChooserDialog newDialog(@NonNull final Context context, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthenticator auth) {
        return new SmbFileChooserDialog(context, serverIP, auth);
    }

    @NonNull
    public static SmbFileChooserDialog newDialog(@NonNull final Context context, @Nullable final Properties properties, @NonNull final String serverIP) {
        return new SmbFileChooserDialog(context, properties, serverIP);
    }

    @NonNull
    public static SmbFileChooserDialog newDialog(@NonNull final Context context, @Nullable final Properties properties, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthenticator auth) {
        return new SmbFileChooserDialog(context, properties, serverIP, auth);
    }

    @NonNull
    public SmbFileChooserDialog setFilter(@NonNull final SmbFileFilter sff) {
        setFilter(false, false, (String[]) null);
        this._fileFilter = sff;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setFilter(final boolean dirOnly, final boolean allowHidden, @NonNull final SmbFileFilter sff) {
        setFilter(dirOnly, allowHidden, (String[]) null);
        this._fileFilter = sff;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setFilter(boolean allowHidden, @Nullable String... suffixes) {
        return setFilter(false, allowHidden, suffixes);
    }

    @NonNull
    public SmbFileChooserDialog setFilter(final boolean dirOnly, final boolean allowHidden, @Nullable final String... suffixes) {
        this._dirOnly = dirOnly;
        if (suffixes == null || suffixes.length == 0) {
            this._fileFilter = dirOnly ?
                file -> {
                    try {
                        return file.isDirectory() && (!file.isHidden() || allowHidden);
                    } catch (SmbException e) {
                        return false;
                    }
                } : file -> {
                try {
                    return !file.isHidden() || allowHidden;
                } catch (SmbException e) {
                    return false;
                }
            };
        } else {
            this._fileFilter = new ExtSmbFileFilter(_dirOnly, allowHidden, suffixes);
        }
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setFilterRegex(boolean dirOnly, boolean allowHidden, @NonNull String pattern, int flags) {
        this._dirOnly = dirOnly;
        this._fileFilter = new RegexSmbFileFilter(_dirOnly, allowHidden, pattern, flags);
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setFilterRegex(boolean dirOnly, boolean allowHidden, @NonNull String pattern) {
        this._dirOnly = dirOnly;
        this._fileFilter = new RegexSmbFileFilter(_dirOnly, allowHidden, pattern, Pattern.CASE_INSENSITIVE);
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setStartFile(@Nullable final String startPath) {
        if (_terminate) return this;
        try {
            EXECUTOR.submit(() -> {
                try {
                    if (startPath != null) {
                        _currentDir = new SmbFile(startPath, _smbContext);
                    } else {
                        _currentDir = _rootDir;
                    }

                    if (!_currentDir.isDirectory()) {
                        String parent = _currentDir.getParent();
                        if (parent == null) {
                            throw new MalformedURLException(startPath + " has no parent directory");
                        }
                        _currentDir = new SmbFile(parent, _smbContext);
                    }

                    if (_currentDir == null) {
                        _currentDir = _rootDir;
                    }

                    if (!_currentDir.exists() || !_currentDir.canRead()) {
                        throw new MalformedURLException("Can't connect to " + _currentDir.getPath());
                    }
                } catch (final MalformedURLException | SmbException | NullPointerException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> _exceptionHandler.handleException(e, IExceptionHandler.ExceptionId.FAILED_TO_FIND_ROOT_DIR));
                }

                return SmbFileChooserDialog.this;
            }).get();
        } catch (ExecutionException | InterruptedException e) {
            _exceptionHandler.handleException(e, IExceptionHandler.ExceptionId.EXECUTOR_INTERRUPTED);
        }
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setCancelable(boolean cancelable) {
        this._cancelable = cancelable;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog cancelOnTouchOutside(boolean cancelOnTouchOutside) {
        this._cancelOnTouchOutside = cancelOnTouchOutside;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog dismissOnButtonClick(boolean dismissOnButtonClick) {
        this._dismissOnButtonClick = dismissOnButtonClick;
        if (dismissOnButtonClick) {
            this._defaultLastBack = Dialog::dismiss;
        } else {
            this._defaultLastBack = dialog -> {
                //
            };
        }
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOnChosenListener(@NonNull OnChosenListener listener) {
        this._onChosenListener = listener;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOnSelectedListener(@NonNull final OnSelectedListener listener) {
        this._onSelectedListener = listener;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOnDismissListener(@NonNull DialogInterface.OnDismissListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            this._onDismissListener = listener;
        }
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOnBackPressedListener(@NonNull OnBackPressedListener listener) {
        this._onBackPressed = listener;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOnLastBackPressedListener(@NonNull OnBackPressedListener listener) {
        this._onLastBackPressed = listener;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setResources(@Nullable @StringRes Integer titleRes, @Nullable @StringRes Integer okRes, @Nullable @StringRes Integer cancelRes) {
        this._titleRes = titleRes;
        this._okRes = okRes;
        this._negativeRes = cancelRes;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setResources(@Nullable String title, @Nullable String ok, @Nullable String cancel) {
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
    public SmbFileChooserDialog enableOptions(final boolean enableOptions) {
        this._enableOptions = enableOptions;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOptionResources(@Nullable @StringRes final Integer createDirRes, @Nullable @StringRes final Integer deleteRes, @Nullable @StringRes final Integer newFolderCancelRes, @Nullable @StringRes final Integer newFolderOkRes) {
        this._createDirRes = createDirRes;
        this._deleteRes = deleteRes;
        this._newFolderCancelRes = newFolderCancelRes;
        this._newFolderOkRes = newFolderOkRes;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOptionResources(@Nullable final String createDir, @Nullable final String delete, @Nullable final String newFolderCancel, @Nullable final String newFolderOk) {
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
    public SmbFileChooserDialog setOptionIcons(@DrawableRes final int optionsIconRes, @DrawableRes final int createDirIconRes, @DrawableRes final int deleteRes) {
        this._optionsIconRes = optionsIconRes;
        this._createDirIconRes = createDirIconRes;
        this._deleteIconRes = deleteRes;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setIcon(@Nullable @DrawableRes Integer iconId) {
        this._iconRes = iconId;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setLayoutView(@Nullable @LayoutRes Integer layoutResId) {
        this._layoutRes = layoutResId;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setRowLayoutView(@Nullable @LayoutRes Integer layoutResId) {
        this._rowLayoutRes = layoutResId;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setDefaultDateFormat() {
        return this.setDateFormat("yyyy/MM/dd HH:mm:ss");
    }

    @NonNull
    public SmbFileChooserDialog setDateFormat(@NonNull String format) {
        this._dateFormat = format;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setNegativeButtonListener(final DialogInterface.OnClickListener listener) {
        this._negativeListener = listener;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOnCancelListener(@NonNull final DialogInterface.OnCancelListener listener) {
        this._onCancelListener = listener;
        return this;
    }

    @Deprecated
    @NonNull
    public SmbFileChooserDialog setFileIcons(final boolean tryResolveFileTypeAndIcon, @Nullable final Drawable fileIcon, @Nullable final Drawable folderIcon) {
        this._adapterSetter = adapter -> {
            if (fileIcon != null)
                adapter.setDefaultFileIcon(fileIcon);
            if (folderIcon != null)
                adapter.setDefaultFolderIcon(folderIcon);
            //noinspection deprecation
            adapter.setResolveFileType(tryResolveFileTypeAndIcon);
        };
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setFileIcons(@Nullable final Drawable fileIcon, @Nullable final Drawable folderIcon) {
        this._adapterSetter = adapter -> {
            if (fileIcon != null)
                adapter.setDefaultFileIcon(fileIcon);
            if (folderIcon != null)
                adapter.setDefaultFolderIcon(folderIcon);
        };
        return this;
    }

    @Deprecated
    @NonNull
    public SmbFileChooserDialog setFileIcons(final boolean tryResolveFileTypeAndIcon, @Nullable @DrawableRes final Integer fileIconResId, @Nullable @DrawableRes final Integer folderIconResId) {
        this._adapterSetter = adapter -> {
            if (fileIconResId != null)
                adapter.setDefaultFileIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), fileIconResId));
            if (folderIconResId != null)
                adapter.setDefaultFolderIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), folderIconResId));
            //noinspection deprecation
            adapter.setResolveFileType(tryResolveFileTypeAndIcon);
        };
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setFileIcons(@Nullable @DrawableRes final Integer fileIconResId, @Nullable @DrawableRes final Integer folderIconResId) {
        this._adapterSetter = adapter -> {
            if (fileIconResId != null)
                adapter.setDefaultFileIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), fileIconResId));
            if (folderIconResId != null)
                adapter.setDefaultFolderIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), folderIconResId));
        };
        return this;
    }

    /**
     * @param setter you can customize the folder navi-adapter with `setter`
     * @return this
     */
    @NonNull
    public SmbFileChooserDialog setAdapterSetter(@NonNull AdapterSetter setter) {
        this._adapterSetter = setter;
        return this;
    }

    /**
     * @param cb give a hook at navigating up to a directory
     * @return this
     */
    @NonNull
    public SmbFileChooserDialog setNavigateUpTo(@NonNull CanNavigateUp cb) {
        this._folderNavUpCB = cb;
        return this;
    }

    /**
     * @param cb give a hook at navigating to a child directory
     * @return this
     */
    @NonNull
    public SmbFileChooserDialog setNavigateTo(@NonNull CanNavigateTo cb) {
        this._folderNavToCB = cb;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog disableTitle(boolean disable) {
        this._disableTitle = disable;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog displayPath(final boolean display) {
        this._displayPath = display;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog customizePathView(CustomizePathView callback) {
        _pathViewCallback = callback;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog enableMultiple(final boolean enableMultiple, final boolean allowSelectMultipleFolders) {
        this._enableMultiple = enableMultiple;
        this._allowSelectDir = allowSelectMultipleFolders;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setNewFolderFilter(@NonNull final NewFolderFilter filter) {
        this._newFolderFilter = filter;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog enableDpad(final boolean enableDpad) {
        this._enableDpad = enableDpad;
        return this;
    }

    public SmbFileChooserDialog setTheme(@StyleRes final int themeResId) {
        this._themeResId = themeResId;
        return this;
    }

    public SmbFileChooserDialog build() {
        return this.build(null);
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    public SmbFileChooserDialog build(@StyleRes @Nullable Integer themeResId) {
        if (_terminate) {
            return this;
        }

        if (themeResId != null) this._themeResId = themeResId;
        if (this._themeResId == null) {
            themeWrapContext(R.style.FileChooserStyle);
        } else {
            themeWrapContext(this._themeResId);
        }

        TypedArray ta = getBaseContext().obtainStyledAttributes(R.styleable.FileChooser);
        int style = ta.getResourceId(R.styleable.FileChooser_fileChooserDialogStyle, R.style.FileChooserDialogStyle);
        final AlertDialog.Builder builder = new AlertDialog.Builder(getThemeWrappedContext(style),
            ta.getResourceId(R.styleable.FileChooser_fileChooserDialogStyle, R.style.FileChooserDialogStyle));
        ta.recycle();

        this._adapter = new SmbDirAdapter(getBaseContext(), EXECUTOR, this._rowLayoutRes != null ? this._rowLayoutRes : R.layout.li_row_textview, this._dateFormat);
        if (this._adapterSetter != null) {
            this._adapterSetter.apply(this._adapter);
        }
        builder.setAdapter(this._adapter, this);

        if (_currentDir == null) {
            this.setStartFile(null);
        }

        this.refreshDirs(true);

        if (!this._disableTitle) {
            if (this._titleRes == null) builder.setTitle(this._title);
            else builder.setTitle(this._titleRes);
        }

        if (this._iconRes != null) {
            builder.setIcon(this._iconRes);
        }

        if (this._layoutRes != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setView(this._layoutRes);
            }
        }

        if (this._dirOnly) {
            final DialogInterface.OnClickListener listener = (dialog, which) -> {
                if (SmbFileChooserDialog.this._onChosenListener != null) {
                    if (SmbFileChooserDialog.this._dirOnly) {
                        SmbFileChooserDialog.this._onChosenListener.onChoosePath(SmbFileChooserDialog.this._currentDir.getPath(), SmbFileChooserDialog.this._currentDir);
                    }
                }
            };

            if (this._okRes == null) builder.setPositiveButton(this._ok, listener);
            else builder.setPositiveButton(this._okRes, listener);
        }

        if (this._dirOnly || this._enableMultiple) {
            final DialogInterface.OnClickListener listener = (dialog, which) -> {
                if (SmbFileChooserDialog.this._enableMultiple) {
                    if (SmbFileChooserDialog.this._adapter.isAnySelected()) {
                        if (SmbFileChooserDialog.this._adapter.isOneSelected()) {
                            if (SmbFileChooserDialog.this._onChosenListener != null) {
                                final SmbFile selected = _adapter.getSelected().get(0);
                                SmbFileChooserDialog.this._onChosenListener.onChoosePath(selected.getPath(), selected);
                            }
                        } else {
                            if (SmbFileChooserDialog.this._onSelectedListener != null) {
                                SmbFileChooserDialog.this._onSelectedListener.onSelectFiles(_adapter.getSelected());
                            }
                        }
                    }
                } else if (SmbFileChooserDialog.this._onChosenListener != null) {
                    SmbFileChooserDialog.this._onChosenListener.onChoosePath(SmbFileChooserDialog.this._currentDir.getPath(), SmbFileChooserDialog.this._currentDir);
                }

                SmbFileChooserDialog.this._alertDialog.dismiss();
            };

            if (this._okRes == null) builder.setPositiveButton(this._ok, listener);
            else builder.setPositiveButton(this._okRes, listener);
        }

        final DialogInterface.OnClickListener listener = this._negativeListener != null ? this._negativeListener : (dialog, which) -> dialog.cancel();

        if (this._negativeRes == null) builder.setNegativeButton(this._negative, listener);
        else builder.setNegativeButton(this._negativeRes, listener);

        if (this._onCancelListener != null) {
            builder.setOnCancelListener(this._onCancelListener);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (this._onDismissListener != null) {
                builder.setOnDismissListener(this._onDismissListener);
            }
        }

        builder.setCancelable(this._cancelable)
            .setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    SmbFileChooserDialog.this._onBackPressed.onBackPressed((AlertDialog) dialog);
                }
                return true;
            });

        this._alertDialog = builder.create();

        this._alertDialog.setOnDismissListener(dialog -> EXECUTOR.shutdownNow());

        this._alertDialog.setCanceledOnTouchOutside(this._cancelOnTouchOutside);
        this._alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                if (_enableMultiple && !_dirOnly) {
                    _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.INVISIBLE);
                }

                if (_enableDpad) {
                    _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setBackgroundResource(R.drawable.listview_item_selector);
                    _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundResource(R.drawable.listview_item_selector);
                    _alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundResource(R.drawable.listview_item_selector);
                }

                if (!SmbFileChooserDialog.this._dismissOnButtonClick) {
                    Button negative = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                    negative.setOnClickListener(v -> {
                        if (SmbFileChooserDialog.this._negativeListener != null) {
                            SmbFileChooserDialog.this._negativeListener.onClick(SmbFileChooserDialog.this._alertDialog, AlertDialog.BUTTON_NEGATIVE);
                        }
                    });

                    if (SmbFileChooserDialog.this._dirOnly) {
                        Button positive = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                        positive.setOnClickListener(v -> {
                            if (SmbFileChooserDialog.this._enableMultiple) {
                                if (SmbFileChooserDialog.this._adapter.isAnySelected()) {
                                    if (SmbFileChooserDialog.this._adapter.isOneSelected()) {
                                        if (SmbFileChooserDialog.this._onChosenListener != null) {
                                            final SmbFile selected = _adapter.getSelected().get(0);
                                            SmbFileChooserDialog.this._onChosenListener.onChoosePath(selected.getPath(), selected);
                                        }
                                    } else {
                                        if (SmbFileChooserDialog.this._onSelectedListener != null) {
                                            SmbFileChooserDialog.this._onSelectedListener.onSelectFiles(_adapter.getSelected());
                                        }
                                    }
                                }
                            } else if (SmbFileChooserDialog.this._onChosenListener != null) {
                                SmbFileChooserDialog.this._onChosenListener.onChoosePath(SmbFileChooserDialog.this._currentDir.getPath(), SmbFileChooserDialog.this._currentDir);
                            }
                        });
                    }
                }

                // Root view (FrameLayout) of the ListView in the AlertDialog.
                final int rootId = getResources().getIdentifier("contentPanel", "id", "android");
                final ViewGroup root = ((AlertDialog) dialog).findViewById(rootId);
                // In case the id was changed or not found.
                if (root == null) return;

                ViewGroup.MarginLayoutParams params;
                if (root instanceof LinearLayout) {
                    params = new LinearLayout.LayoutParams(MATCH_PARENT, UiUtil.dip2px(48));
                } else {
                    params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, CENTER);
                }

                View v = root.getChildAt(0);
                root.removeView(v);
                final SwipeRefreshLayout swipeRefreshLayout = new SwipeRefreshLayout(getBaseContext());
                swipeRefreshLayout.addView(v, params);
                root.addView(swipeRefreshLayout, params);

                final ProgressBar progressBar = new ProgressBar(getBaseContext(), null, android.R.attr.progressBarStyleLarge);
                progressBar.setIndeterminate(true);
                progressBar.setBackgroundColor(0x00000000);
                root.addView(progressBar, params);
                progressBar.bringToFront();
                SmbFileChooserDialog.this.progressBar = progressBar;

                swipeRefreshLayout.setOnRefreshListener(() -> {
                    if (progressBar.getVisibility() != VISIBLE) {
                        refreshDirs(false);
                    }
                    swipeRefreshLayout.setRefreshing(false);
                });


                if (SmbFileChooserDialog.this._enableOptions) {
                    final Button options = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
                    
                    final int buttonColor = options.getCurrentTextColor();
                    final PorterDuffColorFilter filter = new PorterDuffColorFilter(buttonColor, PorterDuff.Mode.SRC_IN);

                    options.setText("");
                    options.setVisibility(VISIBLE);
                    final Drawable drawable = ContextCompat.getDrawable(getBaseContext(),
                        SmbFileChooserDialog.this._optionsIconRes != null ? SmbFileChooserDialog.this._optionsIconRes : R.drawable.ic_menu_24dp);
                    if (drawable != null) {
                        drawable.setColorFilter(filter);
                        options.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
                    } else {
                        options.setCompoundDrawablesWithIntrinsicBounds(
                            SmbFileChooserDialog.this._optionsIconRes != null ? SmbFileChooserDialog.this._optionsIconRes : R.drawable.ic_menu_24dp, 0, 0, 0);
                    }

                    final class Integer {
                        int Int = 0;
                    }
                    final Integer scroll = new Integer();

                    SmbFileChooserDialog.this._list.addOnLayoutChangeListener((v12, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        if (_list.getChildAt(0) == null) return;
                        int oldHeight = oldBottom - oldTop;
                        if (v12.getHeight() != oldHeight) {
                            int offset = oldHeight - v12.getHeight();
                            int newScroll = getListYScroll(_list);
                            if (scroll.Int != newScroll) offset += scroll.Int - newScroll;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                SmbFileChooserDialog.this._list.scrollListBy(offset);
                            } else {
                                SmbFileChooserDialog.this._list.scrollBy(0, offset);
                            }
                        }
                    });

                    final Runnable showOptions = new Runnable() {
                        @Override
                        public void run() {
                            final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) SmbFileChooserDialog.this._list.getLayoutParams();
                            if (SmbFileChooserDialog.this._options.getHeight() == 0) {
                                ViewTreeObserver viewTreeObserver = SmbFileChooserDialog.this._options.getViewTreeObserver();
                                viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (SmbFileChooserDialog.this._options.getHeight() <= 0) {
                                            return false;
                                        }
                                        SmbFileChooserDialog.this._options.getViewTreeObserver().removeOnPreDrawListener(this);
                                        Handler handler = new Handler();
                                        handler.postDelayed(() -> {
                                            scroll.Int = getListYScroll(SmbFileChooserDialog.this._list);
                                            if (SmbFileChooserDialog.this._options.getParent() instanceof LinearLayout) {
                                                params.height = ((LinearLayout) SmbFileChooserDialog.this._options.getParent()).getHeight()
                                                    - SmbFileChooserDialog.this._options.getHeight()
                                                    - (SmbFileChooserDialog.this._pathView != null && SmbFileChooserDialog.this._pathView.getVisibility()
                                                    == VISIBLE ? SmbFileChooserDialog.this._pathView.getHeight() : 0);
                                            } else {
                                                params.bottomMargin = SmbFileChooserDialog.this._options.getHeight();
                                            }
                                            SmbFileChooserDialog.this._list.setLayoutParams(params);
                                            SmbFileChooserDialog.this._options.setVisibility(VISIBLE);
                                            SmbFileChooserDialog.this._options.requestFocus();
                                        }, 100); // Just to make sure that the View has been drawn, so the transition is smoother.
                                        return true;
                                    }
                                });
                            } else {
                                scroll.Int = getListYScroll(SmbFileChooserDialog.this._list);
                                if (SmbFileChooserDialog.this._options.getParent() instanceof LinearLayout) {
                                    params.height = ((LinearLayout) SmbFileChooserDialog.this._options.getParent()).getHeight()
                                        - SmbFileChooserDialog.this._options.getHeight()
                                        - (SmbFileChooserDialog.this._pathView != null && SmbFileChooserDialog.this._pathView.getVisibility()
                                        == VISIBLE ? SmbFileChooserDialog.this._pathView.getHeight() : 0);
                                } else {
                                    params.bottomMargin = SmbFileChooserDialog.this._options.getHeight();
                                }
                                SmbFileChooserDialog.this._list.setLayoutParams(params);
                                SmbFileChooserDialog.this._options.setVisibility(VISIBLE);
                                SmbFileChooserDialog.this._options.requestFocus();
                            }
                        }
                    };
                    final Runnable hideOptions = () -> {
                        scroll.Int = getListYScroll(SmbFileChooserDialog.this._list);
                        SmbFileChooserDialog.this._options.setVisibility(View.INVISIBLE);
                        SmbFileChooserDialog.this._options.clearFocus();
                        ViewGroup.MarginLayoutParams params1 = (ViewGroup.MarginLayoutParams) SmbFileChooserDialog.this._list.getLayoutParams();
                        if (SmbFileChooserDialog.this._options.getParent() instanceof LinearLayout) {
                            params1.height = ((LinearLayout) SmbFileChooserDialog.this._options.getParent()).getHeight()
                                - (SmbFileChooserDialog.this._pathView != null && SmbFileChooserDialog.this._pathView.getVisibility()
                                == VISIBLE ? SmbFileChooserDialog.this._pathView.getHeight() : 0);
                        } else {
                            params1.bottomMargin = 0;
                        }
                        SmbFileChooserDialog.this._list.setLayoutParams(params1);
                    };

                    options.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View v) {
                            if (SmbFileChooserDialog.this._options == null) {
                                // region Draw options view. (this only happens the first time one clicks on options)
                                // Create options view.
                                final FrameLayout options = new FrameLayout(getBaseContext());
                                //options.setBackgroundColor(0x60000000);
                                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, BOTTOM);
                                root.addView(options, params);

                                options.setOnClickListener(null);
                                options.setVisibility(View.INVISIBLE);
                                SmbFileChooserDialog.this._options = options;

                                // Create a button for the option to create a new directory/folder.
                                final Button createDir = new Button(getBaseContext(), null, R.style.FileChooserButtonStyle);
                                if (SmbFileChooserDialog.this._createDirRes == null)
                                    createDir.setText(SmbFileChooserDialog.this._createDir);
                                else createDir.setText(SmbFileChooserDialog.this._createDirRes);
                                createDir.setTextColor(buttonColor);
                                final Drawable plus = ContextCompat.getDrawable(getBaseContext(),
                                    SmbFileChooserDialog.this._createDirIconRes != null ? SmbFileChooserDialog.this._createDirIconRes : R.drawable.ic_add_24dp);
                                if (plus != null) {
                                    plus.setColorFilter(filter);
                                    createDir.setCompoundDrawablesWithIntrinsicBounds(plus, null, null, null);
                                } else {
                                    createDir.setCompoundDrawablesWithIntrinsicBounds(
                                        SmbFileChooserDialog.this._createDirIconRes != null ? SmbFileChooserDialog.this._createDirIconRes : R.drawable.ic_add_24dp, 0, 0, 0);
                                }
                                if (SmbFileChooserDialog.this._enableDpad) {
                                    createDir.setBackgroundResource(R.drawable.listview_item_selector);
                                }
                                params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, START | CENTER_VERTICAL);
                                params.leftMargin = 10;
                                options.addView(createDir, params);

                                // Create a button for the option to delete a file.
                                final Button delete = new Button(getBaseContext(), null, R.style.FileChooserButtonStyle);
                                if (SmbFileChooserDialog.this._deleteRes == null)
                                    delete.setText(SmbFileChooserDialog.this._delete);
                                else delete.setText(SmbFileChooserDialog.this._deleteRes);
                                delete.setTextColor(buttonColor);
                                final Drawable bin = ContextCompat.getDrawable(getBaseContext(),
                                    SmbFileChooserDialog.this._deleteIconRes != null ? SmbFileChooserDialog.this._deleteIconRes : R.drawable.ic_delete_24dp);
                                if (bin != null) {
                                    bin.setColorFilter(filter);
                                    delete.setCompoundDrawablesWithIntrinsicBounds(bin, null, null, null);
                                } else {
                                    delete.setCompoundDrawablesWithIntrinsicBounds(
                                        SmbFileChooserDialog.this._deleteIconRes != null ? SmbFileChooserDialog.this._deleteIconRes : R.drawable.ic_delete_24dp, 0, 0, 0);
                                }
                                if (SmbFileChooserDialog.this._enableDpad) {
                                    delete.setBackgroundResource(R.drawable.listview_item_selector);
                                }
                                params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, END | CENTER_VERTICAL);
                                params.rightMargin = 10;
                                options.addView(delete, params);

                                // Event Listeners.
                                createDir.setOnClickListener(new View.OnClickListener() {
                                    private EditText input = null;

                                    @Override
                                    public void onClick(final View v1) {
                                        hideOptions.run();
                                        final Future<String> futureNewFile = EXECUTOR.submit(() -> {
                                            try {
                                                SmbFile newFolder = new SmbFile(SmbFileChooserDialog.this._currentDir.getPath() + "/New folder", _smbContext);
                                                for (int i = 1; newFolder.exists(); i++)
                                                    newFolder = new SmbFile(SmbFileChooserDialog.this._currentDir.getPath() + "/New folder (" + i + ')', _smbContext);
                                                final String name = newFolder.getName();
                                                runOnUiThread(() -> {
                                                    if (input != null) {
                                                        input.setText(name);
                                                    }
                                                });
                                                return name;
                                            } catch (MalformedURLException | SmbException e) {
                                                e.printStackTrace();
                                                runOnUiThread(() -> {
                                                    if (input != null) {
                                                        _exceptionHandler.handleException(e);
                                                    }
                                                });
                                                return "";
                                            }
                                        });

                                        if (SmbFileChooserDialog.this._newFolderView == null) {
                                            // region Draw a view with input to create new folder. (this only happens the first time one clicks on New folder)
                                            try {
                                                //noinspection ConstantConditions
                                                ((AlertDialog) dialog).getWindow().clearFlags(FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM);
                                                //noinspection ConstantConditions
                                                ((AlertDialog) dialog).getWindow().setSoftInputMode(SOFT_INPUT_STATE_VISIBLE);
                                            } catch (NullPointerException e) {
                                                e.printStackTrace();
                                                _exceptionHandler.handleException(e);
                                            }

                                            // A semitransparent background overlay.
                                            final FrameLayout overlay = new FrameLayout(getBaseContext());
                                            overlay.setBackgroundColor(0x60ffffff);
                                            overlay.setScrollContainer(true);
                                            ViewGroup.MarginLayoutParams params = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, CENTER);
                                            root.addView(overlay, params);

                                            overlay.setOnClickListener(null);
                                            overlay.setVisibility(View.INVISIBLE);
                                            SmbFileChooserDialog.this._newFolderView = overlay;

                                            // A LynearLayout and a pair of Spaces to center vews.
                                            LinearLayout linearLayout = new LinearLayout(getBaseContext());
                                            params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, CENTER);
                                            overlay.addView(linearLayout, params);

                                            // The Space on the left.
                                            Space leftSpace = new Space(getBaseContext());
                                            params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 2);
                                            linearLayout.addView(leftSpace, params);

                                            // A solid holder view for the EditText and Buttons.
                                            final LinearLayout holder = new LinearLayout(getBaseContext());
                                            holder.setOrientation(LinearLayout.VERTICAL);
                                            holder.setBackgroundColor(0xffffffff);
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                holder.setElevation(25f);
                                            } else {
                                                ViewCompat.setElevation(holder, 25);
                                            }
                                            params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 5);
                                            linearLayout.addView(holder, params);

                                            // The Space on the right.
                                            Space rightSpace = new Space(getBaseContext());
                                            params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 2);
                                            linearLayout.addView(rightSpace, params);

                                            // An EditText to input the new folder name.
                                            final EditText input = new EditText(getBaseContext());
                                            input.setSelectAllOnFocus(true);
                                            input.setSingleLine(true);
                                            // There should be no suggestions, but... :)
                                            input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                                            input.setFilters(new InputFilter[]{SmbFileChooserDialog.this._newFolderFilter != null ? SmbFileChooserDialog.this._newFolderFilter : new NewFolderFilter()});
                                            input.setGravity(CENTER_HORIZONTAL);
                                            params = new LinearLayout.LayoutParams(256, WRAP_CONTENT);
                                            holder.addView(input, params);

                                            this.input = input;

                                            // A horizontal LinearLayout to hold buttons
                                            final FrameLayout buttons = new FrameLayout(getBaseContext());
                                            params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                            holder.addView(buttons, params);

                                            // The Cancel button.
                                            final Button cancel = new Button(getBaseContext(), null, android.R.attr.buttonBarButtonStyle);
                                            if (SmbFileChooserDialog.this._newFolderCancelRes == null)
                                                cancel.setText(SmbFileChooserDialog.this._newFolderCancel);
                                            else
                                                cancel.setText(SmbFileChooserDialog.this._newFolderCancelRes);
                                            cancel.setTextColor(buttonColor);
                                            if (SmbFileChooserDialog.this._enableDpad) {
                                                cancel.setBackgroundResource(R.drawable.listview_item_selector);
                                            }
                                            params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, START);
                                            buttons.addView(cancel, params);

                                            // The OK button.
                                            final Button ok = new Button(getBaseContext(), null, android.R.attr.buttonBarButtonStyle);
                                            if (SmbFileChooserDialog.this._newFolderOkRes == null)
                                                ok.setText(SmbFileChooserDialog.this._newFolderOk);
                                            else
                                                ok.setText(SmbFileChooserDialog.this._newFolderOkRes);
                                            ok.setTextColor(buttonColor);
                                            if (SmbFileChooserDialog.this._enableDpad) {
                                                ok.setBackgroundResource(R.drawable.listview_item_selector);
                                            }
                                            params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, END);
                                            buttons.addView(ok, params);

                                            // Event Listeners.
                                            input.setOnEditorActionListener((v23, actionId, event) -> {
                                                if (actionId == EditorInfo.IME_ACTION_DONE) {
                                                    UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                    if (!SmbFileChooserDialog.this._enableDpad) {
                                                        SmbFileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                        overlay.setVisibility(View.INVISIBLE);
                                                        overlay.clearFocus();
                                                    } else {
                                                        input.requestFocus();
                                                    }
                                                    return true;
                                                }
                                                return false;
                                            });
                                            cancel.setOnClickListener(v22 -> {
                                                UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                overlay.setVisibility(View.INVISIBLE);
                                                overlay.clearFocus();
                                                if (SmbFileChooserDialog.this._enableDpad) {
                                                    SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(true);
                                                    SmbFileChooserDialog.this._list.setFocusable(true);
                                                }
                                            });
                                            ok.setOnClickListener(v2 -> {
                                                SmbFileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                overlay.setVisibility(View.INVISIBLE);
                                                overlay.clearFocus();
                                                if (SmbFileChooserDialog.this._enableDpad) {
                                                    SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(true);
                                                    SmbFileChooserDialog.this._list.setFocusable(true);
                                                }
                                            });
                                            // endregion
                                        }

                                        if (SmbFileChooserDialog.this._newFolderView.getVisibility() == View.INVISIBLE) {
                                            SmbFileChooserDialog.this._newFolderView.setVisibility(VISIBLE);
                                            if (SmbFileChooserDialog.this._enableDpad) {
                                                SmbFileChooserDialog.this._newFolderView.requestFocus();
                                                SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(false);
                                                SmbFileChooserDialog.this._list.setFocusable(false);
                                            }
                                            if (this.input != null) {

                                                try {
                                                    if (!futureNewFile.isDone())
                                                        this.input.setText(futureNewFile.get());
                                                } catch (InterruptedException | ExecutionException e) {
                                                    e.printStackTrace();
                                                    _exceptionHandler.handleException(e);
                                                    this.input.setText("");
                                                }
                                            }
                                        } else {
                                            SmbFileChooserDialog.this._newFolderView.setVisibility(View.INVISIBLE);
                                            if (SmbFileChooserDialog.this._enableDpad) {
                                                SmbFileChooserDialog.this._newFolderView.clearFocus();
                                                SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(true);
                                                SmbFileChooserDialog.this._list.setFocusable(true);
                                            }
                                        }
                                    }
                                });
                                delete.setOnClickListener(v1 -> {
                                    //Toast.makeText(getBaseContext(), "delete clicked", Toast.LENGTH_SHORT).show();
                                    hideOptions.run();

                                    if (SmbFileChooserDialog.this._chooseMode == CHOOSE_MODE_SELECT_MULTIPLE) {
                                        try {
                                            final Queue<SmbFile> parents = new ArrayDeque<>();
                                            EXECUTOR.submit((Callable<Void>) () -> {
                                                final String parentPath = SmbFileChooserDialog.this._currentDir.getParent();
                                                SmbFile current = new SmbFile(parentPath, _smbContext);
                                                while (!current.equals(SmbFileChooserDialog.this._rootDir)) {
                                                    parents.add(current);
                                                    final String parent = current.getParent();
                                                    current = new SmbFile(parent, _smbContext);
                                                }
                                                return null;
                                            }).get();

                                            for (SmbFile file : SmbFileChooserDialog.this._adapter.getSelected()) {
                                                deleteFile(file);
                                            }
                                            SmbFileChooserDialog.this._adapter.clearSelected();

                                            SmbFile currentDir = EXECUTOR.submit(() -> {
                                                if (!SmbFileChooserDialog.this._currentDir.exists()) {
                                                    SmbFile parent;

                                                    while ((parent = parents.poll()) != null) {
                                                        if (parent.exists()) break;
                                                    }

                                                    if (parent != null && parent.exists()) {
                                                        SmbFileChooserDialog.this._currentDir = parent;
                                                    } else {
                                                        SmbFileChooserDialog.this._currentDir = SmbFileChooserDialog.this._rootDir;
                                                    }
                                                }
                                                return SmbFileChooserDialog.this._currentDir;
                                            }).get();

                                            boolean scrollTop = !SmbFileChooserDialog.this._currentDir.equals(currentDir);
                                            SmbFileChooserDialog.this._currentDir = currentDir;

                                            refreshDirs(scrollTop);
                                        } catch (InterruptedException | ExecutionException e) {
                                            e.printStackTrace();
                                            _exceptionHandler.handleException(e);
                                        }

                                        SmbFileChooserDialog.this._chooseMode = CHOOSE_MODE_NORMAL;
                                        return;
                                    }

                                    SmbFileChooserDialog.this._chooseMode = SmbFileChooserDialog.this._chooseMode != CHOOSE_MODE_DELETE ? CHOOSE_MODE_DELETE : CHOOSE_MODE_NORMAL;
                                    if (SmbFileChooserDialog.this._deleteMode == null) {
                                        SmbFileChooserDialog.this._deleteMode = () -> {
                                            if (SmbFileChooserDialog.this._chooseMode == CHOOSE_MODE_DELETE) {
                                                final int color1 = 0x80ff0000;
                                                final PorterDuffColorFilter red = new PorterDuffColorFilter(color1, PorterDuff.Mode.SRC_IN);
                                                _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).getCompoundDrawables()[0].setColorFilter(red);
                                                _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(color1);
                                                delete.getCompoundDrawables()[0].setColorFilter(red);
                                                delete.setTextColor(color1);
                                            } else {
                                                _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).getCompoundDrawables()[0].clearColorFilter();
                                                _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(buttonColor);
                                                delete.getCompoundDrawables()[0].clearColorFilter();
                                                delete.setTextColor(buttonColor);
                                            }
                                        };
                                    }
                                    SmbFileChooserDialog.this._deleteMode.run();
                                });
                                // endregion
                            }

                            if (SmbFileChooserDialog.this._options.getVisibility() == VISIBLE) {
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

        _list.setOnTouchListener((v, event) -> !_isScrollable && event.getAction() == MotionEvent.ACTION_MOVE);

        if (this._enableDpad) {
            this._list.setSelector(R.drawable.listview_item_selector);
            this._list.setDrawSelectorOnTop(true);
            this._list.setItemsCanFocus(true);
            this._list.setOnItemSelectedListener(this);
            this._alertDialog.setOnKeyListener(this);
        }
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public SmbFileChooserDialog show() {
        if (_terminate) {
            _terminate = false;
            return this;
        }

        if (_alertDialog == null || _list == null) {
            throw new RuntimeException("Dialog has not been built yet! (call .build() before .show())");
        }

        // Check for permissions if SDK version is >= 23
        if (Build.VERSION.SDK_INT >= 23) {
            int readPermissionCheck = ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            int writePermissionCheck = _enableOptions ? ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) : PERMISSION_GRANTED;

            if (readPermissionCheck != PERMISSION_GRANTED && writePermissionCheck != PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) getBaseContext(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    111);
            } else if (readPermissionCheck != PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) getBaseContext(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    222);
            } else if (writePermissionCheck != PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) getBaseContext(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    333);
            } else {
                if (!_terminate) _alertDialog.show();
                return this;
            }

            readPermissionCheck = ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            writePermissionCheck = _enableOptions ? ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) : PERMISSION_GRANTED;

            if (readPermissionCheck == PERMISSION_GRANTED && writePermissionCheck == PERMISSION_GRANTED) {
                if (!_terminate) _alertDialog.show();
            }

            return this;
        } else {
            _alertDialog.show();
        }
        return this;
    }

    private void displayPath(@Nullable String path) {
        if (_pathView == null) {
            final int rootId = getResources().getIdentifier("contentPanel", "id", "android");
            final ViewGroup root = ((AlertDialog) _alertDialog).findViewById(rootId);
            // In case the id was changed or not found.
            if (root == null) return;

            ViewGroup.MarginLayoutParams params;
            if (root instanceof LinearLayout) {
                params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            } else {
                params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, TOP);
            }

            _pathView = new TextView(getBaseContext());
            _pathView.setTextSize(12);
            _pathView.setLines(1);
            _pathView.setTextColor(0x40000000);
            _pathView.setPadding(
                UiUtil.dip2px(2),
                UiUtil.dip2px(5),
                UiUtil.dip2px(2),
                UiUtil.dip2px(2));
            _pathView.setBackgroundColor(0xffffffff);
            root.addView(_pathView, 0, params);

            _pathView.bringToFront();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                _pathView.setElevation(2f);
            } else {
                ViewCompat.setElevation(_pathView, 2);
            }

            if (_pathViewCallback != null) {
                _pathViewCallback.customize(_pathView);
            }
        }

        if (path == null) {
            _pathView.setVisibility(GONE);

            ViewGroup.MarginLayoutParams param = ((ViewGroup.MarginLayoutParams) _list.getLayoutParams());
            if (_pathView.getParent() instanceof LinearLayout) {
                param.height = ((LinearLayout) _pathView.getParent()).getHeight() - (_options != null && _options.getVisibility() == VISIBLE ? _options.getHeight() : 0);
            } else {
                param.topMargin = 0;
            }
            _list.setLayoutParams(param);
        } else {
            if (path.contains("smb://")) path = path.substring(5);
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
                        _pathView.getViewTreeObserver().removeOnPreDrawListener(this);
                        if (_pathView.getParent() instanceof LinearLayout) {
                            param.height = ((LinearLayout) _pathView.getParent()).getHeight() - _pathView.getHeight() - (_options != null && _options.getVisibility() == VISIBLE ? _options.getHeight() : 0);
                        } else {
                            param.topMargin = _pathView.getHeight();
                        }
                        _list.setLayoutParams(param);
                        return true;
                    }
                });
            } else {
                if (_pathView.getParent() instanceof LinearLayout) {
                    param.height = ((LinearLayout) _pathView.getParent()).getHeight() - _pathView.getHeight() - (_options != null && _options.getVisibility() == VISIBLE ? _options.getHeight() : 0);
                } else {
                    param.topMargin = _pathView.getHeight();
                }
                _list.setLayoutParams(param);
            }
        }
    }

    private void listDirs(final boolean scrollToTop) {
        if (progressBar != null) progressBar.setVisibility(VISIBLE);
        _isScrollable = false;
        AtomicBoolean isRoot = new AtomicBoolean(false);
        EXECUTOR.execute(() -> {
            try {
                _entries.clear();

                // Add the ".." entry
                final String parent = _currentDir.getParent();
                if (parent != null && !parent.equalsIgnoreCase("smb://")) {
                    _entries.add(new SmbFile("smb://..", _smbContext) {
                        @Override
                        public boolean isDirectory() {
                            return true;
                        }

                        @Override
                        public boolean isHidden() {
                            return false;
                        }
                    });
                    isRoot.set(true);
                }

                // Get files
                SmbFile[] files = _currentDir.listFiles(_fileFilter);

                if (files == null) return;

                List<SmbFile> dirList = new LinkedList<>();
                List<SmbFile> fileList = new LinkedList<>();

                for (SmbFile f : files) {
                    if (f.isDirectory()) {
                        dirList.add(f);
                    } else {
                        fileList.add(f);
                    }
                }

                SmbFileChooserDialog.this.sortByName(dirList);
                SmbFileChooserDialog.this.sortByName(fileList);

                _entries.addAll(dirList);
                _entries.addAll(fileList);
            } catch (SmbException | MalformedURLException e) {
                e.printStackTrace();
                runOnUiThread(() -> _exceptionHandler.handleException(e, IExceptionHandler.ExceptionId.FAILED_TO_LOAD_FILES));
            } finally {
                runOnUiThread(() -> {
                    _adapter.setEntries(_entries);
                    _isScrollable = true;
                    if (scrollToTop) SmbFileChooserDialog.this._list.setSelection(0);
                    if (progressBar != null) progressBar.setVisibility(GONE);
                    if (_alertDialog != null && _displayPath) {
                        if (isRoot.get()) {
                            displayPath(_currentDir.getPath());
                        } else {
                            displayPath(null);
                        }
                    }
                });
            }
        });
    }

    void sortByName(@NonNull List<SmbFile> list) {
        Collections.sort(list, (f1, f2) -> f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase()));
    }

    /**
     * @deprecated better use listDirs as it sorts directories and files separately
     */
    @Deprecated
    private void listDirsUncategorised(final boolean scrollToTop) {
        if (progressBar != null) progressBar.setVisibility(VISIBLE);
        _isScrollable = false;
        AtomicBoolean isRoot = new AtomicBoolean(false);
        EXECUTOR.execute(() -> {
            try {
                _entries.clear();

                // Get files
                SmbFile[] files = _currentDir.listFiles(_fileFilter);

                if (files != null) {
                    _entries.addAll(Arrays.asList(files));
                }

                sortByName(_entries);

                // Add the ".." entry
                final String parent = _currentDir.getParent();
                if (parent != null && !parent.equalsIgnoreCase("smb://")) {
                    _entries.add(0, new SmbFile("..", _smbContext) {
                        @Override
                        public boolean isDirectory() {
                            return true;
                        }

                        @Override
                        public boolean isHidden() {
                            return false;
                        }
                    });
                    isRoot.set(true);
                }
            } catch (MalformedURLException | SmbException e) {
                e.printStackTrace();
                if (progressBar != null) runOnUiThread(() -> {
                    _exceptionHandler.handleException(e);
                    Toast.makeText(getBaseContext(), "Failed to load files!", Toast.LENGTH_LONG).show();
                });
            } finally {
                runOnUiThread(() -> {
                    _adapter.setEntries(_entries);
                    _isScrollable = true;
                    if (scrollToTop) SmbFileChooserDialog.this._list.setSelection(0);
                    if (progressBar != null) progressBar.setVisibility(GONE);
                    if (_alertDialog != null && _displayPath) {
                        if (isRoot.get()) {
                            displayPath(_currentDir.getPath());
                        } else {
                            displayPath(null);
                        }
                    }
                });
            }
        });
    }

    private void createNewDirectory(@NonNull final String name) {
        EXECUTOR.execute(() -> {
            try {
                final SmbFile newDir = new SmbFile(SmbFileChooserDialog.this._currentDir.getPath() + "/" + name, SmbFileChooserDialog.this._smbContext);
                if (!newDir.exists()) {
                    newDir.mkdirs();
                    runOnUiThread(() -> refreshDirs(false));
                }
            } catch (MalformedURLException | SmbException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    _exceptionHandler.handleException(e);
                    Toast.makeText(getBaseContext(), "Couldn't create folder " + name + " at " + SmbFileChooserDialog.this._currentDir, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // todo: maybe ask for confirmation? (inside an AlertDialog.. Ironic, I know)
    private Runnable _deleteMode;

    private void deleteFile(@NonNull final SmbFile file) {
        progressBar.setVisibility(VISIBLE);
        EXECUTOR.execute(() -> {
            try {
                file.delete();
            } catch (final SmbException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    _exceptionHandler.handleException(e);
                    Toast.makeText(getBaseContext(), "Couldn't delete " + file.getName() + " at " + file.getPath(), Toast.LENGTH_LONG).show();
                });
            }
            runOnUiThread(() -> progressBar.setVisibility(GONE));
        });
    }

    @Override
    public void onItemClick(@Nullable final AdapterView<?> parent, @NonNull final View list, final int position, final long id) {
        try {
            if (position < 0 || position >= _entries.size()) return;

            View focus = _list;
            Triple<SmbFile, Boolean, String> triple = EXECUTOR.submit(() -> {
                SmbFile file = _entries.get(position);
                if (file.getName().equals("../") || file.getName().equals("..")) {
                    final String parentPath = _currentDir.getParent();
                    final SmbFile f = new SmbFile(parentPath, _smbContext);
                    if (_folderNavUpCB == null) _folderNavUpCB = _defaultNavUpCB;
                    if (_folderNavUpCB.canUpTo(f)) {
                        _currentDir = f;
                        _chooseMode = _chooseMode == CHOOSE_MODE_DELETE ? CHOOSE_MODE_NORMAL : _chooseMode;
                        if (_deleteMode != null) _deleteMode.run();
                        lastSelected = false;
                        return new Triple<SmbFile, Boolean, String>(null, true, null);
                    }
                }
                return new Triple<>(file, file.isDirectory(), file.getPath());
            }).get();

            final SmbFile file = triple.getFirst();
            final boolean isDirectory = triple.getSecond();
            final String path = triple.getThird();
            boolean scrollToTop = false;

            if (file != null) {
                switch (_chooseMode) {
                    case CHOOSE_MODE_NORMAL:
                        if (isDirectory) {
                            if (_folderNavToCB == null) _folderNavToCB = _defaultNavToCB;
                            if (_folderNavToCB.canNavigate(file)) {
                                _currentDir = file;
                                scrollToTop = true;
                            }
                        } else if ((!_dirOnly) && _onChosenListener != null) {
                            _onChosenListener.onChoosePath(path, file);
                            if (_dismissOnButtonClick) _alertDialog.dismiss();
                        }
                        lastSelected = false;
                        break;
                    case CHOOSE_MODE_SELECT_MULTIPLE:
                        if (isDirectory) {
                            if (_folderNavToCB == null) _folderNavToCB = _defaultNavToCB;
                            if (_folderNavToCB.canNavigate(file)) {
                                _currentDir = file;
                                scrollToTop = true;
                            }
                        } else {
                            if (_enableDpad) focus = _alertDialog.getCurrentFocus();
                            _adapter.selectItem(position);
                            if (!_adapter.isAnySelected()) {
                                _chooseMode = CHOOSE_MODE_NORMAL;
                                if (!_dirOnly)
                                    _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.INVISIBLE);
                            }
                        }
                        break;
                    case CHOOSE_MODE_DELETE:
                        deleteFile(file);
                        _chooseMode = CHOOSE_MODE_NORMAL;
                        if (_deleteMode != null) _deleteMode.run();
                        break;
                    default:
                        // ERROR! It shouldn't get here...
                        break;
                }
            } else {
                scrollToTop = isDirectory;
            }

            refreshDirs(scrollToTop);
            if (_enableDpad) {
                if (focus == null) _list.requestFocus();
                else focus.requestFocus();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            _exceptionHandler.handleException(e);
        }
    }

    @Override
    public boolean onItemLongClick(@Nullable final AdapterView<?> parent, @NonNull final View list, final int position, final long id) {
        try {
            if (EXECUTOR.submit(() -> {
                SmbFile file = _entries.get(position);
                return !file.getName().equals("../") && !file.getName().equals("..") && (_allowSelectDir || !file.isDirectory());
            }).get()) {
                _adapter.selectItem(position);
                if (!_adapter.isAnySelected()) {
                    _chooseMode = CHOOSE_MODE_NORMAL;
                    if (!_dirOnly)
                        _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.INVISIBLE);
                } else {
                    _chooseMode = CHOOSE_MODE_SELECT_MULTIPLE;
                    if (!_dirOnly)
                        _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(VISIBLE);
                }
                if (SmbFileChooserDialog.this._deleteMode != null)
                    SmbFileChooserDialog.this._deleteMode.run();
            }
            return true;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            _exceptionHandler.handleException(e);
        }
        return false;
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

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (lastSelected && _list.hasFocus()) {
                lastSelected = false;
                if (_options != null && _options.getVisibility() == VISIBLE) {
                    _options.requestFocus();
                } else {
                    _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).requestFocus();
                }
                return true;
            }

        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (_alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).hasFocus()) {
                if (_options != null && _options.getVisibility() == VISIBLE) {
                    _options.requestFocus(View.FOCUS_RIGHT);
                } else {
                    _list.requestFocus();
                    lastSelected = true;
                }
                return true;
            } else if (_alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus()
                || _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus()) {
                if (_options != null && _options.getVisibility() == VISIBLE) {
                    _options.requestFocus(View.FOCUS_LEFT);
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
        }

        if (_list.hasFocus()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                _onBackPressed.onBackPressed(_alertDialog);
                lastSelected = false;
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                onItemClick(null, _list, _list.getSelectedItemPosition(), _list.getSelectedItemId());
                lastSelected = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        //
    }

    private void refreshDirs(final boolean scrollToTop) {
        listDirs(scrollToTop);
        //_adapter.setEntries(_entries);
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
    private List<SmbFile> _entries = new ArrayList<>();
    private SmbDirAdapter _adapter;
    private String _serverIP;
    private CIFSContext _smbContext;
    private SmbFile _currentDir;
    private String _rootDirPath;
    private SmbFile _rootDir;
    private AlertDialog _alertDialog;
    private ListView _list;
    private boolean _isScrollable = true;
    private OnChosenListener _onChosenListener = null;
    private OnSelectedListener _onSelectedListener = null;
    private boolean _dirOnly;
    private SmbFileFilter _fileFilter;
    private @Nullable
    @StringRes
    Integer _titleRes = null, _okRes = null, _negativeRes = null;
    private @NonNull
    String _title = "Select a file", _ok = "Choose", _negative = "Cancel";
    private @Nullable
    @DrawableRes
    Integer _iconRes = null;
    //private Drawable _icon = null;
    private @Nullable
    @LayoutRes
    Integer _layoutRes = null;
    private @Nullable
    @LayoutRes
    Integer _rowLayoutRes = null;
    private String _dateFormat;
    private DialogInterface.OnClickListener _negativeListener;
    private DialogInterface.OnCancelListener _onCancelListener;
    private boolean _disableTitle;
    private boolean _displayPath;
    private TextView _pathView;
    private CustomizePathView _pathViewCallback;
    private boolean _cancelable = true;
    private boolean _cancelOnTouchOutside;
    private boolean _dismissOnButtonClick = true;
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
    private View _newFolderView;
    private boolean _enableMultiple;
    private boolean _allowSelectDir = false;
    private boolean _enableDpad;


    @FunctionalInterface
    public interface AdapterSetter {
        void apply(SmbDirAdapter adapter);
    }

    private AdapterSetter _adapterSetter = null;

    @FunctionalInterface
    public interface CanNavigateUp {
        boolean canUpTo(SmbFile dir) throws SmbException;
    }

    @FunctionalInterface
    public interface CanNavigateTo {
        boolean canNavigate(SmbFile dir);
    }

    private CanNavigateUp _folderNavUpCB;
    private CanNavigateTo _folderNavToCB;

    private final static CanNavigateUp _defaultNavUpCB = dir -> dir != null && dir.canRead();

    private final static CanNavigateTo _defaultNavToCB = dir -> true;

    @FunctionalInterface
    public interface OnBackPressedListener {
        void onBackPressed(@NonNull AlertDialog dialog);
    }

    private OnBackPressedListener _onBackPressed = dialog -> {
        if (SmbFileChooserDialog.this._entries.size() > 0
            && (SmbFileChooserDialog.this._entries.get(0).getName().equals("../") || SmbFileChooserDialog.this._entries.get(0).getName().equals(".."))) {
            SmbFileChooserDialog.this.onItemClick(null, SmbFileChooserDialog.this._list, 0, 0);
        } else {
            if (SmbFileChooserDialog.this._onLastBackPressed != null)
                SmbFileChooserDialog.this._onLastBackPressed.onBackPressed(dialog);
            else SmbFileChooserDialog.this._defaultLastBack.onBackPressed(dialog);
        }
    };
    private OnBackPressedListener _onLastBackPressed;

    private OnBackPressedListener _defaultLastBack = Dialog::dismiss;

    private static final int CHOOSE_MODE_NORMAL = 0;
    private static final int CHOOSE_MODE_DELETE = 1;
    private static final int CHOOSE_MODE_SELECT_MULTIPLE = 2;

    private int _chooseMode = CHOOSE_MODE_NORMAL;

    private NewFolderFilter _newFolderFilter;

    private ProgressBar progressBar;

    @FunctionalInterface
    public interface CustomizePathView {
        void customize(TextView pathView);
    }
}
