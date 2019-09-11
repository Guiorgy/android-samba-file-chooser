package com.obsez.android.lib.smbfilechooser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
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
import android.widget.ProgressBar;
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

import com.obsez.android.lib.smbfilechooser.internals.ExtSmbFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.RegexSmbFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.Triple;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;
import com.obsez.android.lib.smbfilechooser.permissions.PermissionsUtil;
import com.obsez.android.lib.smbfilechooser.tool.IExceptionHandler;
import com.obsez.android.lib.smbfilechooser.tool.SmbDirAdapter;

import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.Configuration;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
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

    /**
     * idealy exceptions should be cought and handled here
     *
     * @param handler see {@link IExceptionHandler.ExceptionHandler}
     */
    public SmbFileChooserDialog setExceptionHandler(@NonNull final IExceptionHandler.ExceptionHandler handler) {
        this._handler = handler;
        return this;
    }

    private IExceptionHandler _exceptionHandler = new IExceptionHandler() {
        @Override
        public void handleException(@NonNull final Throwable exception) {
            _terminate = _handler == null || _handler.handle(exception, ExceptionId.UNDEFINED);
            if (_alertDialog != null && _terminate) _alertDialog.cancel();
        }

        @Override
        public void handleException(@NonNull final Throwable exception, final int id) {
            _terminate = _handler == null || _handler.handle(exception, id);
            if (_alertDialog != null && _terminate) _alertDialog.cancel();
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

    /**
     * @param properties see {@link PropertyConfiguration} to find all properties and their default values
     */
    private SmbFileChooserDialog(@NonNull final Context context, @Nullable final Properties properties, @NonNull final String serverIP) {
        this(context, properties, serverIP, null);
    }

    private SmbFileChooserDialog(@NonNull final Context context, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthenticator auth) {
        this(context, null, serverIP, auth);
    }

    /**
     * @param properties see {@link PropertyConfiguration} to find all properties and their default values
     */
    private SmbFileChooserDialog(@NonNull final Context context, @Nullable final Properties properties, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthenticator auth) {
        super(context);

        if (serverIP.equals("smb://")) {
            this._serverIP = "";
        } else if (serverIP.startsWith("smb://")) {
            this._serverIP = serverIP.substring(6);
        } else this._serverIP = serverIP;
        if (!this._serverIP.isEmpty() && this._serverIP.endsWith("/")) {
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
    public SmbFileChooserDialog cancelOnTouchOutside(boolean cancelOnTouchOutside) {
        this._cancelOnTouchOutside = cancelOnTouchOutside;
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
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public SmbFileChooserDialog setOnDismissListener(@NonNull DialogInterface.OnDismissListener listener) {
        this._onDismissListener = listener;
        return this;
    }

    /**
     * called every time {@link KeyEvent#KEYCODE_BACK} is caught,
     * and current directory is not the root of Primary/SdCard storage.
     */
    @NonNull
    public SmbFileChooserDialog setOnBackPressedListener(@NonNull OnBackPressedListener listener) {
        this._onBackPressed = listener;
        return this;
    }

    /**
     * called if {@link KeyEvent#KEYCODE_BACK} is caught,
     * and current directory is the root of Primary/SdCard storage.
     */
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
    public SmbFileChooserDialog setOptionResources(@Nullable @StringRes final Integer createDirRes, @Nullable @StringRes final Integer refreshRes, @Nullable @StringRes final Integer deleteRes, @Nullable @StringRes final Integer newFolderCancelRes, @Nullable @StringRes final Integer newFolderOkRes) {
        this._createDirRes = createDirRes;
        this._refreshRes = refreshRes;
        this._deleteRes = deleteRes;
        this._newFolderCancelRes = newFolderCancelRes;
        this._newFolderOkRes = newFolderOkRes;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setOptionResources(@Nullable final String createDir, @Nullable final String refresh, @Nullable final String delete, @Nullable final String newFolderCancel, @Nullable final String newFolderOk) {
        if (createDir != null) {
            this._createDir = createDir;
        }
        if (refresh != null) {
            this._refresh = refresh;
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
    public SmbFileChooserDialog setOptionIcons(@DrawableRes final int optionsIconRes, @DrawableRes final int createDirIconRes, @DrawableRes final int refreshIconRes, @DrawableRes final int deleteRes) {
        this._optionsIconRes = optionsIconRes;
        this._createDirIconRes = createDirIconRes;
        this._refreshIconRes = refreshIconRes;
        this._deleteIconRes = deleteRes;
        return this;
    }

    public SmbFileChooserDialog setOptionIcons(@Nullable Drawable optionsIcon, @Nullable Drawable createDirIcon, @Nullable Drawable refreshIcon, @Nullable Drawable deleteIcon) {
        this._optionsIcon = optionsIcon;
        this._createDirIcon = createDirIcon;
        this._refreshIcon = refreshIcon;
        this._deleteIcon = deleteIcon;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setIcon(@Nullable @DrawableRes Integer iconId) {
        this._iconRes = iconId;
        return this;
    }

    @NonNull
    public SmbFileChooserDialog setIcon(@Nullable Drawable icon) {
        this._icon = icon;
        return this;
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public SmbFileChooserDialog setLayoutView(@Nullable @LayoutRes Integer layoutResId) {
        this._layoutRes = layoutResId;
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

    @NonNull
    public SmbFileChooserDialog setTheme(@StyleRes final int themeResId) {
        this._themeResId = themeResId;
        return this;
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    public SmbFileChooserDialog build() {
        if (_terminate) {
            return this;
        }

        if (this._themeResId == null) {
            TypedValue typedValue = new TypedValue();
            if (!getBaseContext().getTheme().resolveAttribute(R.attr.fileChooserStyle, typedValue, true))
                themeWrapContext(R.style.FileChooserStyle);
            else themeWrapContext(typedValue.resourceId);
        } else {
            themeWrapContext(this._themeResId);
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
        final Context dialogContext = getThemeWrappedContext(dialogStyle);
        final int style = ta.getResourceId(R.styleable.FileChooser_fileChooserListItemStyle, R.style.FileChooserListItemStyle);
        ta.recycle();
        final Context context = getThemeWrappedContext(style);
        ta = context.obtainStyledAttributes(R.styleable.FileChooser);
        final int listview_item_selector = ta.getResourceId(R.styleable.FileChooser_fileListItemFocusedDrawable,
            R.drawable.listview_item_selector);
        ta.recycle();

        this._adapter = new SmbDirAdapter(context, this._dateFormat, this._exceptionHandler);
        if (this._adapterSetter != null) this._adapterSetter.apply(this._adapter);

        builder.setAdapter(this._adapter, this);

        if (_currentDir == null) {
            this.setStartFile(null);
        }

        this.refreshDirs();

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

        builder.setOnKeyListener(this);

        this._alertDialog = builder.create();

        this._alertDialog.setOnDismissListener(dialog -> EXECUTOR.shutdownNow());

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

                final int buttonColor = _btnNeutral.getCurrentTextColor();
                final PorterDuffColorFilter filter = new PorterDuffColorFilter(buttonColor, PorterDuff.Mode.SRC_IN);

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

                ViewGroup.MarginLayoutParams params;
                ProgressBar progressBar;
                if (root instanceof LinearLayout) {
                    progressBar = new ProgressBar(getBaseContext(), null, android.R.attr.progressBarStyleHorizontal);
                    params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                    ((LinearLayout.LayoutParams) params).gravity = CENTER;
                    root.addView(progressBar, 0, params);
                } else {
                    progressBar = new ProgressBar(getBaseContext(), null, android.R.attr.progressBarStyleLarge);
                    params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, CENTER);
                    root.addView(progressBar, params);
                    progressBar.bringToFront();
                }
                progressBar.setIndeterminate(true);
                progressBar.setBackgroundColor(0x00000000);
                progressBar.getIndeterminateDrawable().setColorFilter(filter);
                SmbFileChooserDialog.this._progressBar = progressBar;

                // region options view
                if (SmbFileChooserDialog.this._enableOptions) {
                    _btnNeutral.setText("");
                    _btnNeutral.setTextColor(buttonColor);
                    _btnNeutral.setVisibility(VISIBLE);
                    Drawable dots;
                    if (SmbFileChooserDialog.this._optionsIconRes != null) {
                        dots = ContextCompat.getDrawable(getBaseContext(), SmbFileChooserDialog.this._optionsIconRes);
                    } else if (SmbFileChooserDialog.this._optionsIcon != null) {
                        dots = SmbFileChooserDialog.this._optionsIcon;
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
                            if (SmbFileChooserDialog.this._options.getHeight() == 0) {
                                ViewTreeObserver viewTreeObserver = SmbFileChooserDialog.this._options.getViewTreeObserver();
                                viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (SmbFileChooserDialog.this._options.getHeight() <= 0) {
                                            return false;
                                        }
                                        viewTreeObserver.removeOnPreDrawListener(this);
                                        scroll.Int = getListYScroll(SmbFileChooserDialog.this._list);
                                        if (SmbFileChooserDialog.this._options.getParent() instanceof FrameLayout) {
                                            final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) SmbFileChooserDialog.this._list.getLayoutParams();
                                            params.bottomMargin = SmbFileChooserDialog.this._options.getHeight();
                                            SmbFileChooserDialog.this._list.setLayoutParams(params);
                                        }
                                        SmbFileChooserDialog.this._options.setVisibility(VISIBLE);
                                        SmbFileChooserDialog.this._options.requestFocus();
                                        return true;
                                    }
                                });
                            } else {
                                scroll.Int = getListYScroll(SmbFileChooserDialog.this._list);
                                if (SmbFileChooserDialog.this._options.getParent() instanceof FrameLayout) {
                                    final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) SmbFileChooserDialog.this._list.getLayoutParams();
                                    params.bottomMargin = SmbFileChooserDialog.this._options.getHeight();
                                    SmbFileChooserDialog.this._list.setLayoutParams(params);
                                }
                                SmbFileChooserDialog.this._options.setVisibility(VISIBLE);
                                SmbFileChooserDialog.this._options.requestFocus();
                            }
                        }
                    };
                    final Runnable hideOptions = () -> {
                        scroll.Int = getListYScroll(SmbFileChooserDialog.this._list);
                        SmbFileChooserDialog.this._options.setVisibility(GONE);
                        SmbFileChooserDialog.this._options.clearFocus();
                        if (SmbFileChooserDialog.this._options.getParent() instanceof FrameLayout) {
                            ViewGroup.MarginLayoutParams params1 = (ViewGroup.MarginLayoutParams) SmbFileChooserDialog.this._list.getLayoutParams();
                            params1.bottomMargin = 0;
                            SmbFileChooserDialog.this._list.setLayoutParams(params1);
                        }
                    };

                    _btnNeutral.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View v) {
                            if (UiUtil.getListYScroll(SmbFileChooserDialog.this._list) == -1
                                || !SmbFileChooserDialog.this._isScrollable
                                || (SmbFileChooserDialog.this._newFolderView != null
                                && SmbFileChooserDialog.this._newFolderView.getVisibility() == VISIBLE))
                                return;

                            if (SmbFileChooserDialog.this._options == null) {
                                // region Draw options view. (this only happens the first time one clicks on options)
                                // Create options view.
                                final LinearLayout options = new LinearLayout(getBaseContext());
                                ViewGroup.MarginLayoutParams params;
                                if (root instanceof LinearLayout) {
                                    params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                    LinearLayout.LayoutParams param = ((LinearLayout.LayoutParams) SmbFileChooserDialog.this._list.getLayoutParams());
                                    param.weight = 1;
                                    SmbFileChooserDialog.this._list.setLayoutParams(param);
                                } else {
                                    params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, BOTTOM);
                                }
                                root.addView(options, params);
                                if (root instanceof FrameLayout) {
                                    SmbFileChooserDialog.this._list.bringToFront();
                                    SmbFileChooserDialog.this._progressBar.bringToFront();
                                }
                                options.setOnClickListener(null);
                                options.setOrientation(LinearLayout.HORIZONTAL);

                                // Create a button for the option to create a new directory/folder.
                                final Button createDir = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                if (SmbFileChooserDialog.this._createDirRes == null)
                                    createDir.setText(SmbFileChooserDialog.this._createDir);
                                else createDir.setText(SmbFileChooserDialog.this._createDirRes);
                                createDir.setTextColor(buttonColor);
                                createDir.setSingleLine();
                                Drawable plus;
                                if (SmbFileChooserDialog.this._createDirIconRes != null) {
                                    plus = ContextCompat.getDrawable(getBaseContext(), SmbFileChooserDialog.this._createDirIconRes);
                                } else if (SmbFileChooserDialog.this._createDirIcon != null) {
                                    plus = SmbFileChooserDialog.this._createDirIcon;
                                } else
                                    plus = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_add_24dp);
                                if (plus != null) {
                                    plus.setColorFilter(filter);
                                    createDir.setCompoundDrawablesWithIntrinsicBounds(plus, null, null, null);
                                }

                                params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
                                params.leftMargin = UiUtil.dip2px(6);
                                options.addView(createDir, params);

                                // Create a button for the refreshing data.
                                final Button refresh = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                if (SmbFileChooserDialog.this._refreshRes == null)
                                    refresh.setText(SmbFileChooserDialog.this._refresh);
                                else refresh.setText(SmbFileChooserDialog.this._refreshRes);
                                refresh.setTextColor(buttonColor);
                                refresh.setSingleLine();
                                Drawable round;
                                if (SmbFileChooserDialog.this._refreshIconRes != null) {
                                    round = ContextCompat.getDrawable(getBaseContext(), SmbFileChooserDialog.this._refreshIconRes);
                                } else if (SmbFileChooserDialog.this._refreshIcon != null) {
                                    round = SmbFileChooserDialog.this._refreshIcon;
                                } else
                                    round = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_refresh_24dp);
                                if (round != null) {
                                    round.setColorFilter(filter);
                                    refresh.setCompoundDrawablesWithIntrinsicBounds(round, null, null, null);
                                }

                                params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
                                params.leftMargin = UiUtil.dip2px(6);
                                params.rightMargin = UiUtil.dip2px(6);
                                options.addView(refresh, params);

                                // Create a button for the option to delete a file.
                                final Button delete = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                if (SmbFileChooserDialog.this._deleteRes == null)
                                    delete.setText(SmbFileChooserDialog.this._delete);
                                else delete.setText(SmbFileChooserDialog.this._deleteRes);
                                delete.setTextColor(buttonColor);
                                delete.setSingleLine();
                                Drawable bin;
                                if (SmbFileChooserDialog.this._deleteIconRes != null) {
                                    bin = ContextCompat.getDrawable(getBaseContext(), SmbFileChooserDialog.this._deleteIconRes);
                                } else if (SmbFileChooserDialog.this._deleteIcon != null) {
                                    bin = SmbFileChooserDialog.this._deleteIcon;
                                } else
                                    bin = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_delete_24dp);
                                if (bin != null) {
                                    bin.setColorFilter(filter);
                                    delete.setCompoundDrawablesWithIntrinsicBounds(bin, null, null, null);
                                }

                                params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
                                params.rightMargin = UiUtil.dip2px(6);
                                options.addView(delete, params);

                                SmbFileChooserDialog.this._options = options;
                                showOptions.run();

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
                                                runOnUiThread(() -> _exceptionHandler.handleException(e));
                                                return "";
                                            }
                                        });

                                        if (SmbFileChooserDialog.this._newFolderView == null) {
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
                                                _exceptionHandler.handleException(e);
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
                                            SmbFileChooserDialog.this._newFolderView = overlay;

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

                                            // The Space on the right.
                                            Space rightSpace = new Space(getBaseContext());
                                            params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, (1f - widthWeight) / 2);
                                            linearLayout.addView(rightSpace, params);

                                            // An EditText to input the new folder name.
                                            final EditText input = new EditText(getBaseContext());
                                            final int color = ta.getColor(R.styleable.FileChooser_fileChooserNewFolderTextColor, buttonColor);
                                            input.setTextColor(color);
                                            input.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                                            input.setSelectAllOnFocus(true);
                                            input.setSingleLine(true);
                                            // There should be no suggestions, but... :)
                                            input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                                            input.setFilters(new InputFilter[]{SmbFileChooserDialog.this._newFolderFilter != null ? SmbFileChooserDialog.this._newFolderFilter : new NewFolderFilter()});
                                            input.setGravity(CENTER_HORIZONTAL);
                                            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                                            params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                            params.setMargins(3, 2, 3, 0);
                                            holder.addView(input, params);

                                            this.input = input;

                                            // A horizontal LinearLayout to hold buttons
                                            final FrameLayout buttons = new FrameLayout(getBaseContext());
                                            params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                            holder.addView(buttons, params);

                                            // The Cancel button.
                                            final Button cancel = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                            if (SmbFileChooserDialog.this._newFolderCancelRes == null)
                                                cancel.setText(SmbFileChooserDialog.this._newFolderCancel);
                                            else
                                                cancel.setText(SmbFileChooserDialog.this._newFolderCancelRes);
                                            cancel.setTextColor(buttonColor);
                                            params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, START);
                                            buttons.addView(cancel, params);

                                            // The OK button.
                                            final Button ok = new Button(dialogContext, null, android.R.attr.buttonBarButtonStyle);
                                            if (SmbFileChooserDialog.this._newFolderOkRes == null)
                                                ok.setText(SmbFileChooserDialog.this._newFolderOk);
                                            else
                                                ok.setText(SmbFileChooserDialog.this._newFolderOkRes);
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
                                                    SmbFileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                    overlay.setVisibility(GONE);
                                                    overlay.clearFocus();
                                                    if (SmbFileChooserDialog.this._enableDpad) {
                                                        SmbFileChooserDialog.this._btnNeutral.setFocusable(true);
                                                        SmbFileChooserDialog.this._btnNeutral.requestFocus();
                                                        SmbFileChooserDialog.this._list.setFocusable(true);
                                                    }
                                                    return true;
                                                }
                                                return false;
                                            });
                                            cancel.setOnClickListener(v22 -> {
                                                UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                overlay.setVisibility(GONE);
                                                overlay.clearFocus();
                                                if (SmbFileChooserDialog.this._enableDpad) {
                                                    SmbFileChooserDialog.this._btnNeutral.setFocusable(true);
                                                    SmbFileChooserDialog.this._list.setFocusable(true);
                                                }
                                            });
                                            ok.setOnClickListener(v2 -> {
                                                SmbFileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                overlay.setVisibility(GONE);
                                                overlay.clearFocus();
                                                if (SmbFileChooserDialog.this._enableDpad) {
                                                    SmbFileChooserDialog.this._btnNeutral.setFocusable(true);
                                                    SmbFileChooserDialog.this._list.setFocusable(true);
                                                }
                                            });
                                            ta.recycle();
                                            // endregion
                                        }

                                        if (SmbFileChooserDialog.this._newFolderView.getVisibility() != VISIBLE) {
                                            SmbFileChooserDialog.this._newFolderView.setVisibility(VISIBLE);
                                            if (SmbFileChooserDialog.this._enableDpad) {
                                                SmbFileChooserDialog.this._newFolderView.requestFocus();
                                                SmbFileChooserDialog.this._btnNeutral.setFocusable(false);
                                                SmbFileChooserDialog.this._list.setFocusable(false);
                                            }
                                            if (this.input != null) {
                                                try {
                                                    if (!futureNewFile.isCancelled())
                                                        this.input.setText(futureNewFile.get());
                                                } catch (InterruptedException | ExecutionException e) {
                                                    e.printStackTrace();
                                                    SmbFileChooserDialog.this._exceptionHandler.handleException(e);
                                                    this.input.setText("");
                                                }
                                            }
                                            if (SmbFileChooserDialog.this._pathView != null &&
                                                SmbFileChooserDialog.this._pathView.getVisibility() == View.VISIBLE) {
                                                SmbFileChooserDialog.this._newFolderView.setPadding(0, UiUtil.dip2px(32),
                                                    0, UiUtil.dip2px(12));
                                            } else {
                                                SmbFileChooserDialog.this._newFolderView.setPadding(0, UiUtil.dip2px(12),
                                                    0, UiUtil.dip2px(12));
                                            }
                                        } else {
                                            SmbFileChooserDialog.this._newFolderView.setVisibility(GONE);
                                            if (SmbFileChooserDialog.this._enableDpad) {
                                                SmbFileChooserDialog.this._newFolderView.clearFocus();
                                                SmbFileChooserDialog.this._btnNeutral.setFocusable(true);
                                                SmbFileChooserDialog.this._list.setFocusable(true);
                                            }
                                        }
                                    }
                                });
                                refresh.setOnClickListener(v1 -> {
                                    hideOptions.run();
                                    refreshDirs(0);
                                });
                                delete.setOnClickListener(v1 -> {
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

                                            SmbFileChooserDialog.this._currentDir = EXECUTOR.submit(() -> {
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
                                        } catch (InterruptedException | ExecutionException e) {
                                            e.printStackTrace();
                                            _exceptionHandler.handleException(e);
                                        } finally {
                                            SmbFileChooserDialog.this._chooseMode = CHOOSE_MODE_NORMAL;
                                            SmbFileChooserDialog.this._btnPositive.setVisibility(INVISIBLE);
                                            SmbFileChooserDialog.this._adapter.clearSelected();
                                            refreshDirs();
                                        }
                                        return;
                                    }

                                    SmbFileChooserDialog.this._chooseMode = SmbFileChooserDialog.this._chooseMode != CHOOSE_MODE_DELETE ? CHOOSE_MODE_DELETE : CHOOSE_MODE_NORMAL;
                                    if (SmbFileChooserDialog.this._deleteMode == null) {
                                        SmbFileChooserDialog.this._deleteMode = () -> {
                                            if (SmbFileChooserDialog.this._chooseMode == CHOOSE_MODE_DELETE) {
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
                                    SmbFileChooserDialog.this._deleteMode.run();
                                });
                                // endregion
                            } else if (SmbFileChooserDialog.this._options.getVisibility() == VISIBLE) {
                                hideOptions.run();
                            } else {
                                showOptions.run();
                            }
                        }
                    });
                }
                // endregion
            }
        });

        this._list = this._alertDialog.getListView();
        this._list.setOnItemClickListener(this);
        if (this._enableMultiple) {
            this._list.setOnItemLongClickListener(this);
        }

        //this._list.setOnTouchListener((v, event) -> !_isScrollable && event.getAction() == MotionEvent.ACTION_MOVE);

        if (this._enableDpad) {
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

    public SmbFileChooserDialog show() {
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

            displayRoot = ta.getBoolean(R.styleable.FileChooser_fileChooserPathViewDisplayRoot, false);

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
            if (path.contains("smb://")) path = path.substring(5);
            if (!displayRoot && path.contains(_serverIP))
                path = path.substring(_serverIP.length() + 1);
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

    public final class RootSmbFile extends SmbFile {
        private String name;

        RootSmbFile(String path, String name) throws MalformedURLException {
            //noinspection deprecation
            super(path);
            this.name = name;
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

        @Override
        public String getName() {
            return this.name;
        }
    }

    private void listDirs(final int scrollTo) {
        if (_progressBar != null) _progressBar.setVisibility(VISIBLE);
        _isScrollable = false;
        EXECUTOR.execute(() -> {
            AtomicBoolean displayPath = new AtomicBoolean(false);
            try {
                _entries.clear();
                // Add the ".." entry
                final String parent = _currentDir.getParent();
                if (parent != null && !parent.equalsIgnoreCase("smb://")) {
                    _entries.add(new RootSmbFile(parent, ".."));
                    displayPath.set(true);
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
                _isScrollable = true;
                runOnUiThread(() -> {
                    _adapter.setEntries(_entries);
                    if (_progressBar != null) _progressBar.setVisibility(GONE);
                    if (_alertDialog != null && _alertDialog.isShowing() && _displayPath) {
                        if (displayPath.get()) {
                            displayPath(_currentDir.getPath());
                        } else {
                            displayPath(null);
                        }
                    }
                    if (scrollTo != -1) {
                        _list.setSelection(scrollTo);
                        _list.post(() -> _list.setSelection(scrollTo));
                    }
                });
            }
        });
    }

    void sortByName(@NonNull List<SmbFile> list) {
        Collections.sort(list, (f1, f2) -> f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase()));
    }

    private void createNewDirectory(@NonNull final String name) {
        EXECUTOR.execute(() -> {
            try {
                final SmbFile newDir = new SmbFile(SmbFileChooserDialog.this._currentDir.getPath() + "/" + name, SmbFileChooserDialog.this._smbContext);
                if (!newDir.exists()) {
                    newDir.mkdirs();
                    runOnUiThread(this::refreshDirs);
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
        _progressBar.setVisibility(VISIBLE);
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
            runOnUiThread(() -> _progressBar.setVisibility(GONE));
        });
    }

    @Override
    public void onItemClick(@Nullable final AdapterView<?> parent, @NonNull final View list, final int position, final long id) {
        try {
            if (position < 0 || position >= _entries.size()) return;

            Triple<SmbFile, Boolean, String> triple = EXECUTOR.submit(() -> {
                SmbFile file = _entries.get(position);
                if (file instanceof RootSmbFile) {
                    if (_folderNavUpCB == null) _folderNavUpCB = _defaultNavUpCB;
                    if (_folderNavUpCB.canUpTo(file)) {
                        _currentDir = file;
                        _chooseMode = _chooseMode == CHOOSE_MODE_DELETE ? CHOOSE_MODE_NORMAL : _chooseMode;
                        if (_deleteMode != null) _deleteMode.run();
                        lastSelected = _adapter.isEmpty();
                        return new Triple<SmbFile, Boolean, String>(file, true);
                    }
                }
                return new Triple<>(file, file.isDirectory(), file.getPath());
            }).get();

            final SmbFile file = triple.first;
            final boolean isDirectory = triple.second;
            final String path = triple.third;
            int scrollTo = 0;

            if (file != null) {
                if (file instanceof RootSmbFile) {
                    if (!_adapter.getIndexStack().empty()) {
                        scrollTo = _adapter.getIndexStack().pop();
                    }
                } else switch (_chooseMode) {
                    case CHOOSE_MODE_NORMAL:
                        if (isDirectory) {
                            if (_folderNavToCB == null) _folderNavToCB = _defaultNavToCB;
                            if (_folderNavToCB.canNavigate(file)) {
                                _currentDir = file;
                                scrollTo = 0;
                                _adapter.getIndexStack().push(position);
                            }
                        } else if ((!_dirOnly) && _onChosenListener != null) {
                            _onChosenListener.onChoosePath(path, file);
                            _alertDialog.dismiss();
                            return;
                        }
                        lastSelected = false;
                        break;
                    case CHOOSE_MODE_SELECT_MULTIPLE:
                        if (isDirectory) {
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
                        deleteFile(file);
                        _chooseMode = CHOOSE_MODE_NORMAL;
                        if (_deleteMode != null) _deleteMode.run();
                        scrollTo = -1;
                        break;
                    default:
                        // ERROR! It shouldn't get here...
                        break;
                }
            }

            refreshDirs(scrollTo);
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
                return !(file instanceof RootSmbFile) && (_allowSelectDir || !file.isDirectory());
            }).get()) {
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
        if (_progressBar != null && _progressBar.getVisibility() == VISIBLE) return true;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (_newFolderView != null && _newFolderView.getVisibility() == VISIBLE) {
                _newFolderView.setVisibility(GONE);
                return true;
            }

            _onBackPressedListener.onBackPressed((AlertDialog) dialog);
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
                            _list.setSelection(_entries.size() - 1);
                            return true;
                        }
                    } else if (_options != null && _options.hasFocus()) {
                        _list.requestFocus();
                        _list.setSelection(_entries.size() - 1);
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
    public void onClick(DialogInterface dialog, int which) {
        //
    }

    private void refreshDirs() {
        listDirs(-1);
    }

    private void refreshDirs(final int scrollTo) {
        listDirs(scrollTo);
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
    Integer _createDirRes = null, _refreshRes = null, _deleteRes = null, _newFolderCancelRes = null, _newFolderOkRes = null;
    private @NonNull
    String _createDir = "New folder", _refresh = "Refresh", _delete = "Delete", _newFolderCancel = "Cancel", _newFolderOk = "Ok";
    private @Nullable
    @DrawableRes
    Integer _optionsIconRes = null, _createDirIconRes = null, _refreshIconRes = null, _deleteIconRes = null;
    private @Nullable
    Drawable _optionsIcon = null, _createDirIcon = null, _refreshIcon = null, _deleteIcon = null;
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
        void onBackPressed(@NonNull AlertDialog dialog);
    }

    private OnBackPressedListener _onBackPressedListener = dialog -> {
        if (SmbFileChooserDialog.this._entries.size() > 0
            && (SmbFileChooserDialog.this._entries.get(0) instanceof RootSmbFile)) {
            if (SmbFileChooserDialog.this._onBackPressed != null)
                SmbFileChooserDialog.this._onBackPressed.onBackPressed(dialog);
            else SmbFileChooserDialog.this._defaultBack.onBackPressed(dialog);
        } else {
            if (SmbFileChooserDialog.this._onLastBackPressed != null)
                SmbFileChooserDialog.this._onLastBackPressed.onBackPressed(dialog);
            else SmbFileChooserDialog._defaultLastBack.onBackPressed(dialog);
        }
    };

    private OnBackPressedListener _onBackPressed;
    private OnBackPressedListener _onLastBackPressed;
    private final OnBackPressedListener _defaultBack = dialog -> SmbFileChooserDialog.this._list.performItemClick(SmbFileChooserDialog.this._list, 0, 0);
    private static final OnBackPressedListener _defaultLastBack = Dialog::cancel;

    private static final int CHOOSE_MODE_NORMAL = 0;
    private static final int CHOOSE_MODE_DELETE = 1;
    private static final int CHOOSE_MODE_SELECT_MULTIPLE = 2;

    private int _chooseMode = CHOOSE_MODE_NORMAL;

    private NewFolderFilter _newFolderFilter;

    private ProgressBar _progressBar;

    @FunctionalInterface
    public interface CustomizePathView {
        void customize(TextView pathView);
    }
}
