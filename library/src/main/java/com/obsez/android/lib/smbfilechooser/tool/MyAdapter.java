package com.obsez.android.lib.smbfilechooser.tool;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.collection.SparseArrayCompat;
import androidx.core.content.ContextCompat;

import com.obsez.android.lib.smbfilechooser.FileChooserDialog;
import com.obsez.android.lib.smbfilechooser.R;
import com.obsez.android.lib.smbfilechooser.SmbFileChooserDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
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

@SuppressWarnings("WeakerAccess")
abstract class MyAdapter<T> extends BaseAdapter {
    private Context context;

    Context getContext() {
        return context;
    }

    private List<T> _entries = new ArrayList<>();
    private SparseArrayCompat<T> _selected = new SparseArrayCompat<>();
    private LayoutInflater _inflater;

    MyAdapter(Context context, String dateFormat) {
        this.context = context;
        this._inflater = LayoutInflater.from(context);
        this.init(dateFormat);
    }

    @SuppressLint("SimpleDateFormat")
    private void init(String dateFormat) {
        _formatter = new SimpleDateFormat(dateFormat != null && !"".equals(dateFormat.trim()) ? dateFormat.trim() : "yyyy/MM/dd HH:mm:ss");
        if (_defaultFolderIcon == null)
            _defaultFolderIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_folder);
        if (_defaultFileIcon == null)
            _defaultFileIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_file);

        TypedArray ta = getContext().obtainStyledAttributes(R.styleable.FileChooser);
        int colorFilter = ta.getColor(R.styleable.FileChooser_fileListItemSelectedTint, getContext().getResources().getColor(R.color.li_row_background_tint));
        ta.recycle();
        _colorFilter = new PorterDuffColorFilter(colorFilter, PorterDuff.Mode.MULTIPLY);
    }

    @Override
    public int getCount() {
        return _entries.size();
    }

    @Override
    public T getItem(final int position) {
        return _entries.get(position);
    }

    @Override
    public long getItemId(final int position) {
        return getItem(position).hashCode();
    }

    public T getSelected(int id) {
        return _selected.get(id, null);
    }

    @FunctionalInterface
    public interface GetView<T> {
        /**
         * @param file        file that should me displayed
         * @param isSelected  whether file is selected when _enableMultiple is set to true
         * @param convertView see {@link BaseAdapter#getView(int, View, ViewGroup)}
         * @param parent      see {@link BaseAdapter#getView(int, View, ViewGroup)}
         * @param inflater    a layout inflater with the FileChooser theme wrapped context
         * @return your custom row item view
         */
        @NonNull
        View getView(@NonNull final T file, final boolean isSelected, View convertView, @NonNull final ViewGroup parent, @NonNull final LayoutInflater inflater);
    }

    public void overrideGetView(GetView<T> getView) {
        this._getView = getView;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        return convertView != null ? convertView : _inflater.inflate(R.layout.li_row_textview, parent, false);
    }

    void clear() {
        this._entries.clear();
    }

    void addAll(List<T> entries) {
        this._entries.addAll(entries);
    }

    public void setEntries(List<T> entries) {
        clear();
        addAll(entries);
        notifyDataSetChanged();
    }

    public void selectItem(int position) {
        int id = (int) getItemId(position);
        if (_selected.get(id, null) == null) {
            _selected.append(id, getItem(position));
        } else {
            _selected.remove(id);
        }
        notifyDataSetChanged();
    }

    public boolean isSelected(int position) {
        return isSelectedById((int) getItemId(position));
    }

    public boolean isSelectedById(int id) {
        return _selected.get(id, null) != null;
    }

    public boolean isAnySelected() {
        return _selected.size() > 0;
    }

    public boolean isOneSelected() {
        return _selected.size() == 1;
    }

    public List<T> getSelected() {
        ArrayList<T> list = new ArrayList<>();
        for (int i = 0; i < _selected.size(); i++) {
            list.add(_selected.valueAt(i));
        }
        return list;
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

    public void clearSelected() {
        try {
            _selected.clear();
        } catch (Resources.NotFoundException e) {
            _selected = new SparseArrayCompat<>();
        }
    }

    public boolean isEmpty() {
        return getCount() == 0 || (getCount() == 1 && (getItem(0) instanceof FileChooserDialog.RootFile || getItem(0) instanceof SmbFileChooserDialog.RootSmbFile));
    }

    public Stack<Integer> getIndexStack() {
        return _indexStack;
    }

    protected static SimpleDateFormat _formatter;
    protected Drawable _defaultFolderIcon = null;
    protected Drawable _defaultFileIcon = null;
    protected boolean _resolveFileType = false;
    protected PorterDuffColorFilter _colorFilter;
    protected GetView _getView = null;
    private Stack<Integer> _indexStack = new Stack<>();
}
