package com.obsez.android.lib.smbfilechooser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
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
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.obsez.android.lib.smbfilechooser.internals.ExtSmbFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.RegexSmbFileFilter;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;
import com.obsez.android.lib.smbfilechooser.tool.SmbDirAdapter;

import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;

import jcifs.Config;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;
import kotlin.Triple;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
import static com.obsez.android.lib.smbfilechooser.internals.FileUtil.LightContextWrapper;
import static com.obsez.android.lib.smbfilechooser.internals.FileUtil.NewFolderFilter;

/**
 * Created by coco on 6/7/15. Edited by Guiorgy on 10/09/18.
 */
@SuppressWarnings("SpellCheckingInspection")
public class SmbFileChooserDialog extends LightContextWrapper implements DialogInterface.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, AdapterView.OnItemSelectedListener, DialogInterface.OnKeyListener {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory(){
        @Override
        public Thread newThread(@NonNull final Runnable runnable){
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("SmbFileChooserDialog - Thread");
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    });

    public static ExecutorService getNetworkThread(){
        return  EXECUTOR;
    }

    @FunctionalInterface
    public interface OnChosenListener{
        void onChoosePath(@NonNull String path, @NonNull SmbFile file);
    }

    @FunctionalInterface
    public interface OnSelectedListener {
        void onSelectFiles(@NonNull final List<SmbFile> files);
    }

    public SmbFileChooserDialog(@NonNull final Context context){
        super(context);
    }

    private SmbFileChooserDialog(@NonNull final Context context, @NonNull final String serverIP) throws MalformedURLException{
        this(context, serverIP, null);
    }

    public SmbFileChooserDialog(@NonNull final Context context, @Nullable final NtlmPasswordAuthentication auth){
        super(context);
        this._auth = auth;
    }

    private SmbFileChooserDialog(@NonNull final Context context, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthentication auth) throws MalformedURLException{
        super(context);
        Config.setProperty("jcifs.netbios.wins", serverIP);
        this._rootDirPath = "smb://" + serverIP + '/';
        this._rootDir = new SmbFile(this._rootDirPath, auth);
        this._auth = auth;
    }

    @NonNull public static SmbFileChooserDialog newDialog(@NonNull final Context context){
        return new SmbFileChooserDialog(context);
    }

    @NonNull public static SmbFileChooserDialog newDialog(@NonNull final Context context, @NonNull final String serverIP) throws MalformedURLException{
        return new SmbFileChooserDialog(context, serverIP);
    }

    @NonNull public static SmbFileChooserDialog newDialog(@NonNull final Context context, @Nullable final NtlmPasswordAuthentication auth){
        return new SmbFileChooserDialog(context, auth);
    }

    @NonNull public static SmbFileChooserDialog newDialog(@NonNull final Context context, @NonNull final String serverIP, @Nullable final NtlmPasswordAuthentication auth) throws MalformedURLException{
        return new SmbFileChooserDialog(context, serverIP, auth);
    }

    @NonNull public SmbFileChooserDialog setAuthenticator(@NonNull final NtlmPasswordAuthentication auth) throws MalformedURLException{
        this._auth = auth;
        if(this._rootDirPath != null){
            this._rootDir = new SmbFile(this._rootDirPath, auth);
        }
        return this;
    }

    @NonNull public SmbFileChooserDialog setServer(@NonNull final String serverIP) throws MalformedURLException{
        Config.setProperty("jcifs.netbios.wins", serverIP);
        this._rootDirPath = "smb://" + serverIP + '/';
        this._rootDir = new SmbFile(this._rootDirPath, _auth);
        return this;
    }

    @NonNull public SmbFileChooserDialog setFilter(@NonNull final SmbFileFilter sff) {
        setFilter(false, false, (String[]) null);
        this._fileFilter = sff;
        return this;
    }

    @NonNull public SmbFileChooserDialog setFilter(final boolean dirOnly, final boolean allowHidden, @NonNull final SmbFileFilter sff) {
        setFilter(dirOnly, allowHidden, (String[]) null);
        this._fileFilter = sff;
        return this;
    }

    @NonNull public SmbFileChooserDialog setFilter(boolean allowHidden, @Nullable String... suffixes) {
        return setFilter(false, allowHidden, suffixes);
    }

    @NonNull public SmbFileChooserDialog setFilter(final boolean dirOnly, final boolean allowHidden, @Nullable final String... suffixes) {
        this._dirOnly = dirOnly;
        if (suffixes == null || suffixes.length == 0) {
            this._fileFilter = dirOnly ?
            new SmbFileFilter(){
                @Override
                public boolean accept(final SmbFile file){
                    try{
                        return file.isDirectory() && (!file.isHidden() || allowHidden);
                    } catch(SmbException e){
                        return false;
                    }
                }
            } : new SmbFileFilter(){
                @Override
                public boolean accept(final SmbFile file){
                    try{
                        return !file.isHidden() || allowHidden;
                    } catch(SmbException e){
                        return false;
                    }
                }
            };
        } else {
            this._fileFilter = new ExtSmbFileFilter(_dirOnly, allowHidden, suffixes);
        }
        return this;
    }

    @NonNull public SmbFileChooserDialog setFilterRegex(boolean dirOnly, boolean allowHidden, @NonNull String pattern, int flags) {
        this._dirOnly = dirOnly;
        this._fileFilter = new RegexSmbFileFilter(_dirOnly, allowHidden, pattern, flags);
        return this;
    }

    @NonNull public SmbFileChooserDialog setFilterRegex(boolean dirOnly, boolean allowHidden, @NonNull String pattern) {
        this._dirOnly = dirOnly;
        this._fileFilter = new RegexSmbFileFilter(_dirOnly, allowHidden, pattern, Pattern.CASE_INSENSITIVE);
        return this;
    }

    @NonNull public SmbFileChooserDialog setStartFile(@Nullable final String startFile) throws ExecutionException, InterruptedException{
        final Future<SmbFileChooserDialog> ret = EXECUTOR.submit(new Callable<SmbFileChooserDialog>(){
            @Override
            public SmbFileChooserDialog call() throws Exception{
                if(startFile != null){
                    _currentDir = new SmbFile(startFile, _auth);
                } else{
                    _currentDir = _rootDir;
                }

                if(!_currentDir.isDirectory()){
                    String parent = _currentDir.getParent();
                    if(parent == null){
                        throw new MalformedURLException(startFile + " has no parent directory");
                    }
                    _currentDir = new SmbFile(parent, _auth);
                }

                if(_currentDir == null){
                    _currentDir = _rootDir;
                }
                return SmbFileChooserDialog.this;
            }
        });
        return ret.get();
    }

