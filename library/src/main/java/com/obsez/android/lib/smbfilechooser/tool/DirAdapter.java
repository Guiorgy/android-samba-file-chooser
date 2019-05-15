package com.obsez.android.lib.smbfilechooser.tool;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.obsez.android.lib.smbfilechooser.FileChooserDialog;
import com.obsez.android.lib.smbfilechooser.R;
import com.obsez.android.lib.smbfilechooser.internals.FileUtil;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;
import com.obsez.android.lib.smbfilechooser.internals.WrappedDrawable;

import java.io.File;
import java.util.Date;

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
    public DirAdapter(Context cxt, String dateFormat) {
        super(cxt, dateFormat);
    }

    @Override
    public void overrideGetView(GetView<File> getView) {
        super.overrideGetView(getView);
    }

    // This function is called to show each view item
    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        File file = getItem(position);
        if (file == null) return super.getView(position, convertView, parent);

        if (_getView != null)
            //noinspection unchecked
            return _getView.getView(file, getSelected(file.hashCode()) == null, convertView, parent, LayoutInflater.from(getContext()));

        ViewGroup view = (ViewGroup) super.getView(position, convertView, parent);

        TextView tvName = view.findViewById(R.id.text);
        TextView tvSize = view.findViewById(R.id.txt_size);
        TextView tvDate = view.findViewById(R.id.txt_date);
        //ImageView ivIcon = (ImageView) view.findViewById(R.id.icon);


        tvName.setText(file.getName());

        long lastModified = file.isDirectory() ? 0L : file.lastModified();
        if (lastModified != 0L) {
            tvDate.setText(_formatter.format(new Date(lastModified)));
        } else {
            tvDate.setText("");
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

        View root = view.findViewById(R.id.root);
        if (root.getBackground() == null) root.setBackgroundResource(R.color.li_row_background);
        if (getSelected(file.hashCode()) == null) root.getBackground().clearColorFilter();
        else root.getBackground().setColorFilter(_colorFilter);

        return view;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0 || (getCount() == 1 && getItem(0) instanceof FileChooserDialog.RootFile);
    }
}

