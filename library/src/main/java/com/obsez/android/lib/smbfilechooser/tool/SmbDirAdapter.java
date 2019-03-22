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

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import kotlin.Triple;

/**
 * Created by coco on 6/9/18. Edited by Guiorgy on 10/09/18.
 */
public class SmbDirAdapter extends MyAdapter<SmbFile> {
    private final ExecutorService EXECUTOR;

    public SmbDirAdapter(Context cxt, ExecutorService EXECUTOR, String dateFormat) {
        super(cxt, dateFormat);
        this.EXECUTOR = EXECUTOR;
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

    @Override
    public void overrideGetView(GetView<SmbFile> getView) {
        super.overrideGetView(getView);
    }

    // This function is called to show each view item
    @NonNull
    @Override
    public View getView(final int position, final View convertView, @NonNull final ViewGroup parent) {
        SmbFile file = getItem(position);
        if (file == null) return super.getView(position, convertView, parent);

        if (_getView != null)
            //noinspection unchecked
            return _getView.getView(file, getSelected(File.hashCode(file)) == null, convertView, parent, LayoutInflater.from(getContext()));

        ViewGroup view = (ViewGroup) super.getView(position, convertView, parent);
        new GetViewAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, this, file, view);

        return view;
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
            final View view = triple.getSecond();
            final File file = triple.getThird();
            if (adapter == null || view == null || file == null) {
                cancel(true);
                return;
            }

            final View root = view.findViewById(R.id.root);
            final TextView tvName = view.findViewById(R.id.text);
            final TextView tvSize = view.findViewById(R.id.txt_size);
            final TextView tvDate = view.findViewById(R.id.txt_date);
            //ImageView ivIcon = (ImageView) view.findViewById(R.id.icon);

            tvName.setText(file.name);
            tvName.setCompoundDrawablesWithIntrinsicBounds(file.icon, null, null, null);
            if (file.lastModified != 0L) {
                tvDate.setText(_formatter.format(new Date(file.lastModified)));
                tvDate.setVisibility(View.VISIBLE);
            } else {
                tvDate.setVisibility(View.GONE);
            }
            tvSize.setText(file.fileSize);
            if (root.getBackground() == null) root.setBackgroundResource(R.color.li_row_background);
            if (adapter.getSelected(file.hashCode) == null) root.getBackground().clearColorFilter();
            else root.getBackground().setColorFilter(adapter._colorFilter);
        }
    }

    @Override
    public long getItemId(final int position) {
        Future<Long> ret = EXECUTOR.submit(() -> {
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
}