    @NonNull public SmbFileChooserDialog setCancelable(boolean cancelable){
        this._cancelable = cancelable;
        return this;
    }

    @NonNull public SmbFileChooserDialog cancelOnTouchOutside(boolean cancelOnTouchOutside){
        this._cancelOnTouchOutside = cancelOnTouchOutside;
        return this;
    }

    @NonNull public SmbFileChooserDialog dismissOnButtonClick(boolean dismissOnButtonClick){
        this._dismissOnButtonClick = dismissOnButtonClick;
        if(dismissOnButtonClick){
            this._defaultLastBack = new OnBackPressedListener(){
                @Override
                public void onBackPressed(@NonNull final AlertDialog dialog){
                    dialog.dismiss();
                }
            };
        } else{
            this._defaultLastBack = new OnBackPressedListener(){
                @Override
                public void onBackPressed(@NonNull final AlertDialog dialog){
                    //
                }
            };
        }
        return this;
    }

    @NonNull public SmbFileChooserDialog setOnChosenListener(@NonNull OnChosenListener listener) {
        this._onChosenListener = listener;
        return this;
    }

    @NonNull public SmbFileChooserDialog setOnSelectedListener(@NonNull final OnSelectedListener listener) {
        this._onSelectedListener = listener;
        return this;
    }

