package com.obsez.android.lib.smbfilechooser.tool;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.obsez.android.lib.smbfilechooser.R;
import com.obsez.android.lib.smbfilechooser.internals.FileUtil;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;
import com.obsez.android.lib.smbfilechooser.internals.WrappedDrawable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Created by coco on 6/7/15.
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

public class DirAdapter extends MyAdapter<File> {

    public DirAdapter(Context cxt, List<File> entries, int resId, String dateFormat) {
        super(cxt, entries, resId);
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

    // This function is called to show each view item
    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewGroup rl = (ViewGroup) super.getView(position, convertView, parent);

        TextView tvName = rl.findViewById(R.id.text);
        TextView tvSize = rl.findViewById(R.id.txt_size);
        TextView tvDate = rl.findViewById(R.id.txt_date);
        //ImageView ivIcon = (ImageView) rl.findViewById(R.id.icon);

        File file = super.getItem(position);
        if (file == null) return rl;

        tvName.setText(file.getName());

        long lastModified = file.isDirectory() ? 0L : file.lastModified();
        if (lastModified != 0L) {
            tvDate.setText(_formatter.format(new Date(lastModified)));
            tvDate.setVisibility(View.VISIBLE);
        } else {
            tvDate.setVisibility(View.GONE);
        }

        tvSize.setText(file.isDirectory() ? "" : FileUtil.getReadableFileSize(file.length()));

        Drawable icon = file.isDirectory() ? _defaultFolderIcon : null;
        if (icon == null) {
            if (_resolveFileType) {
                icon = UiUtil.resolveFileTypeIcon(getContext(), Uri.fromFile(file));
                if (icon != null) {
                    icon = new WrappedDrawable(icon, 24, 24);
                }
            }
            if (icon == null) {
                icon = _defaultFileIcon;
            }
        }
        if (file.isHidden()) {
            final PorterDuffColorFilter filter = new PorterDuffColorFilter(0x70ffffff, PorterDuff.Mode.SRC_ATOP);
            icon = icon.getConstantState().newDrawable().mutate();
            icon.setColorFilter(filter);
        }
        tvName.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);

        View root = rl.findViewById(R.id.root);
        if (getSelected(file.hashCode()) == null) root.getBackground().clearColorFilter();
        else root.getBackground().setColorFilter(_colorFilter);

        return rl;
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

    public boolean isResolveFileType() {
        return _resolveFileType;
    }

    public void setResolveFileType(boolean resolveFileType) {
        this._resolveFileType = resolveFileType;
    }

    @Override
    public long getItemId(int position) {
        //noinspection ConstantConditions
        return getItem(position).hashCode();
    }

    private static SimpleDateFormat _formatter;
    private Drawable _defaultFolderIcon = null;
    private Drawable _defaultFileIcon = null;
    private boolean _resolveFileType = false;
    private PorterDuffColorFilter _colorFilter;
}

