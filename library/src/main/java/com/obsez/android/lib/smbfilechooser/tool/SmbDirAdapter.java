package com.obsez.android.lib.smbfilechooser.tool;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.obsez.android.lib.smbfilechooser.R;
import com.obsez.android.lib.smbfilechooser.internals.FileUtil;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import kotlin.Triple;

import static com.obsez.android.lib.smbfilechooser.SmbFileChooserDialog.getNetworkThread;

/**
 * Created by coco on 6/9/18. Edited by Guiorgy on 10/09/18.
 */
public class SmbDirAdapter extends MyAdapter<SmbFile> {
    public SmbDirAdapter(Context cxt, int resId, String dateFormat) {
        super(cxt, resId);
        this.init(dateFormat);
    }

    @SuppressLint("SimpleDateFormat")
    private void init(String dateFormat) {
        _formatter = new SimpleDateFormat(dateFormat != null && !"".equals(dateFormat.trim()) ? dateFormat.trim() : "yyyy/MM/dd HH:mm:ss");
        _defaultFolderIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_folder);
        _defaultFileIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_file);

        int accentColor = UiUtil.getThemeAccentColor(getContext());
        int red = Color.red(accentColor);
        int green = Color.green(accentColor);
        int blue = Color.blue(accentColor);
        int accentColorWithAlpha = Color.argb(40, red, green, blue);
        _colorFilter = new PorterDuffColorFilter(accentColorWithAlpha, PorterDuff.Mode.MULTIPLY);
    }

    private static final class File {
        final String name;
        final Drawable icon;
        final boolean isDirectory;
        final long lastModified;
        final String fileSize;
        final int hashCode;

        File(String name, Drawable icon, boolean isDirectory, long lastModified, String fileSize, int hashCode) {
            this.name = name;
            this.icon = icon;
            this.isDirectory = isDirectory;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
            this.hashCode = hashCode;
        }

        static int hashCode(SmbFile file) {
            // For some reason SmbFile default hashCode function takes ages to compute!
            return file.getServer().hashCode() + file.getCanonicalPath().hashCode();
        }
    }

    // This function is called to show each view item
    @NonNull
    @Override
    public View getView(final int position, final View convertView, @NonNull final ViewGroup parent) {
        ViewGroup rl = (ViewGroup) super.getView(position, convertView, parent);

        new GetViewAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, this, getItem(position), rl);

        return rl;
    }

    private static class GetViewAsync extends AsyncTask<Object, Void, Triple<SmbDirAdapter, View, File>> {
        @Override
        protected Triple<SmbDirAdapter, View, File> doInBackground(final Object... Objects) {
            try {
                SmbDirAdapter adapter = (SmbDirAdapter) Objects[0];
                SmbFile file = (SmbFile) Objects[1];
                if (file == null) return null;
                String name = file.getName();
                name = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                boolean isDirectory = file.isDirectory();
                Drawable icon = isDirectory ? adapter._defaultFolderIcon : adapter._defaultFileIcon;
                if (file.isHidden()) {
                    try {
                        final PorterDuffColorFilter filter = new PorterDuffColorFilter(0x70ffffff, PorterDuff.Mode.SRC_ATOP);
                        //noinspection ConstantConditions
                        icon = icon.getConstantState().newDrawable().mutate();
                        icon.setColorFilter(filter);
                    } catch (NullPointerException ignore) {
                        // ignore
                    }
                }
                long lastModified = isDirectory ? 0L : file.lastModified();
                String fileSize = isDirectory ? "" : FileUtil.getReadableFileSize(file.getContentLengthLong());
                return new Triple<>(adapter, (View) Objects[2], new File(name, icon, isDirectory, lastModified, fileSize, File.hashCode(file)));
            } catch (SmbException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Triple<SmbDirAdapter, View, File> triple) {
            if (triple == null) return;
            final SmbDirAdapter adapter = triple.getFirst();
            final View rl = triple.getSecond();
            final File file = triple.getThird();
            if (adapter == null || rl == null || file == null) {
                cancel(true);
                return;
            }

            final View root = rl.findViewById(R.id.root);
            final TextView tvName = rl.findViewById(R.id.text);
            final TextView tvSize = rl.findViewById(R.id.txt_size);
            final TextView tvDate = rl.findViewById(R.id.txt_date);
            //ImageView ivIcon = (ImageView) rl.findViewById(R.id.icon);

            tvName.setText(file.name);
            tvName.setCompoundDrawablesWithIntrinsicBounds(file.icon, null, null, null);
            if (file.lastModified != 0L) {
                tvDate.setText(_formatter.format(new Date(file.lastModified)));
                tvDate.setVisibility(View.VISIBLE);
            } else {
                tvDate.setVisibility(View.GONE);
            }
            tvSize.setText(file.fileSize);
            if (adapter.getSelected(file.hashCode) == null) root.getBackground().clearColorFilter();
            else root.getBackground().setColorFilter(adapter._colorFilter);
        }
    }

    public Drawable getDefaultFolderIcon() {
        return _defaultFolderIcon;
    }

    public void setDefaultFolderIcon(Drawable defaultFolderIcon) {
        this._defaultFolderIcon = defaultFolderIcon;
    }

    public Drawable getDefaultFileIcon() {
        return _defaultFileIcon;
    }

    public void setDefaultFileIcon(Drawable defaultFileIcon) {
        this._defaultFileIcon = defaultFileIcon;
    }

    /**
     * @deprecated no point. can't get file icons on a samba server
     */
    @Deprecated
    public boolean isResolveFileType() {
        //noinspection deprecation
        return _resolveFileType;
    }

    /**
     * @deprecated no point. can't get file icons on a samba server
     */
    @Deprecated
    public void setResolveFileType(boolean resolveFileType) {
        //noinspection deprecation
        this._resolveFileType = resolveFileType;
    }

    @Override
    public long getItemId(final int position) {
        Future<Long> ret = getNetworkThread().submit(() -> {
            //noinspection ConstantConditions
            return (long) File.hashCode(getItem(position));
        });

        try {
            return ret.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return position;
    }

    private static SimpleDateFormat _formatter;
    private Drawable _defaultFolderIcon = null;
    private Drawable _defaultFileIcon = null;
    /**
     * @deprecated no point. can't get file icons on a samba server
     */
    @Deprecated
    private boolean _resolveFileType = false;
    private PorterDuffColorFilter _colorFilter;
}