    @NonNull public SmbFileChooserDialog setOnDismissListener(@NonNull DialogInterface.OnDismissListener listener){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            this._onDismissListener = listener;
        }
        return this;
    }

    @NonNull public SmbFileChooserDialog setOnBackPressedListener(@NonNull OnBackPressedListener listener){
        this._onBackPressed = listener;
        return this;
    }

    @NonNull public SmbFileChooserDialog setOnLastBackPressedListener(@NonNull OnBackPressedListener listener){
        this._onLastBackPressed = listener;
        return this;
    }

    @NonNull public SmbFileChooserDialog setResources(@StringRes int titleRes, @StringRes int okRes, @StringRes int cancelRes) {
        this._titleRes = titleRes;
        this._okRes = okRes;
        this._negativeRes = cancelRes;
        return this;
    }

    @NonNull public SmbFileChooserDialog setResources(@Nullable String title, @Nullable String ok, @Nullable String cancel) {
        if(title != null){
            this._title = title;
            this._titleRes = 0;
        }
        if(ok != null){
            this._ok = ok;
            this._okRes = 0;
        }
        if(cancel != null){
            this._negative = cancel;
            this._negativeRes = 0;
        }
        return this;
    }

    @NonNull public SmbFileChooserDialog enableOptions(final boolean enableOptions){
        this._enableOptions = enableOptions;
        return this;
    }

    @NonNull public SmbFileChooserDialog setOptionResources(@StringRes final int createDirRes, @StringRes final int deleteRes, @StringRes final int newFolderCancelRes, @StringRes final int newFolderOkRes) {
        this._createDirRes = createDirRes;
        this._deleteRes = deleteRes;
        this._newFolderCancelRes = newFolderCancelRes;
        this._newFolderOkRes = newFolderOkRes;
        return this;
    }

    @NonNull public SmbFileChooserDialog setOptionResources(@Nullable final String createDir, @Nullable final String delete, @Nullable final String newFolderCancel, @Nullable final String newFolderOk) {
        if(createDir != null){
            this._createDir = createDir;
            this._createDirRes = -1;
        }
        if(delete != null){
            this._delete = delete;
            this._deleteRes = -1;
        }
        if(newFolderCancel != null){
            this._newFolderCancel = newFolderCancel;
            this._newFolderCancelRes = -1;
        }
        if(newFolderOk != null){
            this._newFolderOk = newFolderOk;
            this._newFolderOkRes = -1;
        }
        return this;
    }

    @NonNull public SmbFileChooserDialog setOptionIcons(@DrawableRes final int optionsIconRes, @DrawableRes final int createDirIconRes, @DrawableRes final int deleteRes) {
        this._optionsIconRes = optionsIconRes;
        this._createDirIconRes = createDirIconRes;
        this._deleteIconRes = deleteRes;
        return this;
    }

    @NonNull public SmbFileChooserDialog setIcon(@DrawableRes int iconId) {
        this._iconRes = iconId;
        return this;
    }

    @NonNull public SmbFileChooserDialog setLayoutView(@LayoutRes int layoutResId) {
        this._layoutRes = layoutResId;
        return this;
    }

    @NonNull public SmbFileChooserDialog setRowLayoutView(@LayoutRes int layoutResId) {
        this._rowLayoutRes = layoutResId;
        return this;
    }

    @NonNull public SmbFileChooserDialog setDefaultDateFormat() {
        return this.setDateFormat("yyyy/MM/dd HH:mm:ss");
    }

    @NonNull public SmbFileChooserDialog setDateFormat(@NonNull String format) {
        this._dateFormat = format;
        return this;
    }

    @NonNull public SmbFileChooserDialog setNegativeButtonListener(final DialogInterface.OnClickListener listener) {
        this._negativeListener = listener;
        return this;
    }

    /**
     * it's NOT recommended to use the `setOnCancelListener`, replace with `setNegativeButtonListener` pls.
     *
     * @deprecated will be removed at v1.2
     */
    @NonNull public SmbFileChooserDialog setOnCancelListener(@NonNull final DialogInterface.OnCancelListener listener) {
        this._onCancelListener = listener;
        return this;
    }

    /**
     * @deprecated need to find a way to actually get the icons.
     */
    @Deprecated
    @NonNull public SmbFileChooserDialog setFileIcons(final boolean tryResolveFileTypeAndIcon, @Nullable final Drawable fileIcon, @Nullable final Drawable folderIcon) {
        _adapterSetter = new AdapterSetter(){
            @Override
            public void apply(final SmbDirAdapter adapter){
                if(fileIcon != null)
                    adapter.setDefaultFileIcon(fileIcon);
                if(folderIcon != null)
                    adapter.setDefaultFolderIcon(folderIcon);
                //noinspection deprecation
                adapter.setResolveFileType(tryResolveFileTypeAndIcon);
            }
        };
        return this;
    }

    @NonNull public SmbFileChooserDialog setFileIcons(@Nullable final Drawable fileIcon, @Nullable final Drawable folderIcon) {
        _adapterSetter = new AdapterSetter(){
            @Override
            public void apply(final SmbDirAdapter adapter){
                if(fileIcon != null)
                    adapter.setDefaultFileIcon(fileIcon);
                if(folderIcon != null)
                    adapter.setDefaultFolderIcon(folderIcon);
            }
        };
        return this;
    }

    /**
     * @deprecated need to find a way to actually get the icons.
     */
    @Deprecated
    @NonNull public SmbFileChooserDialog setFileIcons(final boolean tryResolveFileTypeAndIcon, final int fileIconResId, final int folderIconResId) {
        _adapterSetter = new AdapterSetter(){
            @Override
            public void apply(final SmbDirAdapter adapter){
                if(fileIconResId != -1)
                    adapter.setDefaultFileIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), fileIconResId));
                if(folderIconResId != -1)
                    adapter.setDefaultFolderIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), folderIconResId));
                //noinspection deprecation
                adapter.setResolveFileType(tryResolveFileTypeAndIcon);
            }
        };
        return this;
    }

    @NonNull public SmbFileChooserDialog setFileIcons(final int fileIconResId, final int folderIconResId) {
        _adapterSetter = new AdapterSetter(){
            @Override
            public void apply(final SmbDirAdapter adapter){
                if(fileIconResId != -1)
                    adapter.setDefaultFileIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), fileIconResId));
                if(folderIconResId != -1)
                    adapter.setDefaultFolderIcon(ContextCompat.getDrawable(SmbFileChooserDialog.this.getBaseContext(), folderIconResId));
            }
        };
        return this;
    }

    /**
     * @param setter you can customize the folder navi-adapter with `setter`
     * @return this
     */
    @NonNull public SmbFileChooserDialog setAdapterSetter(@NonNull AdapterSetter setter) {
        _adapterSetter = setter;
        return this;
    }

    /**
     * @param cb give a hook at navigating up to a directory
     * @return this
     */
    @NonNull public SmbFileChooserDialog setNavigateUpTo(@NonNull CanNavigateUp cb) {
        _folderNavUpCB = cb;
        return this;
    }

    /**
     * @param cb give a hook at navigating to a child directory
     * @return this
     */
    @NonNull public SmbFileChooserDialog setNavigateTo(@NonNull CanNavigateTo cb) {
        _folderNavToCB = cb;
        return this;
    }

    @NonNull public SmbFileChooserDialog disableTitle(boolean b) {
        _disableTitle = b;
        return this;
    }

    @NonNull public SmbFileChooserDialog enableMultiple(final boolean enableMultiple, final boolean allowSelectMultipleFolders){
        this._enableMultiple = enableMultiple;
        this._allowSelectDir = allowSelectMultipleFolders;
        return this;
    }

    @NonNull public SmbFileChooserDialog setNewFolderFilter(@NonNull final NewFolderFilter filter){
        this._newFolderFilter = filter;
        return this;
    }

    @NonNull public SmbFileChooserDialog enableDpad(final boolean enableDpad){
        this._enableDpad = enableDpad;
        return this;
    }

    @NonNull public SmbFileChooserDialog build(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(getBaseContext());

        this._adapter = new SmbDirAdapter(getBaseContext(), new ArrayList<SmbFile>(),
                this._rowLayoutRes != -1 ? this._rowLayoutRes : R.layout.li_row_textview, this._dateFormat);
        if(this._adapterSetter != null){
            this._adapterSetter.apply(this._adapter);
        }
        builder.setAdapter(this._adapter, this);

        this.refreshDirs();

        if(!this._disableTitle){
            if(this._titleRes == 0) builder.setTitle(this._title);
            else builder.setTitle(this._titleRes);
        }

        if(this._iconRes != -1){
            builder.setIcon(this._iconRes);
        }

        if(this._layoutRes != -1){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                builder.setView(this._layoutRes);
            }
        }

        if(this._dirOnly){
            final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener(){
                @Override
                public void onClick(final DialogInterface dialog, final int which){
                    if(SmbFileChooserDialog.this._onChosenListener != null){
                        if(SmbFileChooserDialog.this._dirOnly){
                            SmbFileChooserDialog.this._onChosenListener.onChoosePath(SmbFileChooserDialog.this._currentDir.getPath(), SmbFileChooserDialog.this._currentDir);
                        }
                    }
                }
            };

            if(this._okRes == 0) builder.setPositiveButton(this._ok, listener);
              else builder.setPositiveButton(this._okRes, listener);
        }

        if (this._dirOnly || this._enableMultiple) {
            final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener(){
                @Override
                public void onClick(final DialogInterface dialog, final int which){
                    if(SmbFileChooserDialog.this._enableMultiple){
                        if(SmbFileChooserDialog.this._adapter.isAnySelected()){
                            if(SmbFileChooserDialog.this._adapter.isOneSelected()){
                                if(SmbFileChooserDialog.this._onChosenListener != null){
                                    final SmbFile selected = _adapter.getSelected().get(0);
                                    SmbFileChooserDialog.this._onChosenListener.onChoosePath(selected.getPath(), selected);
                                }
                            } else{
                                if(SmbFileChooserDialog.this._onSelectedListener != null){
                                    SmbFileChooserDialog.this._onSelectedListener.onSelectFiles(_adapter.getSelected());
                                }
                            }
                        }
                    } else if(SmbFileChooserDialog.this._onChosenListener != null){
                        SmbFileChooserDialog.this._onChosenListener.onChoosePath(SmbFileChooserDialog.this._currentDir.getPath(), SmbFileChooserDialog.this._currentDir);
                    }

                    SmbFileChooserDialog.this._alertDialog.dismiss();
                }
            };

            if(this._okRes == -1) builder.setPositiveButton(this._ok, listener);
              else builder.setPositiveButton(this._okRes, listener);
        }

        final DialogInterface.OnClickListener listener = this._negativeListener != null ? this._negativeListener : new DialogInterface.OnClickListener(){
            @Override
            public void onClick(final DialogInterface dialog, final int which){
                dialog.cancel();
            }
        };

        if(this._negativeRes == 0) builder.setNegativeButton(this._negative, listener);
          else builder.setNegativeButton(this._negativeRes, listener);

        if(this._onCancelListener != null){
            builder.setOnCancelListener(this._onCancelListener);
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            if(this._onDismissListener != null){
                builder.setOnDismissListener(this._onDismissListener);
            }
        }

        builder.setCancelable(this._cancelable)
                .setOnKeyListener(new DialogInterface.OnKeyListener(){
                    @Override
                    public boolean onKey(final DialogInterface dialog, final int keyCode, final KeyEvent event){
                        if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP){
                            SmbFileChooserDialog.this._onBackPressed.onBackPressed((AlertDialog) dialog);
                        }
                        return true;
                    }
                });

        this._alertDialog = builder.create();

        this._alertDialog.setCanceledOnTouchOutside(this._cancelOnTouchOutside);
        this._alertDialog.setOnShowListener(new DialogInterface.OnShowListener(){
            @Override
            public void onShow(final DialogInterface dialog){
                if(!SmbFileChooserDialog.this._dismissOnButtonClick){
                    Button negative = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                    negative.setOnClickListener(new View.OnClickListener(){
                        @Override
                        public void onClick(final View v){
                            if(SmbFileChooserDialog.this._negativeListener != null){
                                SmbFileChooserDialog.this._negativeListener.onClick(SmbFileChooserDialog.this._alertDialog, AlertDialog.BUTTON_NEGATIVE);
                            }
                        }
                    });

					if(SmbFileChooserDialog.this._dirOnly){
						Button positive = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
						positive.setOnClickListener(new View.OnClickListener(){
							@Override
							public void onClick(final View v){
                                if(SmbFileChooserDialog.this._enableMultiple){
                                    if(SmbFileChooserDialog.this._adapter.isAnySelected()){
                                        if(SmbFileChooserDialog.this._adapter.isOneSelected()){
                                            if(SmbFileChooserDialog.this._onChosenListener != null){
                                                final SmbFile selected = _adapter.getSelected().get(0);
                                                SmbFileChooserDialog.this._onChosenListener.onChoosePath(selected.getPath(), selected);
                                            }
                                        } else{
                                            if(SmbFileChooserDialog.this._onSelectedListener != null){
                                                SmbFileChooserDialog.this._onSelectedListener.onSelectFiles(_adapter.getSelected());
                                            }
                                        }
                                    }
                                } else if(SmbFileChooserDialog.this._onChosenListener != null){
                                    SmbFileChooserDialog.this._onChosenListener.onChoosePath(SmbFileChooserDialog.this._currentDir.getPath(), SmbFileChooserDialog.this._currentDir);
                                }
                            }
						});
					}
                }

                if(SmbFileChooserDialog.this._enableOptions){
                    final int color = UiUtil.getThemeAccentColor(getBaseContext());
                    final PorterDuffColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);

                    final Button options = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
                    options.setText("");
                    options.setVisibility(View.VISIBLE);
                    options.setTextColor(color);
                    final Drawable drawable = ContextCompat.getDrawable(getBaseContext(),
                            SmbFileChooserDialog.this._optionsIconRes != -1 ? SmbFileChooserDialog.this._optionsIconRes : R.drawable.ic_menu_24dp);
                    if(drawable != null){
                        drawable.setColorFilter(filter);
                        options.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
                    } else{
                        options.setCompoundDrawablesWithIntrinsicBounds(
                                SmbFileChooserDialog.this._optionsIconRes != -1 ? SmbFileChooserDialog.this._optionsIconRes : R.drawable.ic_menu_24dp, 0, 0, 0);
                    }

                    final Runnable showOptions = new Runnable(){
                        @Override
                        public void run(){
                            final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) SmbFileChooserDialog.this._list.getLayoutParams();
                            if(SmbFileChooserDialog.this._options.getHeight() == 0){
                                ViewTreeObserver viewTreeObserver = SmbFileChooserDialog.this._options.getViewTreeObserver();
                                viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (SmbFileChooserDialog.this._options.getHeight() <= 0) { return false; }
                                        SmbFileChooserDialog.this._options.getViewTreeObserver().removeOnPreDrawListener(this);
                                        Handler handler = new Handler();
                                        handler.postDelayed(new Runnable(){
                                            @Override
                                            public void run(){
                                                params.bottomMargin = _options.getHeight();
                                                SmbFileChooserDialog.this._list.setLayoutParams(params);
                                                SmbFileChooserDialog.this._options.setVisibility(View.VISIBLE);
                                                SmbFileChooserDialog.this._options.requestFocus();
                                            }
                                        }, 100); // Just to make sure that the View has been drawn, so the transition is smoother.
                                        return true;
                                    }
                                });
                            } else{
                                params.bottomMargin = SmbFileChooserDialog.this._options.getHeight();
                                SmbFileChooserDialog.this._list.setLayoutParams(params);
                                SmbFileChooserDialog.this._options.setVisibility(View.VISIBLE);
                                SmbFileChooserDialog.this._options.requestFocus();
                            }
                        }
                    };
                    final Runnable hideOptions = new Runnable(){
                        @Override
                        public void run(){
                            SmbFileChooserDialog.this._options.setVisibility(View.INVISIBLE);
                            SmbFileChooserDialog.this._options.clearFocus();
                            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) SmbFileChooserDialog.this._list.getLayoutParams();
                            params.bottomMargin = 0;
                            SmbFileChooserDialog.this._list.setLayoutParams(params);
                        }
                    };

                    options.setOnClickListener(new View.OnClickListener(){
                        @Override
                        public void onClick(final View v){
                            if(SmbFileChooserDialog.this._options == null){
                                // region Draw options view. (this only happens the first time one clicks on options)
                                // Root view (FrameLayout) of the ListView in the AlertDialog.
                                final int rootId = getResources().getIdentifier("contentPanel", "id", "android");
                                final FrameLayout root = ((AlertDialog) dialog).findViewById(rootId);
                                // In case the was changed or not found.
                                if(root == null)
                                    return;

                                // Create options view.
                                final FrameLayout options = new FrameLayout(getBaseContext());
                                //options.setBackgroundColor(0x60000000);
                                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(MATCH_PARENT, 60, BOTTOM);
                                root.addView(options, params);

                                options.setOnClickListener(null);
                                options.setVisibility(View.INVISIBLE);
                                SmbFileChooserDialog.this._options = options;

                                // Create a button for the option to create a new directory/folder.
                                final Button createDir = new Button(getBaseContext(), null, android.R.attr.buttonBarButtonStyle);
                                if(SmbFileChooserDialog.this._createDirRes == -1) createDir.setText(SmbFileChooserDialog.this._createDir);
                                  else createDir.setText(SmbFileChooserDialog.this._createDirRes);
                                createDir.setTextColor(color);
                                final Drawable plus = ContextCompat.getDrawable(getBaseContext(),
                                        SmbFileChooserDialog.this._createDirIconRes != -1 ? SmbFileChooserDialog.this._createDirIconRes : R.drawable.ic_add_24dp);
                                if(plus != null){
                                    plus.setColorFilter(filter);
                                    createDir.setCompoundDrawablesWithIntrinsicBounds(plus, null, null, null);
                                } else{
                                    createDir.setCompoundDrawablesWithIntrinsicBounds(
                                            SmbFileChooserDialog.this._createDirIconRes != -1 ? SmbFileChooserDialog.this._createDirIconRes : R.drawable.ic_add_24dp, 0, 0, 0);
                                }
                                if(SmbFileChooserDialog.this._enableDpad){
                                    createDir.setBackgroundResource(R.drawable.listview_item_selector);
                                }
                                params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, START | CENTER_VERTICAL);
                                params.leftMargin = 10;
                                options.addView(createDir, params);

                                // Create a button for the option to delete a file.
                                final Button delete = new Button(getBaseContext(), null, android.R.attr.buttonBarButtonStyle);
                                if(SmbFileChooserDialog.this._deleteRes == -1) delete.setText(SmbFileChooserDialog.this._delete);
                                  else delete.setText(SmbFileChooserDialog.this._deleteRes);
                                delete.setTextColor(color);
                                final Drawable bin = ContextCompat.getDrawable(getBaseContext(),
                                        SmbFileChooserDialog.this._deleteIconRes != -1 ? SmbFileChooserDialog.this._deleteIconRes : R.drawable.ic_delete_24dp);
                                if(bin != null){
                                    bin.setColorFilter(filter);
                                    delete.setCompoundDrawablesWithIntrinsicBounds(bin, null, null, null);
                                } else{
                                    delete.setCompoundDrawablesWithIntrinsicBounds(
                                            SmbFileChooserDialog.this._deleteIconRes != -1 ? SmbFileChooserDialog.this._deleteIconRes : R.drawable.ic_delete_24dp, 0, 0, 0);
                                }
                                if(SmbFileChooserDialog.this._enableDpad){
                                    delete.setBackgroundResource(R.drawable.listview_item_selector);
                                }
                                params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, END | CENTER_VERTICAL);
                                params.rightMargin = 10;
                                options.addView(delete, params);

                                // Event Listeners.
                                createDir.setOnClickListener(new View.OnClickListener(){
                                    private EditText input = null;

                                    @Override
                                    public void onClick(final View v1){
                                        //Toast.makeText(getBaseContext(), "new folder clicked", Toast.LENGTH_SHORT).show();
                                        hideOptions.run();
                                        final Future<String> futureNewFile = EXECUTOR.submit(new Callable<String>(){
                                            @Override
                                            public String call(){
                                                try{
                                                    SmbFile newFolder = new SmbFile(SmbFileChooserDialog.this._currentDir.getPath(), "New folder", _auth);
                                                    for(int i = 1; newFolder.exists(); i++)
                                                        newFolder = new SmbFile(SmbFileChooserDialog.this._currentDir.getPath(), "New Folder (" + i + ')', _auth);
                                                    return newFolder.getName();
                                                } catch(MalformedURLException | SmbException e){
                                                    e.printStackTrace();
                                                    return "";
                                                }
                                            }
                                        });

                                        if(SmbFileChooserDialog.this._newFolderView == null){
                                            // region Draw a view with input to create new folder. (this only happens the first time one clicks on New folder)
                                            try{
                                                //noinspection ConstantConditions
                                                ((AlertDialog) dialog).getWindow().clearFlags(FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM);
                                                //noinspection ConstantConditions
                                                ((AlertDialog) dialog).getWindow().setSoftInputMode(SOFT_INPUT_STATE_VISIBLE);
                                            } catch(NullPointerException e){
                                                e.printStackTrace();
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
                                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                                                holder.setElevation(20f);
                                            } else{
                                                ViewCompat.setElevation(holder, 20);
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
                                            if(SmbFileChooserDialog.this._newFolderCancelRes == -1) cancel.setText(SmbFileChooserDialog.this._newFolderCancel);
                                              else cancel.setText(SmbFileChooserDialog.this._newFolderCancelRes);
											cancel.setTextColor(color);
                                            if(SmbFileChooserDialog.this._enableDpad){
                                                cancel.setBackgroundResource(R.drawable.listview_item_selector);
                                            }
                                            params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, START);
                                            buttons.addView(cancel, params);

                                            // The OK button.
                                            final Button ok = new Button(getBaseContext(), null, android.R.attr.buttonBarButtonStyle);
                                            if(SmbFileChooserDialog.this._newFolderOkRes == -1) ok.setText(SmbFileChooserDialog.this._newFolderOk);
                                              else ok.setText(SmbFileChooserDialog.this._newFolderOkRes);
											ok.setTextColor(color);
                                            if(SmbFileChooserDialog.this._enableDpad){
                                                ok.setBackgroundResource(R.drawable.listview_item_selector);
                                            }
                                            params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, END);
                                            buttons.addView(ok, params);

                                            // Event Listeners.
                                            input.setOnEditorActionListener(new TextView.OnEditorActionListener(){
                                                @Override
                                                public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event){
                                                    if(actionId == EditorInfo.IME_ACTION_DONE){
                                                        UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                        if(!SmbFileChooserDialog.this._enableDpad){
                                                            SmbFileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                            overlay.setVisibility(View.INVISIBLE);
                                                            overlay.clearFocus();
                                                        } else{
                                                            input.requestFocus();
                                                        }
                                                        return true;
                                                    }
                                                    return false;
                                                }
                                            });
                                            cancel.setOnClickListener(new View.OnClickListener(){
                                                @Override
                                                public void onClick(final View v){
                                                    UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                    overlay.setVisibility(View.INVISIBLE);
                                                    overlay.clearFocus();
                                                    if(SmbFileChooserDialog.this._enableDpad){
                                                        SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(true);
                                                        SmbFileChooserDialog.this._list.setFocusable(true);
                                                    }
                                                }
                                            });
                                            ok.setOnClickListener(new View.OnClickListener(){
                                                @Override
                                                public void onClick(final View v){
                                                    SmbFileChooserDialog.this.createNewDirectory(input.getText().toString());
                                                    UiUtil.hideKeyboardFrom(getBaseContext(), input);
                                                    overlay.setVisibility(View.INVISIBLE);
                                                    overlay.clearFocus();
                                                    if(SmbFileChooserDialog.this._enableDpad){
                                                        SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(true);
                                                        SmbFileChooserDialog.this._list.setFocusable(true);
                                                    }
                                                }
                                            });
                                            // endregion
                                        }

                                        if(SmbFileChooserDialog.this._newFolderView.getVisibility() == View.INVISIBLE){
                                            SmbFileChooserDialog.this._newFolderView.setVisibility(View.VISIBLE);
                                            if(SmbFileChooserDialog.this._enableDpad){
                                                SmbFileChooserDialog.this._newFolderView.requestFocus();
                                                SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(false);
                                                SmbFileChooserDialog.this._list.setFocusable(false);
                                            }
                                            if(this.input != null){
                                                try{
                                                    this.input.setText(futureNewFile.get());
                                                } catch(InterruptedException | ExecutionException e){
                                                    e.printStackTrace();
                                                    this.input.setText("");
                                                }
                                            }
                                        } else{
                                            SmbFileChooserDialog.this._newFolderView.setVisibility(View.INVISIBLE);
                                            if(SmbFileChooserDialog.this._enableDpad){
                                                SmbFileChooserDialog.this._newFolderView.clearFocus();
                                                SmbFileChooserDialog.this._alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setFocusable(true);
                                                SmbFileChooserDialog.this._list.setFocusable(true);
                                            }
                                        }
                                    }
                                });
                                delete.setOnClickListener(new View.OnClickListener(){
                                    @Override
                                    public void onClick(final View v1){
                                        //Toast.makeText(getBaseContext(), "delete clicked", Toast.LENGTH_SHORT).show();
                                        hideOptions.run();

                                        if(SmbFileChooserDialog.this._chooseMode == CHOOSE_MODE_SELECT_MULTIPLE){
                                            try{
                                                final Queue<SmbFile> parents = new ArrayDeque<SmbFile>();
                                                EXECUTOR.submit(new Callable<Void>(){
                                                    @Override
                                                    public Void call() throws MalformedURLException{
                                                        final String parentPath = SmbFileChooserDialog.this._currentDir.getParent();
                                                        SmbFile current = new SmbFile(parentPath, _auth);
                                                        while(!current.equals(SmbFileChooserDialog.this._rootDir)){
                                                            parents.add(current);
                                                            final String parent = current.getParent();
                                                            current = new SmbFile(parent, _auth);
                                                        }
                                                        return null;
                                                    }
                                                }).get();

                                                for(SmbFile file : SmbFileChooserDialog.this._adapter.getSelected()){
                                                    deleteFile(file);
                                                }
                                                SmbFileChooserDialog.this._adapter.clearSelected();

                                                SmbFile currentDir = EXECUTOR.submit( new Callable<SmbFile>(){
                                                    @Override
                                                    public SmbFile call() throws SmbException{
                                                        if(!SmbFileChooserDialog.this._currentDir.exists()){
                                                            SmbFile parent;

                                                            while((parent = parents.poll()) != null){
                                                                if(parent.exists()) break;
                                                            }

                                                            if(parent != null && parent.exists()){
                                                                SmbFileChooserDialog.this._currentDir = parent;
                                                            } else{
                                                                SmbFileChooserDialog.this._currentDir = SmbFileChooserDialog.this._rootDir;
                                                            }
                                                        }
                                                        return SmbFileChooserDialog.this._currentDir;
                                                    }
                                                }).get();

                                                boolean scrollTop = !SmbFileChooserDialog.this._currentDir.equals(currentDir);
                                                SmbFileChooserDialog.this._currentDir = currentDir;

                                                refreshDirs();
                                                if(scrollTop) SmbFileChooserDialog.this._list.setSelection(0);
                                            } catch(InterruptedException | ExecutionException e){
                                                e.printStackTrace();
                                            }

                                            SmbFileChooserDialog.this._chooseMode = CHOOSE_MODE_NORMAL;
                                            SmbFileChooserDialog.this._deleteMode.run();
                                            return;
                                        }

                                        SmbFileChooserDialog.this._chooseMode = SmbFileChooserDialog.this._chooseMode != CHOOSE_MODE_DELETE ? CHOOSE_MODE_DELETE : CHOOSE_MODE_NORMAL;
                                        if(SmbFileChooserDialog.this._deleteMode == null){
                                            SmbFileChooserDialog.this._deleteMode = new Runnable(){
                                                @Override
                                                public void run(){
                                                    if(SmbFileChooserDialog.this._chooseMode == CHOOSE_MODE_DELETE){
                                                        final int color = 0x80ff0000;
                                                        final PorterDuffColorFilter red = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
                                                        _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).getCompoundDrawables()[0].setColorFilter(red);
                                                        _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(color);
                                                        delete.getCompoundDrawables()[0].setColorFilter(red);
                                                        delete.setTextColor(color);
                                                    } else{
                                                        _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).getCompoundDrawables()[0].clearColorFilter();
                                                        _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(color);
                                                        delete.getCompoundDrawables()[0].clearColorFilter();
                                                        delete.setTextColor(color);
                                                    }
                                                }
                                            };
                                        }
                                        SmbFileChooserDialog.this._deleteMode.run();
                                    }
                                });
                                // endregion
                            }

                            if(SmbFileChooserDialog.this._options.getVisibility() == View.VISIBLE){
                                hideOptions.run();
                            } else{
                                showOptions.run();
                            }
                        }
                    });
                }
            }
        });

        this._list = this._alertDialog.getListView();
        this._list.setOnItemClickListener(this);
        if(this._enableMultiple){
            this._list.setOnItemLongClickListener(this);
        }

        if(this._enableDpad){
            this._list.setSelector(R.drawable.listview_item_selector);
            this. _list.setDrawSelectorOnTop(true);
            this._list.setItemsCanFocus(true);
            this._list.setOnItemSelectedListener(this);
            this._alertDialog.setOnKeyListener(this);
        }
        return this;
    }

    @NonNull public SmbFileChooserDialog show(){
        if(_alertDialog == null || _list == null){
            throw new RuntimeException("Dialog has not been built yet! (call .build() before .show())");
        }

        // Check for permissions if SDK version is >= 23
        if(Build.VERSION.SDK_INT >= 23){
            final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 0;
            int permissionCheck = ContextCompat.checkSelfPermission(getBaseContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE);

            //if = permission granted
            if(permissionCheck == PackageManager.PERMISSION_GRANTED){
                _alertDialog.show();
            } else{
                ActivityCompat.requestPermissions((Activity) getBaseContext(),
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
            }
        } else{
            _alertDialog.show();

        }

        if(_enableMultiple && !_dirOnly){
            _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.INVISIBLE);
        }

        if(_enableDpad){
            _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setBackgroundResource(R.drawable.listview_item_selector);
            _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundResource(R.drawable.listview_item_selector);
            _alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundResource(R.drawable.listview_item_selector);
        }
        return this;
    }

    private void listDirs(){
        final Future ret = EXECUTOR.submit(new Runnable(){
            @Override
            public void run(){
                try{
                    _entries.clear();

                    // Add the ".." entry
                    final String parent = _currentDir.getParent();
                    if(parent != null && !parent.equalsIgnoreCase("smb://")){
                        _entries.add(new SmbFile("smb://.."));
                    }

                    // Get files
                    SmbFile[] files = _currentDir.listFiles(_fileFilter);

                    if(files == null) return;

                    List<SmbFile> dirList = new LinkedList<SmbFile>();
                    List<SmbFile> fileList = new LinkedList<SmbFile>();

                    for(SmbFile f : files){
                        if(f.isDirectory()){
                            dirList.add(f);
                        } else{
                            fileList.add(f);
                        }
                    }

                    SmbFileChooserDialog.this.sortByName(dirList);
                    SmbFileChooserDialog.this.sortByName(fileList);
                    _entries.addAll(dirList);
                    _entries.addAll(fileList);
                } catch(SmbException | MalformedURLException e){
                    e.printStackTrace();
                }
            }
        });
        try{
            ret.get();
        } catch(InterruptedException e){
            e.printStackTrace();
        } catch(ExecutionException e){
            e.printStackTrace();
        }
    }

    void sortByName(@NonNull List<SmbFile> list) {
        Collections.sort(list, new Comparator<SmbFile>(){
            @Override
            public int compare(final SmbFile f1, final SmbFile f2){
                return f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase());
            }
        });
    }

    /**
     * @deprecated better use listDirs as it sorts directories and files separately
     */
    @Deprecated
    private void listDirsUncategorised(){
        final Future ret = EXECUTOR.submit(new Runnable(){
            @Override
            public void run(){
                try{
                    _entries.clear();

                    // Get files
                    SmbFile[] files = _currentDir.listFiles(_fileFilter);

                    if (files != null) {
                        _entries.addAll(Arrays.asList(files));
                    }

                    sortByName(_entries);

                    // Add the ".." entry
                    final String parent = _currentDir.getParent();
                    if(parent != null && !parent.equalsIgnoreCase("smb://")){
                        _entries.add(0, new SmbFile(".."));
                    }
                } catch(MalformedURLException | SmbException e){
                    e.printStackTrace();
                }
            }
        });
        try{
            ret.get();
        } catch(InterruptedException | ExecutionException e){
            e.printStackTrace();
        }
    }

    private void createNewDirectory(@NonNull final String name){
        EXECUTOR.execute(new Runnable(){
            @Override
            public void run(){
                try{
                    final SmbFile newDir = new SmbFile(SmbFileChooserDialog.this._currentDir.getPath(), name, SmbFileChooserDialog.this._auth);
                    if(!newDir.exists()){
                        newDir.mkdirs();
                        runOnUiThread(new Runnable(){
                            @Override
                            public void run(){
                                refreshDirs();
                            }
                        });
                        return;
                    }
                } catch(MalformedURLException | SmbException e){
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable(){
                    @Override
                    public void run(){
                        Toast.makeText(getBaseContext(), "Couldn't create folder " + name + " at " + SmbFileChooserDialog.this._currentDir, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // todo: ask for confirmation! (inside an AlertDialog.. Ironical, I know)
    private Runnable _deleteMode;
    private void deleteFile(@NonNull final SmbFile file){
        try{
            this._chooseMode = CHOOSE_MODE_NORMAL;
            if(EXECUTOR.submit(new Callable<Boolean>(){
                @Override
                public Boolean call(){
                    try{
                        file.delete();
                        return true;
                    } catch(SmbException e){
                        e.printStackTrace();
                        return false;
                    }
                }
            }).get()) return;
        } catch(InterruptedException | ExecutionException e){
            e.printStackTrace();
        }
        Toast.makeText(getBaseContext(), "Couldn't delete " + file.getName() + " at " + file.getPath(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onItemClick(@Nullable final AdapterView<?> parent, @NonNull final View list, final int position, final long id) {
        try{
            if(position < 0 || position >= _entries.size()) return;

            View focus = _list;
            Triple<SmbFile, Boolean, String> triple = EXECUTOR.submit(new Callable<Triple<SmbFile, Boolean, String>>(){
                @Override
                public Triple<SmbFile, Boolean, String> call() throws MalformedURLException, SmbException{
                    SmbFile file = _entries.get(position);
                    if(file.getName().equals("../") || file.getName().equals("..")){
                        final String parentPath = _currentDir.getParent();
                        final SmbFile f = new SmbFile(parentPath, _auth);
                        if(_folderNavUpCB == null) _folderNavUpCB = _defaultNavUpCB;
                        if(_folderNavUpCB.canUpTo(f)) {
                            _currentDir = f;
                            _chooseMode = _chooseMode == CHOOSE_MODE_DELETE ? CHOOSE_MODE_NORMAL : _chooseMode;
                            if (_deleteMode != null) _deleteMode.run();
                            lastSelected = false;
                            return new Triple<SmbFile, Boolean, String>(null, true, null);
                        }
                    }
                    return new Triple<SmbFile, Boolean, String>(file, file.isDirectory(), file.getPath());
                }
            }).get();

            final SmbFile file = triple.getFirst();
            final boolean isDirectory = triple.getSecond();
            final String path = triple.getThird();
            boolean scrollToTop = false;

            if(file != null){
                switch(_chooseMode){
                    case CHOOSE_MODE_NORMAL:
                        if (isDirectory){
                            if (_folderNavToCB == null) _folderNavToCB = _defaultNavToCB;
                            if (_folderNavToCB.canNavigate(file)) {
                                _currentDir = file;
                                scrollToTop = true;
                            }
                        } else if ((!_dirOnly) && _onChosenListener != null){
                            _onChosenListener.onChoosePath(path, file);
                            if(_dismissOnButtonClick) _alertDialog.dismiss();
                        }
                        lastSelected = false;
                        break;
                    case CHOOSE_MODE_SELECT_MULTIPLE:
                        if(isDirectory){
                            if (_folderNavToCB == null) _folderNavToCB = _defaultNavToCB;
                            if (_folderNavToCB.canNavigate(file)) {
                                _currentDir = file;
                                scrollToTop = true;
                            }
                        } else{
                            if(_enableDpad) focus = _alertDialog.getCurrentFocus();
                            _adapter.selectItem(position);
                            if(!_adapter.isAnySelected()){
                                _chooseMode = CHOOSE_MODE_NORMAL;
                                if(!_dirOnly) _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.INVISIBLE);
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
            } else{
                scrollToTop = isDirectory;
            }

            refreshDirs();
            if(scrollToTop) _list.setSelection(0);
            if(_enableDpad){
                if(focus == null) _list.requestFocus();
                  else focus.requestFocus();
            }
        } catch(InterruptedException | ExecutionException e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean onItemLongClick(@Nullable final AdapterView<?> parent, @NonNull final View list, final int position, final long id) {
        try{
            if(EXECUTOR.submit(new Callable<Boolean>(){
                @Override
                public Boolean call() throws SmbException{
                    SmbFile file = _entries.get(position);
                    return !file.getName().equals("../") && !file.getName().equals("..") && (_allowSelectDir || !file.isDirectory());
                }
            }).get()){
                _adapter.selectItem(position);
                if(!_adapter.isAnySelected()){
                    _chooseMode = CHOOSE_MODE_NORMAL;
                    if(!_dirOnly) _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.INVISIBLE);
                } else{
                    _chooseMode = CHOOSE_MODE_SELECT_MULTIPLE;
                    if(!_dirOnly) _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                }
            }
            return true;
        } catch(InterruptedException | ExecutionException e){
            e.printStackTrace();
        }
        return false;
    }

    private boolean lastSelected = false;
    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id){
        lastSelected = position == _entries.size() - 1;
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent){
        lastSelected = false;
    }

    @Override
    public boolean onKey(final DialogInterface dialog, final int keyCode, final KeyEvent event){
        if(event.getAction() != KeyEvent.ACTION_DOWN) return false;

        if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN){
            if(lastSelected && _list.hasFocus()){
                lastSelected = false;
                if(_options != null && _options.getVisibility() == View.VISIBLE){
                    _options.requestFocus();
                } else{
                    _alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).requestFocus();
                }
                return true;
            }

        }

        if(keyCode == KeyEvent.KEYCODE_DPAD_UP){
            if(_alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).hasFocus()){
                if(_options != null && _options.getVisibility() == View.VISIBLE){
                    _options.requestFocus(View.FOCUS_RIGHT);
                } else{
                    _list.requestFocus();
                    lastSelected = true;
                }
                return true;
            } else if(_alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus()
                || _alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus()){
                if(_options != null && _options.getVisibility() == View.VISIBLE){
                    _options.requestFocus(View.FOCUS_LEFT);
                    return true;
                } else{
                    _list.requestFocus();
                    lastSelected = true;
                    return true;
                }
            }

            if(_options != null && _options.hasFocus()){
                _list.requestFocus();
                lastSelected = true;
                return true;
            }
        }

        if(_list.hasFocus()) {
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

    private void refreshDirs(){
        listDirs();
        _adapter.setEntries(_entries);
    }

    public void dismiss(){
        if(_alertDialog == null) return;
        _alertDialog.dismiss();
    }

    public void cancel(){
        if(_alertDialog == null) return;
        _alertDialog.cancel();
    }

    private List<SmbFile> _entries = new ArrayList<>();
    private SmbDirAdapter _adapter;
    private SmbFile _currentDir;
    private String _rootDirPath;
    private SmbFile _rootDir;
    private NtlmPasswordAuthentication _auth = null;
    private AlertDialog _alertDialog;
    private ListView _list;
    private OnChosenListener _onChosenListener = null;
    private OnSelectedListener _onSelectedListener = null;
    private boolean _dirOnly;
    private SmbFileFilter _fileFilter;
    private @StringRes int _titleRes = 0, _okRes = 0, _negativeRes = 0;
    private @NonNull String _title = "Select a file", _ok = "Choose", _negative = "Cancel";
    private @DrawableRes int _iconRes = -1;
    //private Drawable _icon = null;
    private @LayoutRes int _layoutRes = -1;
    private @LayoutRes int _rowLayoutRes = -1;
    private String _dateFormat;
    private DialogInterface.OnClickListener _negativeListener;
    private DialogInterface.OnCancelListener _onCancelListener;
    private boolean _disableTitle;
    private boolean _cancelable = true;
    private boolean _cancelOnTouchOutside;
    private boolean _dismissOnButtonClick = true;
    private DialogInterface.OnDismissListener _onDismissListener;
    private boolean _enableOptions;
    private View _options;
    private @StringRes int _createDirRes = -1, _deleteRes = -1, _newFolderCancelRes = -1, _newFolderOkRes = -1;
    private @NonNull String _createDir = "New folder", _delete = "Delete", _newFolderCancel = "Cancel", _newFolderOk = "Ok";
    private @DrawableRes int _optionsIconRes = -1, _createDirIconRes = -1, _deleteIconRes = -1;
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

    private final static CanNavigateUp _defaultNavUpCB = new CanNavigateUp(){
        @Override
        public boolean canUpTo(final SmbFile dir) throws SmbException{
            return dir != null && dir.canRead();
        }
    };

    private final static CanNavigateTo _defaultNavToCB = new CanNavigateTo(){
        @Override
        public boolean canNavigate(final SmbFile dir){
            return true;
        }
    };

    @FunctionalInterface
    public interface OnBackPressedListener{
        void onBackPressed(@NonNull AlertDialog dialog);
    }

    private OnBackPressedListener _onBackPressed = new OnBackPressedListener(){
        @Override
        public void onBackPressed(@NonNull final AlertDialog dialog){
            if(SmbFileChooserDialog.this._entries.size() > 0
                    && (SmbFileChooserDialog.this._entries.get(0).getName().equals("../") || SmbFileChooserDialog.this._entries.get(0).getName().equals(".."))){
                SmbFileChooserDialog.this.onItemClick(null, SmbFileChooserDialog.this._list, 0, 0);
            } else{
                if(SmbFileChooserDialog.this._onLastBackPressed != null) SmbFileChooserDialog.this._onLastBackPressed.onBackPressed(dialog);
                  else SmbFileChooserDialog.this._defaultLastBack.onBackPressed(dialog);
            }
        }
    };
    private OnBackPressedListener _onLastBackPressed;

    private OnBackPressedListener _defaultLastBack = new OnBackPressedListener(){
        @Override
        public void onBackPressed(@NonNull final AlertDialog dialog){
            dialog.dismiss();
        }
    };

    private static final int CHOOSE_MODE_NORMAL = 0;
    private static final int CHOOSE_MODE_DELETE = 1;
    private static final int CHOOSE_MODE_SELECT_MULTIPLE = 2;

    private int _chooseMode = CHOOSE_MODE_NORMAL;

    private NewFolderFilter _newFolderFilter;
}
