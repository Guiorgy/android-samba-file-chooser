package com.obsez.android.lib.smbfilechooser.tool;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class MyAdapter<T> extends BaseAdapter {
    private Context context;

    Context getContext() {
        return context;
    }

    private List<T> _entries = new ArrayList<>();
    private SparseArray<T> _selected = new SparseArray<>();
    private LayoutInflater _inflater;
    private int _resource;

    MyAdapter(Context context, int resId) {
        this.context = context;
        this._inflater = LayoutInflater.from(context);
        this._resource = resId;
    }

    MyAdapter(Context context, List<T> entries, int resId) {
        this.context = context;
        this._inflater = LayoutInflater.from(context);
        this._resource = resId;
        addAll(entries);
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

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        this.setScrollListener(parent);
        return convertView != null ? convertView : _inflater.inflate(_resource, parent, false);
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
            _selected.delete(id);
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

    public void clearSelected() {
        _selected.clear();
    }

    private AtomicBoolean _isDone = new AtomicBoolean(false);
    private boolean _isScrollEnabled = true;

    @SuppressLint("ClickableViewAccessibility")
    private void setScrollListener(final ViewGroup parent) {
        if (_isDone.get()) return;
        parent.setOnTouchListener((v, event) -> !_isScrollEnabled && event.getAction() == MotionEvent.ACTION_MOVE);
    }

    public void setScrollEnabled(final boolean isEnable) {
        _isScrollEnabled = isEnable;
    }
}
