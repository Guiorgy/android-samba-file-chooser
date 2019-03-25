package com.obsez.android.lib.smbfilechooser.tool;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.obsez.android.lib.smbfilechooser.R;
import com.obsez.android.lib.smbfilechooser.internals.FileUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.collection.SparseArrayCompat;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import kotlin.Pair;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by coco on 6/9/18. Edited by Guiorgy on 10/09/18.
 */
public class SmbDirAdapter extends MyAdapter<SmbFile> {
    public SmbDirAdapter(Context cxt, String dateFormat) {
        super(cxt, dateFormat);
    }

    @SuppressWarnings("WeakerAccess")
    public static final class FileInfo {
        public final String share;
        public final String name;
        public final Drawable icon;
        public final boolean isDirectory;
        public final long lastModified;
        public final String fileSize;
        public final boolean isHidden;

        FileInfo(String share, String name, Drawable icon, boolean isDirectory, long lastModified, String fileSize, boolean isHidden) {
            this.share = share;
            this.name = name;
            this.icon = icon;
            this.isDirectory = isDirectory;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
            this.isHidden = isHidden;
        }

        static int hashCode(SmbFile file) {
            // For some reason SmbFile default hashCode function takes ages to compute!
            return file.getServer().hashCode() + file.getCanonicalPath().hashCode();
        }
    }

    @FunctionalInterface
    public interface BindView {
        /**
         * @param file       basic information about the file that can be accessed on main thread
         *                   see {@link FileInfo}
         * @param isSelected whether file is selected when _enableMultiple is set to true
         * @param view       pre-inflated view to be bound
         */
        void bindView(@NonNull FileInfo file, boolean isSelected, View view);
    }

    @Override
    public void overrideGetView(GetView<SmbFile> getView) {
        super.overrideGetView(getView);
    }

    public void overrideGetView(GetView<SmbFile> getView, BindView bindView) {
        overrideGetView(getView);
        this._bindView = bindView;
    }

    // This function is called to show each view item
    @NonNull
    @Override
    public View getView(final int position, final View convertView, @NonNull final ViewGroup parent) {
        SmbFile file = getItem(position);
        if (file == null) return super.getView(position, convertView, parent);
        final int hashCode = FileInfo.hashCode(file);
        final boolean isSelected = getSelected(hashCode) != null;

        if (_getView != null)
            //noinspection unchecked
            return _getView.getView(file, isSelected, convertView, parent, LayoutInflater.from(getContext()));

        ViewGroup view = (ViewGroup) super.getView(position, convertView, parent);
        view.setVisibility(GONE);
        _getViewAsync.bindView(hashCode, view, isSelected);

        return view;
    }

    private static class LoadFilesAsync extends AsyncTask<SmbFile, Void, List<Integer>> {
        private final SmbDirAdapter adapter;
        private SparseArrayCompat<FileInfo> files = new SparseArrayCompat<>();
        private SparseArrayCompat<Pair<View, Boolean>> views = new SparseArrayCompat<>();

        LoadFilesAsync(SmbDirAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        protected List<Integer> doInBackground(final SmbFile... files) {
            try {
                List<Integer> hashCodes = new ArrayList<>();
                for (SmbFile file : files) {
                    if (isCancelled()) return null;
                    if (file == null) continue;
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
                    if (isCancelled()) return null;
                    final int hashCode = FileInfo.hashCode(file);
                    this.files.append(hashCode, new FileInfo(file.getShare(), name, icon, isDirectory, lastModified, fileSize, file.isHidden()));
                    if (this.views.get(hashCode, null) != null) hashCodes.add(hashCode);
                }
                return hashCodes;
            } catch (SmbException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<Integer> hashCodes) {
            if (isCancelled() || hashCodes.isEmpty()) return;
            for (int hashCode : hashCodes) {
                Pair<View, Boolean> pair = this.views.get(hashCode, null);
                if (pair != null) this.bindView(hashCode, pair.getFirst(), pair.getSecond());
            }
        }

        void bindView(final int hashCode, final View view, final boolean isSelected) {
            if (isCancelled()) return;
            FileInfo file = this.files.get(hashCode, null);
            if (file == null) {
                this.views.append(hashCode, new Pair<>(view, isSelected));
                return;
            }
            if (adapter == null || view == null) {
                cancel(true);
                return;
            }

            if (adapter._bindView == null) {
                final View root = view.findViewById(R.id.root);
                final TextView tvName = view.findViewById(R.id.text);
                final TextView tvSize = view.findViewById(R.id.txt_size);
                final TextView tvDate = view.findViewById(R.id.txt_date);
                //ImageView ivIcon = (ImageView) view.findViewById(R.id.icon);

                tvName.setText(file.name);
                tvName.setCompoundDrawablesWithIntrinsicBounds(file.icon, null, null, null);
                if (file.lastModified != 0L) {
                    tvDate.setText(_formatter.format(new Date(file.lastModified)));
                    tvDate.setVisibility(VISIBLE);
                } else {
                    tvDate.setVisibility(GONE);
                }
                tvSize.setText(file.fileSize);
                if (root.getBackground() == null)
                    root.setBackgroundResource(R.color.li_row_background);
                if (isSelected) root.getBackground().setColorFilter(adapter._colorFilter);
                else root.getBackground().clearColorFilter();
            } else adapter._bindView.bindView(file, isSelected, view);

            view.setVisibility(VISIBLE);
        }
    }

    @Override
    public long getItemId(final int position) {
        return FileInfo.hashCode(getItem(position));
    }

    private SmbFile[] toArray(final List<SmbFile> list) {
        SmbFile[] array = new SmbFile[list.size()];
        list.toArray(array);
        return array;
    }

    @Override
    public void setEntries(List<SmbFile> entries) {
        if (_getView == null) {
            if (_getViewAsync != null) _getViewAsync.cancel(true);
            _getViewAsync = new LoadFilesAsync(this);
            _getViewAsync.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, toArray(entries));
        }
        clear();
        addAll(entries);
        notifyDataSetChanged();
    }

    private LoadFilesAsync _getViewAsync;
    private BindView _bindView;
}

